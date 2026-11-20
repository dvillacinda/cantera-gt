package com.dvillacinda.canteragt.shared.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Stable snake_case page body. Spring Data's own {@code Page} serialization uses camelCase
 * ({@code totalElements}), which breaks the API naming convention.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PageResponse<T>(List<T> content, PageMetadata page) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), new PageMetadata(
                page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages()));
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageMetadata(int size, int number, long totalElements, int totalPages) {
    }
}
