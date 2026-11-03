package com.ai.xbot.service.workflow;

import static com.ai.xbot.entity.enums.PostStatus.*;

import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.exception.InvalidStateTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the draft lifecycle.
 *
 * <pre>
 * GENERATED ─► UNDER_REVIEW ─► APPROVED ─► SCHEDULED ─► PUBLISHING ─► PUBLISHED
 *      │             │            │            │             │
 *      └─► REJECTED ◄┘            │            │             └─► FAILED ─► SCHEDULED (retry)
 *                                 └── edit ────┴──► UNDER_REVIEW (approval invalidated)
 * </pre>
 *
 * Key guarantees:
 * <ul>
 *   <li>Nothing reaches PUBLISHING except from SCHEDULED, and nothing reaches SCHEDULED
 *       except from APPROVED (or FAILED, which was itself approved).</li>
 *   <li>GENERATED and UNDER_REVIEW can never jump straight to SCHEDULED/PUBLISHING.</li>
 *   <li>REJECTED, PUBLISHED and CANCELLED are terminal.</li>
 * </ul>
 */
public final class PostStatusPolicy {

    private static final Map<PostStatus, Set<PostStatus>> ALLOWED = new EnumMap<>(PostStatus.class);

    static {
        ALLOWED.put(GENERATED,    EnumSet.of(UNDER_REVIEW, REJECTED, CANCELLED));
        ALLOWED.put(UNDER_REVIEW, EnumSet.of(APPROVED, REJECTED, CANCELLED));
        ALLOWED.put(APPROVED,     EnumSet.of(SCHEDULED, UNDER_REVIEW, REJECTED, CANCELLED));
        ALLOWED.put(SCHEDULED,    EnumSet.of(PUBLISHING, UNDER_REVIEW, CANCELLED));
        ALLOWED.put(PUBLISHING,   EnumSet.of(PUBLISHED, FAILED));
        ALLOWED.put(FAILED,       EnumSet.of(SCHEDULED, CANCELLED));
        ALLOWED.put(REJECTED,     EnumSet.noneOf(PostStatus.class));
        ALLOWED.put(PUBLISHED,    EnumSet.noneOf(PostStatus.class));
        ALLOWED.put(CANCELLED,    EnumSet.noneOf(PostStatus.class));
    }

    /** Statuses whose content may still be edited. Editing APPROVED/SCHEDULED sends it back to review. */
    private static final Set<PostStatus> EDITABLE = EnumSet.of(GENERATED, UNDER_REVIEW, APPROVED, SCHEDULED);

    /** Statuses that require a fresh approval if the content changes. */
    private static final Set<PostStatus> APPROVAL_BOUND = EnumSet.of(APPROVED, SCHEDULED);

    private PostStatusPolicy() {}

    public static boolean canTransition(PostStatus from, PostStatus to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    public static void assertTransition(PostStatus from, PostStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }

    public static boolean isEditable(PostStatus status) {
        return EDITABLE.contains(status);
    }

    /** True when editing a post in this status must invalidate its approval. */
    public static boolean editInvalidatesApproval(PostStatus status) {
        return APPROVAL_BOUND.contains(status);
    }

    public static boolean isTerminal(PostStatus status) {
        return ALLOWED.get(status).isEmpty();
    }

    public static Set<PostStatus> allowedFrom(PostStatus from) {
        return EnumSet.copyOf(ALLOWED.get(from));
    }
}
