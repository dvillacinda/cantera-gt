package com.dvillacinda.canteragt.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserResponse(
        UUID userId,
        String keycloakId,
        String email,
        String username,
        String firstName,
        String lastName,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
