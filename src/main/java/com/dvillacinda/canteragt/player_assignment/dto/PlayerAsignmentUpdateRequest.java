package com.dvillacinda.canteragt.player_assignment.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.dvillacinda.canteragt.player_assignment.enums.PlayerAsignmentStatus;


public record PlayerAsignmentUpdateRequest(
        UUID playerId,
        UUID academyCategoryId,
        PlayerAsignmentStatus status,
        LocalDate startDate,
        LocalDate endDate) {

}
