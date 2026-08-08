package com.nibash.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Framework-level fallback (spec §11) — never leak a stack trace to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An internal server error occurred"));
    }
}
