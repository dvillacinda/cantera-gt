package com.dvillacinda.canteragt.category.dto;

import com.dvillacinda.canteragt.category.enums.CategoryCode;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CategoryUpdateRequest(
    String name,
    Integer minAge,
    Integer maxAge,
    CategoryCode categoryCode
) {
    
}
