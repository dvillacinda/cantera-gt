package com.dvillacinda.canteragt.coach_role.dto;

import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoachRoleCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 500) String description,
    @NotNull CoachRoleCode coachRoleCode
) {
    
}
