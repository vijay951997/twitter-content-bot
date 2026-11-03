package com.ai.xbot.integration.xapi;

/** An X API failure. {@code retryable} decides whether the publisher tries again later. */
public class XApiException extends RuntimeException {

    public enum Kind { UNAUTHORIZED, FORBIDDEN, DUPLICATE, RATE_LIMITED, SERVER_ERROR, NETWORK, BAD_REQUEST, PAYMENT_REQUIRED }

    private final Kind kind;
    private final int status;
    private final Long retryAfterSeconds;

    public XApiException(Kind kind, int status, String message, Long retryAfterSeconds) {
        super(message);
        this.kind = kind;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Kind getKind() { return kind; }
    public int getStatus() { return status; }
    public Long getRetryAfterSeconds() { return retryAfterSeconds; }

    public boolean isRetryable() {
        return kind == Kind.RATE_LIMITED || kind == Kind.SERVER_ERROR || kind == Kind.NETWORK;
    }
}
