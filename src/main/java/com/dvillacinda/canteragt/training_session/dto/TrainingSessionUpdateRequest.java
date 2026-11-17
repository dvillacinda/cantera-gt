package com.dvillacinda.canteragt.training_session.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TrainingSessionUpdateRequest(
        @Size(max = 150) String name,
        OffsetDateTime sessionDate,
        UUID locationId,
        @Size(max = 2000) String notes) {
}
