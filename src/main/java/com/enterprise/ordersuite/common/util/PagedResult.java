package com.enterprise.ordersuite.common.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Common wrapper for paginated API responses.
 * @param <T> The type of the items in the page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResult<T> {
    private List<T> items;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;

    /**
     * Factory method to create a PagedResult from a Spring Data Page object.
     * @param page The Spring Data Page object.
     * @param <T> The item type.
     * @return A PagedResult instance.
     */
    public static <T> PagedResult<T> of(Page<T> page) {
        return PagedResult.<T>builder()
                .items(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    /**
     * Factory method to create a PagedResult from a Spring Data Page object and a list of items.
     * Useful when items need to be mapped (e.g., from Entity to DTO).
     * @param page The original Spring Data Page object.
     * @param items The already mapped/transformed items.
     * @param <T> The item type.
     * @param <S> The source item type.
     * @return A PagedResult instance.
     */
    public static <T, S> PagedResult<T> of(Page<S> page, List<T> items) {
        return PagedResult.<T>builder()
                .items(items)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
