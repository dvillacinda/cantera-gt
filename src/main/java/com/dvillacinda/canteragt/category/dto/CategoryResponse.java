package com.dvillacinda.canteragt.category.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.shared.enums.Status;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CategoryResponse(
        UUID categoryId,
        String name,
        Integer minAge,
        Integer maxAge,
        CategoryCode categoryCode,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
