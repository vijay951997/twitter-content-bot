package com.ai.xbot.service.ai;

/** Failure talking to the AI provider. {@link Kind} decides the HTTP status shown to the user. */
public class AiServiceException extends RuntimeException {

    public enum Kind {
        NOT_CONFIGURED,   // key or model missing
        AUTH_FAILED,      // 401/403 from provider
        RATE_LIMITED,     // 429 after retries
        TIMEOUT,          // no response in time after retries
        UNAVAILABLE,      // 5xx / network after retries
        BAD_RESPONSE,     // unparseable or schema-violating output
        REFUSED           // model declined the request
    }

    private final Kind kind;

    public AiServiceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiServiceException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}
