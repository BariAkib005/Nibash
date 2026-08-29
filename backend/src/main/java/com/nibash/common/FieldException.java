package com.nibash.common;

import java.util.List;
import java.util.Map;

/**
 * A validation failure that renders as the DRF-style field map rather than {@code {"detail": ...}}
 * — e.g. {@code {"end_time": ["End time must be after start time."]}} (spec §11).
 *
 * <p>The distinction matters to the frontend: {@link ApiException} messages surface as a toast,
 * while these land under the offending input. {@code non_field_errors} is the conventional key for
 * a rule that belongs to the form as a whole, such as a booking clash.
 */
public class FieldException extends RuntimeException {

    private final Map<String, List<String>> errors;

    public FieldException(String field, String message) {
        super(message);
        this.errors = Map.of(field, List.of(message));
    }

    public FieldException(Map<String, List<String>> errors) {
        super(errors.values().stream().flatMap(List::stream).findFirst().orElse("Bad request"));
        this.errors = errors;
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }
}
