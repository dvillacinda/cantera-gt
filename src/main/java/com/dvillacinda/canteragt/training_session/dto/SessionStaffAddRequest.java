package com.dvillacinda.canteragt.training_session.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionStaffAddRequest(
        @NotNull UUID coachId,
        @NotNull SessionParticipationType participationType) {
}
