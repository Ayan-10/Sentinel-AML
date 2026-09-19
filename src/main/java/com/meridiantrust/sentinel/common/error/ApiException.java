package com.meridiantrust.sentinel.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for all deliberately-signalled API failures.
 *
 * <p>Carrying the HTTP status and a stable machine-readable {@code errorCode}
 * on the exception keeps status-code decisions in the domain, where the context
 * to make them correctly exists, rather than in a handler guessing from the
 * exception type.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // --- Concrete failures ------------------------------------------------

    public static class NotFound extends ApiException {
        public NotFound(String entity, String id) {
            super(HttpStatus.NOT_FOUND, "resource-not-found", "%s '%s' was not found".formatted(entity, id));
        }
    }

    public static class Validation extends ApiException {
        public Validation(String message) {
            super(HttpStatus.BAD_REQUEST, "validation-failed", message);
        }
    }

    /** Semantically well-formed but not permissible in the current state. */
    public static class IllegalTransition extends ApiException {
        public IllegalTransition(String message) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "illegal-state-transition", message);
        }
    }

    /** Concurrent modification — the caller's view was stale. */
    public static class Conflict extends ApiException {
        public Conflict(String message) {
            super(HttpStatus.CONFLICT, "conflict", message);
        }
    }
}
