package com.bipros.common.dto;

import java.util.List;

/**
 * Standard paged response envelope. Exposes {@code totalElements} / {@code totalPages} /
 * {@code currentPage} / {@code pageSize} at the top level to match the Spring Page envelope
 * (so callers used to {@code response.data.totalElements} just work), and also as a nested
 * {@code pagination} object for callers that prefer a grouped shape.
 */
public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int pageSize,
        Pagination pagination
) {
    public record Pagination(
            long totalElements,
            int totalPages,
            int currentPage,
            int pageSize
    ) {}

    public static <T> PagedResponse<T> of(List<T> content, long totalElements, int totalPages,
                                           int currentPage, int pageSize) {
        return new PagedResponse<>(
            content,
            totalElements,
            totalPages,
            currentPage,
            pageSize,
            new Pagination(totalElements, totalPages, currentPage, pageSize));
    }
}
