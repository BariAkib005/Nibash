package com.nibash.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Coercion helpers for the {@code Map<String, Object>} request bodies the CRUD controllers take.
 *
 * <p>The API accepts loosely-typed JSON (a number may arrive as {@code 5} or {@code "5"}), and every
 * module needs the same handful of conversions, so they live here once instead of being copied into
 * each controller. A malformed value raises the contract's own {@code 400}, never a 500.
 */
public final class Body {

    private Body() {
    }

    public static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    public static String requireStr(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(key + " is required");
        }
        return value.trim();
    }

    public static Long asLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(key + " must be a number");
        }
    }

    public static Long requireLong(Map<String, Object> body, String key) {
        Long value = asLong(body, key);
        if (value == null) {
            throw ApiException.badRequest(key + " is required");
        }
        return value;
    }

    public static Integer asInt(Map<String, Object> body, String key) {
        Long value = asLong(body, key);
        return value == null ? null : value.intValue();
    }

    public static BigDecimal asDecimal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(key + " must be a number");
        }
    }

    public static boolean asBool(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null && Boolean.parseBoolean(value.toString().trim());
    }

    public static LocalDate asDate(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(key + " must be a date (YYYY-MM-DD)");
        }
    }

    /** Accepts both {@code 2026-08-28T14:30:00} and the {@code 2026-08-28 14:30:00} browsers send. */
    public static LocalDateTime asDateTime(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace(' ', 'T');
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(key + " must be a date-time (YYYY-MM-DDTHH:MM)");
        }
    }

    public static LocalDateTime requireDateTime(Map<String, Object> body, String key) {
        LocalDateTime value = asDateTime(body, key);
        if (value == null) {
            throw ApiException.badRequest(key + " is required");
        }
        return value;
    }

    /** Nested arrays of objects, e.g. an invoice's {@code items} or a poll's {@code options}. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objectList(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    /** Enforces a closed vocabulary with the contract's phrasing. */
    public static void requireOneOf(String value, List<String> allowed, String field) {
        if (value != null && !allowed.contains(value)) {
            throw ApiException.badRequest(field + " must be one of " + allowed);
        }
    }
}
