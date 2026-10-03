package com.dvillacinda.canteragt.category.dto;

import com.dvillacinda.canteragt.category.enums.CategoryCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryCreateRequest(
    @NotBlank String name,
    @NotNull Integer minAge,
    @NotNull Integer maxAge,
    @NotNull CategoryCode categoryCode
) {
    
}
