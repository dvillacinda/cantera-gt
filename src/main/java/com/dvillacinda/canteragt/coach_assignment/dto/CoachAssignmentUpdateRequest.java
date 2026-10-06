package com.dvillacinda.canteragt.coach_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;


public record CoachAssignmentUpdateRequest(
        UUID academyCategoryId,
        UUID coachId,
        UUID coachRoleId,
        LocalDate startDate,
        LocalDate endDate,
        CoachAssignmentStatus status) {

}
