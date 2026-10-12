package com.dvillacinda.canteragt.evaluation_template.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvaluationCreateRequest(
        @NotNull UUID templateId,
        @NotNull Integer templateVersion,
        @NotEmpty @Valid List<ResultRequest> results,
        @Size(max = 5000) String strengths,
        @Size(max = 5000) String improvementAreas,
        @Size(max = 5000) String generalComment) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ResultRequest(
            @NotNull UUID criterionId,
            BigDecimal rawValue,
            Integer successes,
            Integer opportunities,
            Integer errorCount,
            Integer level,
            String laterality,
            @Size(max = 500) String comment,
            @Valid List<ObservationRequest> observations) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ObservationRequest(
            @NotNull Integer sequence,
            BigDecimal value,
            Integer level,
            Boolean success,
            Boolean valid,
            @Size(max = 255) String note) {}
}
