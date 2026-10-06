package com.dvillacinda.canteragt.coach_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;

import jakarta.validation.constraints.NotNull;

public record CoachAssignmentCreateRequest(
    @NotNull UUID academyCategoryId,
    @NotNull UUID coachId,
    @NotNull UUID coachRoleId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull CoachAssignmentStatus status
) {
    
}
