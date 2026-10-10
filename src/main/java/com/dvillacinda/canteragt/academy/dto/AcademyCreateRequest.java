package com.dvillacinda.canteragt.academy.dto;

import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcademyCreateRequest(
    @NotBlank String name,
    @NotNull Status status
) {
    
}
