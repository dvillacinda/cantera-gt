package com.dvillacinda.canteragt.player.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.user.dto.UserResponse;

public record PlayerResponse(
        UUID playerId,
        UserResponse userResponse,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Sex sex,
        PositionEntity principalPosition,
        Set<PositionEntity> secondaryPositions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
