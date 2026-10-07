package com.dvillacinda.canteragt.player_assignment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;

public record PlayerAssignmentResponse(
        UUID playerAssignmentId,
        UUID playerId,
        UUID academyCategoryId,
        PlayerAssignmentStatus status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
