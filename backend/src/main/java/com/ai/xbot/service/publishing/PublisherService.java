package com.ai.xbot.service.publishing;

import static com.ai.xbot.entity.enums.PostStatus.*;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.entity.*;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.integration.xapi.XApiClient;
import com.ai.xbot.integration.xapi.XApiException;
import com.ai.xbot.repository.*;
import com.ai.xbot.service.ContentHasher;
import com.ai.xbot.service.SettingsService;
import com.ai.xbot.service.notify.NotificationService;
import com.ai.xbot.service.text.XTextLength;
import com.ai.xbot.service.xaccount.XAccountService;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes due, approved posts to X.
 *
 * Before every send, inside a transaction, it re-checks: the post is SCHEDULED, its approval is live and
 * belongs to it, the approved content hash equals the hash of the text being sent, the post isn't already
 * published, and publishing is switched on. Any failed check means the post is NOT sent.
 *
 * Duplicate protection: queue rows are claimed with FOR UPDATE SKIP LOCKED; the post moves to PUBLISHING
 * (optimistic lock) before the X call; published_posts.post_id is unique; a worker that dies mid-send leaves
 * the post marked FAILED ("check X before retrying") instead of retrying automatically.
 */
@Service
public class PublisherService {

    private static final Logger log = LoggerFactory.getLogger(PublisherService.class);
    static final Duration LOCK_TTL = Duration.ofMinutes(5);
    static final Duration RETRY_LATENESS_CAP = Duration.ofHours(6);
    static final int BATCH = 5;

    private final PublishingQueueRepository queue;
    private final GeneratedPostRepository posts;
    private final PostApprovalRepository approvals;
    private final PublishedPostRepository published;
    private final ConnectedAccountRepository accounts;
    private final XAccountService xAccounts;
    private final XApiClient x;
    private final ContentHasher hasher;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String workerId;

    public PublisherService(PublishingQueueRepository queue, GeneratedPostRepository posts, PostApprovalRepository approvals,
                            PublishedPostRepository published, ConnectedAccountRepository accounts, XAccountService xAccounts,
                            XApiClient x, ContentHasher hasher, SettingsService settings, AuditService audit,
                            NotificationService notifications, TransactionTemplate tx, Clock clock) {
        this.queue = queue;
        this.posts = posts;
        this.approvals = approvals;
        this.published = published;
        this.accounts = accounts;
        this.xAccounts = xAccounts;
        this.x = x;
        this.hasher = hasher;
        this.settings = settings;
        this.audit = audit;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
        String host;
        try { host = InetAddress.getLocalHost().getHostName(); } catch (Exception e) { host = "worker"; }
        this.workerId = host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** One publisher cycle. Returns the number of posts published. */
    public int runOnce() {
        recoverStaleLocks();
        List<UUID> claimed = claimDue();
        int done = 0;
        for (UUID id : claimed) {
            try {
                if (publishOne(id)) done++;
            } catch (RuntimeException e) {
                log.error("Unexpected publisher error for queue entry {}", id, e);
            }
        }
        return done;
    }

    List<UUID> claimDue() {
        Instant now = Instant.now(clock);
        List<UUID> ids = tx.execute(s -> {
            List<UUID> out = new ArrayList<>();
            for (PublishingQueueEntry q : queue.lockDue(now, BATCH)) {
                q.lock(workerId, now.plus(LOCK_TTL));
                out.add(q.getId());
            }
            return out;
        });
        return ids == null ? List.of() : ids;
    }

    /** Negative #12 (restart with pending posts): handle rows a dead worker left locked. */
    void recoverStaleLocks() {
        Instant now = Instant.now(clock);
        tx.executeWithoutResult(s -> {
            for (PublishingQueueEntry q : queue.findStaleLocks(now)) {
                GeneratedPost p = posts.findById(q.getPostId()).orElse(null);
                if (p != null && p.getStatus() == PUBLISHING) {
                    // We can't know whether X received it: never resend automatically.
                    if (published.existsByPostId(p.getId())) {
                        p.transitionTo(PUBLISHED);
                        q.done();
                    } else {
                        p.transitionTo(FAILED);
                        q.fail("Interrupted while publishing. Check your X profile before retrying, the post may already be live.");
                        notifications.notify("PUBLISH_FAILURE", "Publishing was interrupted",
                                "Check your X profile before retrying this post.", "generated_posts", p.getId());
                    }
                } else {
                    q.retryAt(now, "Recovered after an interrupted run (not sent)");
                }
                audit.record(null, ActorType.SCHEDULER, "PUBLISH_LOCK_RECOVERED", "publishing_queue", q.getId(), null,
                        Map.of("queueStatus", q.getStatus().name()), null);
            }
        });
    }

    private record Prepared(UUID postId, UUID approvalId, UUID accountId, String content) {}

    boolean publishOne(UUID queueId) {
        Instant now = Instant.now(clock);
        Prepared prepared = tx.execute(s -> preflight(queueId, now));
        if (prepared == null) return false;

        ConnectedAccount account = accounts.findById(prepared.accountId()).orElseThrow();
        try {
            XApiClient.CreatedPost created;
            try {
                created = x.createPost(xAccounts.accessToken(account, false), prepared.content());
            } catch (XApiException e) {
                if (e.getKind() != XApiException.Kind.UNAUTHORIZED) throw e;
                // Negative #6/#11: token expired or revoked — refresh once and retry.
                created = x.createPost(xAccounts.accessToken(account, true), prepared.content());
            }
            XApiClient.CreatedPost result = created;
            tx.executeWithoutResult(s -> {
                GeneratedPost p = posts.findById(prepared.postId()).orElseThrow();
                published.save(new PublishedPost(p.getId(), prepared.approvalId(), prepared.accountId(), result.id(),
                        prepared.content(), Instant.now(clock)));
                p.transitionTo(PUBLISHED);
                queue.findById(queueId).orElseThrow().done();
                audit.record(null, ActorType.SCHEDULER, "POST_PUBLISHED", "generated_posts", p.getId(), null,
                        Map.of("xPostId", result.id(), "approvalId", prepared.approvalId().toString()), null);
            });
            notifications.notify("PUBLISH_SUCCESS", "Posted to X", preview(prepared.content()), "generated_posts", prepared.postId());
            log.info("Published post {} as X post {}", prepared.postId(), result.id());
            return true;
        } catch (XApiException e) {
            handleFailure(queueId, prepared, e);
            return false;
        }
    }

    /** All checks that must pass before anything is sent. Returns null when the post must not be sent now. */
    private Prepared preflight(UUID queueId, Instant now) {
        PublishingQueueEntry q = queue.findById(queueId).orElse(null);
        if (q == null || q.getStatus() != PublishingQueueEntry.Status.LOCKED) return null;
        GeneratedPost p = posts.findById(q.getPostId()).orElse(null);
        if (p == null) { q.cancel("Post no longer exists"); return null; }

        if (published.existsByPostId(p.getId())) { q.done(); return null; } // idempotent: already live

        if (p.getStatus() != SCHEDULED) {
            q.cancel("Post is " + p.getStatus() + ", not scheduled");
            return null;
        }
        PostApproval approval = approvals.findById(q.getApprovalId()).orElse(null);
        if (approval == null || !approval.isLiveApproval() || !approval.getPostId().equals(p.getId())) {
            q.cancel("Approval is no longer valid");
            p.transitionTo(UNDER_REVIEW);
            audit.record(null, ActorType.SCHEDULER, "PUBLISH_BLOCKED", "generated_posts", p.getId(), null,
                    Map.of("reason", "approval not valid"), null);
            return null;
        }
        String currentHash = hasher.hash(p.getContent());
        if (!currentHash.equals(approval.getApprovedContentHash()) || !currentHash.equals(p.getContentHash())) {
            // Negative #2: text differs from what was approved.
            approval.invalidate(now, "Content changed after approval");
            q.cancel("Content changed after approval");
            p.transitionTo(UNDER_REVIEW);
            audit.record(null, ActorType.SCHEDULER, "PUBLISH_BLOCKED", "generated_posts", p.getId(), null,
                    Map.of("reason", "content changed after approval"), null);
            notifications.notify("PUBLISH_FAILURE", "Post held back", "Its text changed after approval. Review it again.",
                    "generated_posts", p.getId());
            return null;
        }

        Duration late = Duration.between(q.getScheduledAt(), now);
        int windowMin = Integer.parseInt(settings.rawValue("publishing.missed_window_minutes").orElse("60"));
        boolean firstTry = q.getAttemptCount() <= 1;
        if ((firstTry && late.toMinutes() > windowMin) || late.compareTo(RETRY_LATENESS_CAP) > 0) {
            approval.invalidate(now, "Missed publishing window");
            q.skip("Missed its publishing time by " + late.toMinutes() + " minutes");
            p.transitionTo(UNDER_REVIEW);
            notifications.notify("PUBLISH_FAILURE", "Scheduled post missed its time",
                    "It was not posted. Approve it again with a new time if it is still relevant.", "generated_posts", p.getId());
            return null;
        }
        if (!settings.isPublishingEnabled()) {
            q.deferWithoutAttempt(now.plus(Duration.ofMinutes(2)), "Publishing switch is off");
            return null;
        }
        Optional<ConnectedAccount> acc = accounts.current().filter(a -> a.getStatus() == ConnectedAccount.Status.ACTIVE);
        if (acc.isEmpty()) {
            q.deferWithoutAttempt(now.plus(Duration.ofMinutes(5)), "No X account connected");
            return null;
        }
        if (XTextLength.weightedLength(p.getContent()) > XTextLength.MAX_WEIGHTED_LENGTH) {
            q.fail("Too long for X");
            p.transitionTo(PUBLISHING);
            p.transitionTo(FAILED);
            return null;
        }
        p.transitionTo(PUBLISHING); // optimistic lock: fails if the post changed concurrently
        return new Prepared(p.getId(), approval.getId(), acc.get().getId(), p.getContent());
    }

    private void handleFailure(UUID queueId, Prepared prepared, XApiException e) {
        int maxAttempts = Integer.parseInt(settings.rawValue("publishing.max_attempts").orElse("5"));
        tx.executeWithoutResult(s -> {
            PublishingQueueEntry q = queue.findById(queueId).orElseThrow();
            GeneratedPost p = posts.findById(prepared.postId()).orElseThrow();
            p.transitionTo(FAILED);
            if (e.isRetryable() && q.getAttemptCount() < maxAttempts) {
                long waitSeconds = e.getRetryAfterSeconds() != null ? Math.min(e.getRetryAfterSeconds(), 3600)
                        : Math.min(30L * 60, 60L << Math.max(0, q.getAttemptCount() - 1)); // 1, 2, 4, 8, 16 min
                p.transitionTo(SCHEDULED);
                q.retryAt(Instant.now(clock).plusSeconds(Math.max(30, waitSeconds)), e.getMessage());
                log.warn("Publish attempt {} failed for {}: {} — retrying in {}s", q.getAttemptCount(), p.getId(), e.getMessage(), waitSeconds);
            } else {
                q.fail(e.getMessage());
                notifications.notify("PUBLISH_FAILURE", "Couldn't post to X", e.getMessage(), "generated_posts", p.getId());
                log.warn("Publishing failed permanently for {}: {}", p.getId(), e.getMessage());
            }
            audit.record(null, ActorType.SCHEDULER, "PUBLISH_ATTEMPT_FAILED", "generated_posts", p.getId(), null,
                    Map.of("kind", e.getKind().name(), "httpStatus", e.getStatus(), "attempt", q.getAttemptCount(),
                            "willRetry", q.getStatus() == PublishingQueueEntry.Status.PENDING), e.getMessage());
        });
    }

    private static String preview(String s) {
        return s.length() <= 120 ? s : s.substring(0, 120) + "…";
    }
}
