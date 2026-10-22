package com.dvillacinda.canteragt.player.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.position.dto.PositionResponse;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Public self-service profile fields; excludes local identity keys and account administration data. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlayerSelfProfileResponse(
        UUID playerId,
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Sex sex,
        PositionResponse principalPosition,
        Set<PositionResponse> secondaryPositions) {
}
