package com.dvillacinda.canteragt.evaluation_template.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvaluationResponse(UUID evaluationId, UUID playerId, UUID templateId, Integer templateVersion,
        LocalDateTime evaluationDate, String status, UUID evaluatorCoachId, List<Result> results,
        String strengths, String improvementAreas, String generalComment) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(UUID criterionId, BigDecimal rawValue, Integer successes, Integer opportunities,
            Integer errorCount, Integer level, String laterality, String comment, List<Observation> observations) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Observation(Integer sequence, BigDecimal value, Integer level, Boolean success,
            boolean valid, String note) {}
}
