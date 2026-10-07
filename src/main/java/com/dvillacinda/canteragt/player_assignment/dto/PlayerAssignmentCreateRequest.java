package com.dvillacinda.canteragt.player_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;

import jakarta.validation.constraints.NotNull;

public record PlayerAssignmentCreateRequest(
    @NotNull UUID playerId,
    @NotNull UUID academyCategoryId,
    @NotNull PlayerAssignmentStatus status,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {
    
}
