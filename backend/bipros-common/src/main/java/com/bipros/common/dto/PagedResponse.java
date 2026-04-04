package com.bipros.common.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
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
        return new PagedResponse<>(content, new Pagination(totalElements, totalPages, currentPage, pageSize));
    }
}
