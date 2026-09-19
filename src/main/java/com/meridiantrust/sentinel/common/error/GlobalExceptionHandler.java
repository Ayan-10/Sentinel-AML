package com.meridiantrust.sentinel.common.error;

import com.meridiantrust.sentinel.common.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Single translation point from exception to HTTP response.
 *
 * <p>NFR (API standards): every failure leaves the application in the same
 * RFC 7807 {@code ProblemDetail} shape, so a client can parse one error format
 * rather than several. Controllers therefore contain no try/catch.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://sentinel.meridiantrust.com/errors/";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        log.warn("API exception [{}] on {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        return problem(ex.getStatus(), ex.getErrorCode(), titleFor(ex.getStatus()), ex.getMessage(), request);
    }

    /** Bean-validation failures on request bodies — reported field by field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
                "%d field(s) rejected".formatted(errors.size()), request);
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request body",
                "Request body could not be parsed as JSON.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "type-mismatch", "Invalid parameter",
                "Parameter '%s' has an invalid value: '%s'".formatted(ex.getName(), ex.getValue()), request);
    }

    /**
     * NFR (Concurrency): two analysts editing the same alert must not silently
     * overwrite one another. Optimistic locking surfaces as 409, never a
     * last-writer-wins.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {}", request.getRequestURI());
        return problem(HttpStatus.CONFLICT, "concurrent-modification", "Concurrent modification",
                "This record was modified by another user. Reload and retry.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Access denied",
                "Your role does not permit this operation.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // Log the stack trace, but never leak internals to the caller.
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "An unexpected error occurred. Quote the traceId when reporting this.", request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String title,
                                  String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + code));
        pd.setTitle(title);
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("errorCode", code);
        pd.setProperty("traceId", MDC.get(CorrelationIdFilter.TRACE_ID));
        return pd;
    }

    private String titleFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Resource not found";
            case BAD_REQUEST -> "Validation failed";
            case CONFLICT -> "Conflict";
            case UNPROCESSABLE_ENTITY -> "Operation not permitted in current state";
            case FORBIDDEN -> "Access denied";
            default -> status.getReasonPhrase();
        };
    }
}
