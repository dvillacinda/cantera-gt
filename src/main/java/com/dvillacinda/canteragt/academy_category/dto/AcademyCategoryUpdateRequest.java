package com.dvillacinda.canteragt.academy_category.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcademyCategoryUpdateRequest(
    UUID academyId,
    UUID categoryId,
    UUID seasonId,
    AcademyCategoryStatus status
) {
    
}
