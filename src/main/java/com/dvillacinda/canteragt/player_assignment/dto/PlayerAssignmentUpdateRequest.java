package com.dvillacinda.canteragt.player_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlayerAssignmentUpdateRequest(
        UUID playerId,
        UUID academyCategoryId,
        PlayerAssignmentStatus status,
        LocalDate startDate,
        LocalDate endDate) {

}
