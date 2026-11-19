package com.dvillacinda.canteragt.training_session.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Roster entry as seen by staff: name, position and attendance only (no email, username or Keycloak id). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionPlayerResponse(
        UUID sessionPlayerId,
        UUID playerId,
        String firstName,
        String lastName,
        String principalPosition,
        AttendanceStatus attendanceStatus,
        OffsetDateTime updatedAt) {
}
