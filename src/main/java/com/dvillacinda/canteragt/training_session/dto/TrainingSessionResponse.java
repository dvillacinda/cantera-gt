package com.dvillacinda.canteragt.training_session.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TrainingSessionResponse(
        UUID sessionId,
        UUID academyCategoryId,
        UUID locationId,
        UUID createdByCoachId,
        String name,
        OffsetDateTime sessionDate,
        TrainingSessionStatus status,
        String notes,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version) {
}
