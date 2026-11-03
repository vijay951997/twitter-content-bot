package com.ai.xbot.exception;

import java.time.Instant;
import java.util.List;

/** Uniform error body returned by every API endpoint. */
public record ApiError(Instant timestamp, int status, String error, String message,
                       String path, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}
}
