package com.dvillacinda.canteragt.academy_category.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;

import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcademyCategoryCreateRequest(
    @NotNull UUID categoryId,
    @NotNull UUID seasonId,
    @NotNull AcademyCategoryStatus status
) {
    
}
