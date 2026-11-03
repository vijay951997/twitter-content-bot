package com.ai.xbot.exception;

import com.ai.xbot.entity.enums.PostStatus;

public class InvalidStateTransitionException extends RuntimeException {
    private final PostStatus from;
    private final PostStatus to;

    public InvalidStateTransitionException(PostStatus from, PostStatus to) {
        super("Post status cannot change from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public PostStatus getFrom() { return from; }
    public PostStatus getTo() { return to; }
}
