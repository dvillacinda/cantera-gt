package com.dvillacinda.canteragt.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

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
