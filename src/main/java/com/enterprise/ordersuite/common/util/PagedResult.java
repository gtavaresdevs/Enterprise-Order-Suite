package com.enterprise.ordersuite.common.util;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * The paginated list shape (API conventions §9.1):
 * {@code { items, page, size, totalItems, totalPages }}.
 * @param <T> The type of the items in the page.
 */
public record PagedResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    // Items already mapped from the page's entities.
    public static <T, S> PagedResult<T> of(Page<S> page, List<T> items) {
        return new PagedResult<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
