package com.dvillacinda.canteragt.coach_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;

import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoachAssignmentCreateRequest(
    @NotNull UUID academyCategoryId,
    @NotNull UUID coachId,
    @NotNull UUID coachRoleId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull CoachAssignmentStatus status
) {
    
}
