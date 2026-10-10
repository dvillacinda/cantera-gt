package com.dvillacinda.canteragt.evaluation_template.dto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvaluationTemplateResponse(
    UUID templateId,
    String name,
    String description,
    Integer version,
    EvaluationTemplateStatus status, 
    String code,
    String objective,
    String instructions,
    Boolean isSessionIntegrated,
    Integer groupSizeMin,
    Integer groupSizeMax,
    Integer frequencyMinDays,
    Integer frequencyMaxDays,
    Integer validityDays,
    String scoreAggregation,
    Boolean isOfficial, 
    UUID ownerAcademyId,
    Set<UUID> childTemplateIds, 
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    
}
