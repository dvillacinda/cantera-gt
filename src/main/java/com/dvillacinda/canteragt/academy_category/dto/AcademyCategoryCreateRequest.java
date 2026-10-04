package com.dvillacinda.canteragt.academy_category.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.constraints.NotNull;

public record AcademyCategoryCreateRequest(
    @NotNull UUID academyId,
    @NotNull UUID categoryId,
    @NotNull UUID seasonId,
    @NotNull Status status
) {
    
}
