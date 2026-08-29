package com.nibash.common;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps for columns the application stamps itself.
 *
 * <p>Every {@code DATETIME} column in the schema stores whole seconds, so a raw
 * {@code LocalDateTime.now()} loses its fraction the moment it is persisted. Truncating up front
 * means the value in a create response is byte-for-byte the value a later read returns — without
 * this, a client that caches a POST result and then refetches sees the timestamp appear to change.
 */
public final class Times {

    private Times() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** Truncates a caller-supplied timestamp to the precision the column can actually hold. */
    public static LocalDateTime toStorage(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS);
    }
}
