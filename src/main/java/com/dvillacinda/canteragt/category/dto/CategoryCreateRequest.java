package com.dvillacinda.canteragt.category.dto;

import com.dvillacinda.canteragt.category.enums.CategoryCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CategoryCreateRequest(
    @NotBlank String name,
    @NotNull Integer minAge,
    @NotNull Integer maxAge,
    @NotNull CategoryCode categoryCode
) {
    
}
