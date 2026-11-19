package com.dvillacinda.canteragt.training_session.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Session staff entry: coach name and role only (no email, username or Keycloak id). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionStaffResponse(
        UUID sessionStaffId,
        UUID coachId,
        String firstName,
        String lastName,
        SessionParticipationType participationType,
        OffsetDateTime createdAt) {
}
