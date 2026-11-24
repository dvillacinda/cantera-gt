package com.dvillacinda.canteragt.training_session.dto;

import java.util.UUID;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionEventMetricResponse(
        UUID criterionId,
        String code,
        String name,
        String unit,
        Integer displayOrder) {
}
