package com.dvillacinda.canteragt.evaluation_template.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EvaluationCriterionResponse(UUID criterionId, String dimensionCode, String code, String name,
        String description, BigDecimal weight, BigDecimal minScore, BigDecimal maxScore, Integer displayOrder,
        String metricType, String unit, Integer attemptsCount, Integer minOpportunities, Integer scaleMin,
        Integer scaleMax, boolean commentRequired, boolean activeByDefault, boolean scored,
        String lateralityMode, String scoringMethod, String scoringConfig, List<ScaleLevel> scaleLevels) {
    public record ScaleLevel(Integer level, String label, String descriptor, BigDecimal score) {}
}
