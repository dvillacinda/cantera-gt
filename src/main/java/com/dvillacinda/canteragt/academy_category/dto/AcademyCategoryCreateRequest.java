package com.dvillacinda.canteragt.academy_category.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;

import jakarta.validation.constraints.NotNull;

public record AcademyCategoryCreateRequest(
    @NotNull UUID categoryId,
    @NotNull UUID seasonId,
    @NotNull AcademyCategoryStatus status
) {
    
}
