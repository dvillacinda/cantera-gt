package com.dvillacinda.canteragt.coach_assignment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;


public record CoachAssignmentResponse(
        UUID coachAssignmentId,
        UUID academyCategoryId,
        UUID coachId,
        UUID coachRoleId,
        LocalDate startDate,
        LocalDate endDate,
        CoachAssignmentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
