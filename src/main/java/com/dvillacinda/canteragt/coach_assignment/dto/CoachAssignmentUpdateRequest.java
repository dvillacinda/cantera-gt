package com.dvillacinda.canteragt.coach_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoachAssignmentUpdateRequest(
        UUID academyCategoryId,
        UUID coachId,
        UUID coachRoleId,
        LocalDate startDate,
        LocalDate endDate,
        CoachAssignmentStatus status) {

}
