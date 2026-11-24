package com.dvillacinda.canteragt.training_session.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * New evaluation event. {@code block_order} is assigned by the server (T4.3-D2). When
 * {@code metric_criterion_ids} is omitted, the template's {@code is_active_by_default} scored metrics are loaded
 * (T4.3-D3); when sent, it must hold 1 to 3 scored metrics of the same template.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionEvaluationEventCreateRequest(
        @NotNull UUID templateId,
        @Size(min = 1, max = 3) List<@NotNull UUID> metricCriterionIds) {
}
