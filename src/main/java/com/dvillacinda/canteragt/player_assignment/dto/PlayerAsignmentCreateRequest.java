package com.dvillacinda.canteragt.player_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.player_assignment.enums.PlayerAsignmentStatus;

import jakarta.validation.constraints.NotNull;

public record PlayerAsignmentCreateRequest(
    @NotNull UUID playerId,
    @NotNull UUID academyCategoryId,
    @NotNull PlayerAsignmentStatus status,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {
    
}
