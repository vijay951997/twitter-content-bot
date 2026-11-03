package com.ai.xbot.service.drafts;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.dto.DraftDto;
import com.ai.xbot.dto.GenerateDraftRequest;
import com.ai.xbot.dto.GenerationResultDto;
import com.ai.xbot.dto.GenerationResultDto.Discarded;
import com.ai.xbot.dto.ScheduleSettingsDto;
import com.ai.xbot.entity.GeneratedPost;
import com.ai.xbot.entity.PostSource;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostSlot;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.exception.TooManyRequestsException;
import com.ai.xbot.repository.AuditLogRepository;
import com.ai.xbot.repository.GeneratedPostRepository;
import com.ai.xbot.repository.PostSourceRepository;
import com.ai.xbot.service.ContentHasher;
import com.ai.xbot.service.SettingsService;
import com.ai.xbot.service.ai.AiDraftGenerator;
import com.ai.xbot.service.ai.AiGenerationRequest;
import com.ai.xbot.service.ai.AiGenerationResult;
import com.ai.xbot.service.ai.AiServiceException;
import com.ai.xbot.service.ai.PromptBuilder;
import com.ai.xbot.service.drafts.DraftValidator.Accepted;
import com.ai.xbot.service.drafts.DraftValidator.Rejected;
import com.ai.xbot.service.drafts.DuplicateDetector.Duplicate;
import com.ai.xbot.service.drafts.DuplicateDetector.Existing;
import com.ai.xbot.service.drafts.DuplicateDetector.Similar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates drafts with the AI and saves those that pass every check.
 *
 * The AI call runs outside any database transaction (it can take many seconds); saving happens in a
 * short transaction afterwards. Every saved draft has status GENERATED: this service can never approve,
 * schedule or publish anything.
 */
@Service
public class DraftGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DraftGenerationService.class);
    static final String ACTION_REQUESTED = "AI_GENERATION_REQUESTED";
    private static final int PREVIEW = 80;

    private final AiDraftGenerator ai;
    private final PromptBuilder prompts;
    private final SettingsService settings;
    private final GeneratedPostRepository posts;
    private final PostSourceRepository sources;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final ContentHasher hasher;
    private final DraftMapper mapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DraftGenerationService(AiDraftGenerator ai, PromptBuilder prompts, SettingsService settings,
                                  GeneratedPostRepository posts, PostSourceRepository sources,
                                  AuditLogRepository auditLogs, AuditService audit, ContentHasher hasher,
                                  DraftMapper mapper, ObjectMapper objectMapper, TransactionTemplate tx, Clock clock) {
        this.ai = ai;
        this.prompts = prompts;
        this.settings = settings;
        this.posts = posts;
        this.sources = sources;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.hasher = hasher;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.tx = tx;
        this.clock = clock;
    }

    /** One slot, with optional topic and source material. */
    public GenerationResultDto generate(GenerateDraftRequest request, UUID actorId) {
        List<String> urls = cleanUrls(request.sourceUrls());
        ContentLanguage language = resolveLanguage(request.language(), request.slot());
        int variations = settings.getContent().variationsPerTopic();
        String notes = blankToNull(request.sourceNotes());
        String topic = blankToNull(request.topic());

        checkRateLimit(1);
        recordRequest(actorId, request.slot(), language, notes != null);

        AiGenerationResult result;
        try {
            result = ai.generate(new AiGenerationRequest(prompts.systemPrompt(),
                    prompts.userPrompt(request.slot(), language, topic, notes, urls, variations)));
        } catch (AiServiceException e) {
            tx.executeWithoutResult(s -> audit.record(actorId, ActorType.USER, "AI_GENERATION_FAILED",
                    "generated_posts", null, null, Map.of("kind", e.getKind().name()), e.getMessage()));
            throw e;
        }
        return save(request.slot(), language, topic, notes, urls, result, actorId);
    }

    /** All three daily slots, no topic or sources (discussion posts until news discovery arrives in Phase 3). */
    public GenerationResultDto generateDaily(UUID actorId) {
        checkRateLimit(PostSlot.values().length);
        List<DraftDto> created = new ArrayList<>();
        List<Discarded> discarded = new ArrayList<>();
        for (PostSlot slot : PostSlot.values()) {
            GenerationResultDto r = generate(new GenerateDraftRequest(slot, null, null, null, List.of()), actorId);
            created.addAll(r.created());
            discarded.addAll(r.discarded());
        }
        return new GenerationResultDto(created, discarded);
    }

    private GenerationResultDto save(PostSlot slot, ContentLanguage language, String topic, String notes,
                                     List<String> urls, AiGenerationResult result, UUID actorId) {
        return tx.execute(status -> {
            int lookbackDays = settings.getContent().duplicateLookbackDays();
            Instant since = Instant.now(clock).minus(Duration.ofDays(lookbackDays));
            List<Existing> recent = new ArrayList<>(posts
                    .findRecentForDuplicateCheck(since, List.of(PostStatus.REJECTED, PostStatus.CANCELLED))
                    .stream().map(p -> new Existing(p.getContent(), p.getContentHash(), p.getCreatedAt())).toList());

            Instant publishAt = nextSlotTime(slot);
            UUID group = UUID.randomUUID();
            List<DraftDto> created = new ArrayList<>();
            List<Discarded> discarded = new ArrayList<>();

            if (result.drafts().isEmpty()) {
                discarded.add(new Discarded(slot.name(), "The AI returned no drafts", null));
            }

            for (AiGenerationResult.AiDraft d : result.drafts()) {
                DraftValidator.Outcome outcome = DraftValidator.validate(d, language, slot, urls, notes != null);
                if (outcome instanceof Rejected r) {
                    discarded.add(new Discarded(slot.name(), r.reason(), preview(d.content())));
                    continue;
                }
                Accepted a = (Accepted) outcome;
                String hash = hasher.hash(a.content());

                String duplicateWarning = null;
                DuplicateDetector.Verdict verdict = DuplicateDetector.check(a.content(), hash, recent);
                if (verdict instanceof Duplicate dup) {
                    discarded.add(new Discarded(slot.name(), dup.reason(), preview(a.content())));
                    continue;
                }
                if (verdict instanceof Similar sim) {
                    duplicateWarning = "Similar (" + Math.round(sim.score() * 100) + "%) to a draft from "
                            + DateTimeFormatter.ofPattern("d MMM yyyy").format(sim.otherCreatedAt().atZone(zone()));
                }

                GeneratedPost post = new GeneratedPost(a.content(), hash, a.language(), a.category(), slot, publishAt);
                post.recordGeneration(group, ai.modelName(), PromptBuilder.PROMPT_VERSION, result.rawJson(), topic, notes);
                post.recordReviewAids(a.verificationStatus(), a.qualityEstimate(), claimsJson(a),
                        a.riskFlags(), duplicateWarning, a.weightedLength());
                GeneratedPost saved = posts.save(post);

                List<PostSource> postSources = a.sourceUrls().stream().map(u -> new PostSource(saved.getId(), u)).toList();
                sources.saveAll(postSources);

                audit.record(actorId, ActorType.USER, "DRAFT_GENERATED", "generated_posts", saved.getId(), null,
                        Map.of("slot", slot.name(), "language", a.language().code(), "status", saved.getStatus().name(),
                                "verificationStatus", a.verificationStatus().name()), null);

                recent.add(new Existing(a.content(), hash, Instant.now(clock)));
                created.add(mapper.toDto(saved, a.sourceUrls()));
            }
            log.info("Draft generation slot={} language={} created={} discarded={}",
                    slot, language.code(), created.size(), discarded.size());
            return new GenerationResultDto(created, discarded);
        });
    }

    ContentLanguage resolveLanguage(String requested, PostSlot slot) {
        var content = settings.getContent();
        if (requested != null && !requested.isBlank()) {
            if (!content.enabledLanguages().contains(requested)) {
                throw new BadRequestException("Language '" + requested + "' is not enabled in Settings");
            }
            return ContentLanguage.fromCode(requested);
        }
        return LanguagePlanner.languageFor(LocalDate.now(clock.withZone(zone())), slot, content.languageMix());
    }

    /** Next occurrence of the slot's configured time, in the configured timezone. */
    Instant nextSlotTime(PostSlot slot) {
        ScheduleSettingsDto s = settings.getSchedule();
        String time = switch (slot) {
            case MORNING -> s.morning();
            case AFTERNOON -> s.afternoon();
            case EVENING -> s.evening();
        };
        ZoneId zone = ZoneId.of(s.timezone());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        ZonedDateTime candidate = now.toLocalDate().atTime(LocalTime.parse(time)).atZone(zone);
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
        return candidate.toInstant();
    }

    static List<String> cleanUrls(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            if (r == null || r.isBlank()) continue;
            String u = r.strip();
            try {
                URI uri = URI.create(u);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Not a valid http(s) link: " + u);
            }
            out.add(u);
        }
        if (out.size() > 5) throw new BadRequestException("At most 5 source URLs");
        return List.copyOf(out);
    }

    private void checkRateLimit(int needed) {
        int max = settings.maxGenerationsPerHour();
        long used = auditLogs.countByActionAndCreatedAtAfter(ACTION_REQUESTED, Instant.now(clock).minus(Duration.ofHours(1)));
        if (used + needed > max) {
            throw new TooManyRequestsException("Generation limit reached (" + max
                    + " per hour). Try again later or raise ai.max_generations_per_hour.");
        }
    }

    private void recordRequest(UUID actorId, PostSlot slot, ContentLanguage language, boolean hasSources) {
        tx.executeWithoutResult(s -> audit.record(actorId, ActorType.USER, ACTION_REQUESTED, "generated_posts", null,
                null, Map.of("slot", slot.name(), "language", language.code(), "hasSourceMaterial", hasSources), null));
    }

    private String claimsJson(Accepted a) {
        try {
            return objectMapper.writeValueAsString(Map.of("verified", a.verifiedClaims(), "unverified", a.unverifiedClaims()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ZoneId zone() {
        return ZoneId.of(settings.getSchedule().timezone());
    }

    private static String preview(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.codePointCount(0, t.length()) <= PREVIEW ? t : t.substring(0, t.offsetByCodePoints(0, PREVIEW)) + "…";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
