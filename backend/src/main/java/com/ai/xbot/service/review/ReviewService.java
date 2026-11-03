package com.ai.xbot.service.review;

import static com.ai.xbot.entity.enums.PostStatus.*;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.dto.*;
import com.ai.xbot.entity.*;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.entity.enums.VerificationStatus;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.exception.ConflictException;
import com.ai.xbot.exception.NotFoundException;
import com.ai.xbot.repository.*;
import com.ai.xbot.service.ContentHasher;
import com.ai.xbot.service.drafts.DraftQueryService;
import com.ai.xbot.service.text.XTextLength;
import com.ai.xbot.service.workflow.PostStatusPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human review: edit, approve-and-schedule, reject, cancel schedule, save for later.
 *
 * Business rules enforced here (and re-checked by the publisher right before sending):
 * <ol>
 *   <li>Only an authenticated user's explicit approve call creates an approval — never AI or the scheduler.</li>
 *   <li>The approval stores the hash of the exact text approved.</li>
 *   <li>Editing an approved or scheduled draft invalidates the approval and cancels the schedule.</li>
 *   <li>Rejected drafts are terminal.</li>
 *   <li>Every change is audited with who and when.</li>
 * </ol>
 */
@Service
public class ReviewService {

    static final Duration MAX_SCHEDULE_AHEAD = Duration.ofDays(30);

    private final GeneratedPostRepository posts;
    private final PostApprovalRepository approvals;
    private final PublishingQueueRepository queue;
    private final AuditService audit;
    private final ContentHasher hasher;
    private final DraftQueryService queries;
    private final Clock clock;

    public ReviewService(GeneratedPostRepository posts, PostApprovalRepository approvals, PublishingQueueRepository queue,
                         AuditService audit, ContentHasher hasher, DraftQueryService queries, Clock clock) {
        this.posts = posts;
        this.approvals = approvals;
        this.queue = queue;
        this.audit = audit;
        this.hasher = hasher;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional
    public DraftDto edit(UUID id, EditDraftRequest r, UUID actor) {
        GeneratedPost p = load(id, r.version());
        if (!PostStatusPolicy.isEditable(p.getStatus())) {
            throw new ConflictException("A " + p.getStatus() + " draft can't be edited");
        }
        String content = r.content().strip();
        int length = XTextLength.weightedLength(content);
        if (length > XTextLength.MAX_WEIGHTED_LENGTH) {
            throw new BadRequestException("Too long for X (" + length + "/" + XTextLength.MAX_WEIGHTED_LENGTH + ")");
        }
        if (content.equals(p.getContent())) return queries.get(id);

        String before = p.getContent();
        PostStatus statusBefore = p.getStatus();
        if (PostStatusPolicy.editInvalidatesApproval(statusBefore)) {
            invalidateApprovalAndSchedule(p, "Edited after approval", actor);
        }
        p.editContent(content, hasher.hash(content), length);
        if (p.getStatus() == GENERATED) p.transitionTo(UNDER_REVIEW);

        audit.record(actor, ActorType.USER, "DRAFT_EDITED", "generated_posts", id,
                Map.of("content", before, "status", statusBefore.name()),
                Map.of("content", content, "status", p.getStatus().name()),
                statusBefore == APPROVED || statusBefore == SCHEDULED ? "Approval invalidated by edit" : null);
        return queries.get(id);
    }

    @Transactional
    public DraftDto approve(UUID id, ApproveDraftRequest r, UUID actor) {
        if (actor == null) throw new IllegalStateException("Approval requires a user");
        GeneratedPost p = load(id, r.version());
        if (p.getStatus() != GENERATED && p.getStatus() != UNDER_REVIEW) {
            throw new ConflictException("Only new or in-review drafts can be approved (this one is " + p.getStatus() + ")");
        }
        int length = XTextLength.weightedLength(p.getContent());
        if (length > XTextLength.MAX_WEIGHTED_LENGTH) {
            throw new BadRequestException("Too long for X (" + length + "/280). Edit it first.");
        }
        List<String> warnings = warnings(p, queries.get(id));
        if (!warnings.isEmpty() && !r.acknowledgeWarnings()) {
            throw new BadRequestException("Please confirm you checked: " + String.join("; ", warnings));
        }

        Instant now = Instant.now(clock);
        Instant publishAt = r.publishNow() ? now
                : (r.publishAt() != null ? r.publishAt() : p.getProposedPublishAt());
        if (publishAt == null) throw new BadRequestException("Choose a publishing time");
        if (!r.publishNow() && publishAt.isBefore(now.plusSeconds(60))) {
            throw new BadRequestException("The publishing time must be at least a minute from now (or choose 'Post now')");
        }
        if (publishAt.isAfter(now.plus(MAX_SCHEDULE_AHEAD))) {
            throw new BadRequestException("Posts can be scheduled at most 30 days ahead");
        }

        // The hash is recomputed from the stored text, so the approval covers exactly what was reviewed.
        String hash = hasher.hash(p.getContent());
        if (!hash.equals(p.getContentHash())) throw new ConflictException("Draft content hash mismatch; reload and review again");

        if (p.getStatus() == GENERATED) p.transitionTo(UNDER_REVIEW);
        PostApproval approval = approvals.save(new PostApproval(id, PostApproval.APPROVED, actor, now, hash, blankToNull(r.comment())));
        p.transitionTo(APPROVED);
        p.setProposedPublishAt(publishAt);
        queue.save(new PublishingQueueEntry(id, approval.getId(), publishAt));
        p.transitionTo(SCHEDULED);
        p.setSavedForLater(false);

        audit.record(actor, ActorType.USER, "DRAFT_APPROVED", "generated_posts", id, null,
                Map.of("approvalId", approval.getId().toString(), "publishAt", publishAt.toString(),
                        "contentHash", hash, "acknowledgedWarnings", warnings), r.comment());
        return queries.get(id);
    }

    @Transactional
    public DraftDto reject(UUID id, RejectDraftRequest r, UUID actor) {
        GeneratedPost p = load(id, r.version());
        if (p.getStatus() == SCHEDULED) {
            invalidateApprovalAndSchedule(p, "Rejected", actor);
        } else {
            approvals.findLiveApproval(id).ifPresent(a -> a.invalidate(Instant.now(clock), "Rejected"));
        }
        if (!PostStatusPolicy.canTransition(p.getStatus(), REJECTED)) {
            throw new ConflictException("A " + p.getStatus() + " draft can't be rejected");
        }
        approvals.save(new PostApproval(id, PostApproval.REJECTED, actor, Instant.now(clock), p.getContentHash(), blankToNull(r.reason())));
        p.reject(blankToNull(r.reason()));
        audit.record(actor, ActorType.USER, "DRAFT_REJECTED", "generated_posts", id, null,
                Map.of("status", REJECTED.name()), r.reason());
        return queries.get(id);
    }

    /** Takes a scheduled draft off the schedule and back into review; the approval no longer counts. */
    @Transactional
    public DraftDto unschedule(UUID id, long version, UUID actor) {
        GeneratedPost p = load(id, version);
        if (p.getStatus() != SCHEDULED && p.getStatus() != APPROVED) {
            throw new ConflictException("This draft isn't scheduled");
        }
        invalidateApprovalAndSchedule(p, "Unscheduled by user", actor);
        audit.record(actor, ActorType.USER, "DRAFT_UNSCHEDULED", "generated_posts", id, null,
                Map.of("status", p.getStatus().name()), null);
        return queries.get(id);
    }

    @Transactional
    public DraftDto saveForLater(UUID id, boolean saved, UUID actor) {
        GeneratedPost p = posts.findById(id).orElseThrow(() -> new NotFoundException("Draft not found"));
        if (p.getStatus() != GENERATED && p.getStatus() != UNDER_REVIEW) {
            throw new ConflictException("Only drafts waiting for review can be saved for later");
        }
        p.setSavedForLater(saved);
        audit.record(actor, ActorType.USER, saved ? "DRAFT_SAVED_FOR_LATER" : "DRAFT_UNSAVED", "generated_posts", id,
                null, null, null);
        return queries.get(id);
    }

    /**
     * Retries a FAILED post. Allowed only while its approval is still live and the text still matches it;
     * otherwise the user must review and approve again.
     */
    @Transactional
    public DraftDto retryPublish(UUID id, long version, UUID actor) {
        GeneratedPost p = load(id, version);
        if (p.getStatus() != FAILED) throw new ConflictException("Only failed posts can be retried");
        PostApproval a = approvals.findLiveApproval(id)
                .orElseThrow(() -> new ConflictException("The approval is no longer valid. Approve the post again."));
        if (!a.getApprovedContentHash().equals(hasher.hash(p.getContent()))) {
            throw new ConflictException("The text differs from what was approved. Approve the post again.");
        }
        PublishingQueueEntry q = queue.findByPostIdOrderByCreatedAtDesc(id).stream().findFirst()
                .orElseThrow(() -> new ConflictException("No publishing record to retry"));
        Instant now = Instant.now(clock);
        q.requeue(now);
        p.setProposedPublishAt(now);
        p.transitionTo(SCHEDULED);
        audit.record(actor, ActorType.USER, "PUBLISH_RETRY_REQUESTED", "generated_posts", id, null, null, null);
        return queries.get(id);
    }

    /** Gives up on a failed post. */
    @Transactional
    public DraftDto cancelFailed(UUID id, long version, UUID actor) {
        GeneratedPost p = load(id, version);
        if (p.getStatus() != FAILED) throw new ConflictException("Only failed posts can be cancelled here");
        approvals.findLiveApproval(id).ifPresent(a -> a.invalidate(Instant.now(clock), "Cancelled after failure"));
        p.transitionTo(CANCELLED);
        audit.record(actor, ActorType.USER, "DRAFT_CANCELLED", "generated_posts", id, null, null, null);
        return queries.get(id);
    }

    @Transactional
    public void cancelAfterRegenerate(UUID id, UUID actor) {
        GeneratedPost p = posts.findById(id).orElseThrow();
        if (p.getStatus() == GENERATED || p.getStatus() == UNDER_REVIEW) {
            p.transitionTo(CANCELLED);
            audit.record(actor, ActorType.USER, "DRAFT_REPLACED", "generated_posts", id, null, null,
                    "Replaced by regenerated drafts");
        }
    }

    /** Warnings a reviewer must explicitly acknowledge before approving. */
    static List<String> warnings(GeneratedPost p, DraftDto d) {
        List<String> w = new ArrayList<>();
        if (p.getVerificationStatus() == VerificationStatus.CONFLICTING_INFORMATION) w.add("sources conflict");
        if (!d.unverifiedClaims().isEmpty()) w.add("it contains unverified claims");
        if (d.riskFlags().stream().anyMatch(f -> !f.equals("HAS_UNVERIFIED_CLAIMS"))) w.add("it has risk warnings");
        if (p.isEditedByHuman()) w.add("you edited it, so re-check the facts");
        if (d.duplicateWarning() != null) w.add("it is similar to an earlier draft");
        if (p.getContent().matches("(?s).*https?://.*")) w.add("it contains a link (X charges more for posts with links)");
        return w;
    }

    private void invalidateApprovalAndSchedule(GeneratedPost p, String reason, UUID actor) {
        Instant now = Instant.now(clock);
        approvals.findLiveApproval(p.getId()).ifPresent(a -> a.invalidate(now, reason));
        queue.findActiveForPost(p.getId()).ifPresent(q -> {
            if (q.getStatus() == PublishingQueueEntry.Status.LOCKED) {
                throw new ConflictException("This post is being published right now");
            }
            q.cancel(reason);
        });
        if (p.getStatus() == SCHEDULED || p.getStatus() == APPROVED) p.transitionTo(UNDER_REVIEW);
    }

    private GeneratedPost load(UUID id, Long expectedVersion) {
        GeneratedPost p = posts.findById(id).orElseThrow(() -> new NotFoundException("Draft not found"));
        if (expectedVersion != null && p.getVersion() != expectedVersion) {
            throw new ConflictException("This draft changed since you opened it. Reload and review it again.");
        }
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
