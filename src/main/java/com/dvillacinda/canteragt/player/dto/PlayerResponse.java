package com.dvillacinda.canteragt.player.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.position.dto.PositionResponse;
import com.dvillacinda.canteragt.user.dto.UserResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlayerResponse(
        UUID playerId,
        UserResponse userResponse,
        LocalDate birthDate,
        Sex sex,
        PositionResponse principalPosition,
        Set<PositionResponse> secondaryPositions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
