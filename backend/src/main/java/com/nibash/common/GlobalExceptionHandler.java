package com.nibash.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Implements the error conventions in spec §11. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("detail", ex.getMessage()));
    }

    /**
     * Bean-validation failures render as the DRF-style field map, e.g.
     * {@code {"end_time": ["End time must be after start time."]}}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> {
            @SuppressWarnings("unchecked")
            List<String> messages = (List<String>) body.computeIfAbsent(
                    err.getField(), k -> new java.util.ArrayList<String>());
            messages.add(err.getDefaultMessage());
        });
        if (body.isEmpty()) {
            body.put("detail", "Bad request");
        }
        return ResponseEntity.badRequest().body(body);
    }

    /** DRF-style field maps thrown explicitly by a controller (spec §11). */
    @ExceptionHandler(FieldException.class)
    public ResponseEntity<Map<String, Object>> handleFieldErrors(FieldException ex) {
        return ResponseEntity.badRequest().body(Map.copyOf(ex.getErrors()));
    }

    /**
     * A foreign-key {@code RESTRICT} or unique-key clash that no controller pre-checked — e.g.
     * deleting a row other records still point at. It is the client's request that cannot be
     * honoured, not a server fault, so it is a {@code 400} in the contract's shape rather than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.info("Integrity violation turned away: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("detail", "This record is still in use elsewhere, so that change isn't allowed."));
    }

    /**
     * Unreadable bodies (malformed JSON) and unconvertible parameters ({@code ?building_id=abc},
     * {@code /api/units/abc/}) are the client's mistake — the spec's framework {@code 400}.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleUnreadable(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Bad request"));
    }

    /** An upload over the servlet's 10 MB ceiling never reaches a controller's own size check. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(Map.of("detail", "file must be 10MB or smaller"));
    }

    /**
     * Framework-level fallback (spec §11) — never leak a stack trace to the client.
     *
     * <p>Spring's own exceptions for an unknown path, a wrong HTTP method or an unsupported content
     * type already know their 4xx status; those keep it, with the spec's fallback wording. Only
     * genuine faults become a 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            int status = framework.getStatusCode().value();
            return ResponseEntity.status(status).body(Map.of("error", fallbackMessage(status)));
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An internal server error occurred"));
    }

    /** The spec §11 wording for the framework fallbacks, plus plain phrases for the rest. */
    private static String fallbackMessage(int status) {
        return switch (status) {
            case 400 -> "Bad request";
            case 403 -> "You do not have permission to access this resource";
            case 404 -> "The requested resource was not found";
            case 405 -> "Method not allowed";
            case 415 -> "Unsupported media type";
            default -> {
                HttpStatus known = HttpStatus.resolve(status);
                yield known == null ? "Bad request" : known.getReasonPhrase();
            }
        };
    }
}
