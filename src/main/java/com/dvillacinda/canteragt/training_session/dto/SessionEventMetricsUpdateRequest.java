package com.dvillacinda.canteragt.training_session.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Full replacement of an event's active metrics (1 to 3 scored metrics of the event's template). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionEventMetricsUpdateRequest(
        @NotNull @Size(min = 1, max = 3) List<@NotNull UUID> metricCriterionIds) {
}
