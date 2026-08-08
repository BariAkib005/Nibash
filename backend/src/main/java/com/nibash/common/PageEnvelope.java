package com.nibash.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The DRF response envelope the frontend expects on every plain list endpoint,
 * page size 20 (spec §13.1).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PageEnvelope<T>(long count, String next, String previous, List<T> results) {

    public static final int PAGE_SIZE = 20;

    /** Wraps a page, mapping each entity through {@code mapper} and building absolute page links. */
    public static <E, D> PageEnvelope<D> of(Page<E> page, Function<E, D> mapper) {
        List<D> results = page.getContent().stream().map(mapper).toList();
        return new PageEnvelope<>(
                page.getTotalElements(),
                page.hasNext() ? pageLink(page.getNumber() + 2) : null,
                page.hasPrevious() ? pageLink(page.getNumber()) : null,
                results);
    }

    /** DRF pages are 1-based; Spring's are 0-based. */
    private static String pageLink(int oneBasedPage) {
        try {
            return ServletUriComponentsBuilder.fromCurrentRequest()
                    .replaceQueryParam("page", oneBasedPage)
                    .toUriString();
        } catch (IllegalStateException outsideRequest) {
            return null;
        }
    }
}
