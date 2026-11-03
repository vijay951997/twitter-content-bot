package com.ai.xbot.news;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.dto.NewsSourceDto;
import com.ai.xbot.dto.NewsSourceRequest;
import com.ai.xbot.entity.NewsSource;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.exception.NotFoundException;
import com.ai.xbot.repository.NewsSourceRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The configurable source registry. Social media and anonymous platforms cannot be registered:
 * posts there are never treated as verified facts.
 */
@Service
public class SourceRegistryService {

    static final Set<String> BLOCKED_DOMAINS = Set.of(
            "x.com", "twitter.com", "facebook.com", "fb.com", "instagram.com", "threads.net", "youtube.com",
            "youtu.be", "t.me", "telegram.me", "whatsapp.com", "reddit.com", "sharechat.com", "koo.in", "tiktok.com",
            "linkedin.com", "medium.com", "substack.com", "blogspot.com", "wordpress.com");

    private final NewsSourceRepository repository;
    private final AuditService audit;

    public SourceRegistryService(NewsSourceRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NewsSourceDto> list() {
        return repository.findAllByOrderByNameAsc().stream().map(SourceRegistryService::toDto).toList();
    }

    @Transactional
    public NewsSourceDto create(NewsSourceRequest r, UUID actor) {
        validate(r, null);
        NewsSource s = repository.save(new NewsSource(r.name().strip(), r.baseUrl().strip(), r.feedUrl().strip(),
                r.sourceType(), ContentLanguage.fromCode(r.language()), r.primarySource(), r.reliabilityTier(),
                blankToNull(r.reliabilityNotes()), r.enabled(), r.fetchIntervalMinutes(), r.requiresKeywordMatch()));
        audit.record(actor, ActorType.USER, "NEWS_SOURCE_CREATED", "news_sources", s.getId(), null, toDto(s), null);
        return toDto(s);
    }

    @Transactional
    public NewsSourceDto update(UUID id, NewsSourceRequest r, UUID actor) {
        NewsSource s = repository.findById(id).orElseThrow(() -> new NotFoundException("Source not found"));
        validate(r, id);
        NewsSourceDto before = toDto(s);
        s.update(r.name().strip(), r.baseUrl().strip(), r.feedUrl().strip(), r.sourceType(),
                ContentLanguage.fromCode(r.language()), r.primarySource(), r.reliabilityTier(),
                blankToNull(r.reliabilityNotes()), r.enabled(), r.fetchIntervalMinutes(), r.requiresKeywordMatch());
        audit.record(actor, ActorType.USER, "NEWS_SOURCE_UPDATED", "news_sources", id, before, toDto(s), null);
        return toDto(s);
    }

    void validate(NewsSourceRequest r, UUID selfId) {
        checkUrl(r.baseUrl(), "Website");
        checkUrl(r.feedUrl(), "Feed");
        repository.findByBaseUrl(r.baseUrl().strip()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) throw new BadRequestException("A source with this website URL already exists");
        });
        if (r.primarySource() && r.reliabilityTier() != 1) {
            throw new BadRequestException("Official primary sources must use reliability tier 1");
        }
    }

    static void checkUrl(String url, String label) {
        URI u;
        try {
            u = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(label + " URL is not valid");
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http")) || u.getHost() == null) {
            throw new BadRequestException(label + " URL must start with http:// or https://");
        }
        String domain = UrlNormalizer.domain(url);
        for (String blocked : BLOCKED_DOMAINS) {
            if (domain.equals(blocked) || domain.endsWith("." + blocked)) {
                throw new BadRequestException("Social media and blog platforms can't be news sources (" + blocked
                        + "). Posts there are not treated as verified facts.");
            }
        }
    }

    public static NewsSourceDto toDto(NewsSource s) {
        return new NewsSourceDto(s.getId(), s.getName(), s.getBaseUrl(), s.getFeedUrl(), s.getSourceType().name(),
                s.getLanguage().code(), s.isPrimarySource(), s.getReliabilityTier(), s.getReliabilityNotes(),
                s.isEnabled(), s.getFetchIntervalMinutes(), s.isRequiresKeywordMatch(), s.getLastFetchedAt(),
                s.getLastFetchStatus(), s.getLastFetchMessage(), s.getConsecutiveFailures());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
