package com.dvillacinda.canteragt.training_session.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionEvaluationEventResponse(
        UUID sessionEventId,
        UUID sessionId,
        UUID templateId,
        String templateCode,
        String templateName,
        int blockOrder,
        SessionEvaluationEventStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<SessionEventMetricResponse> metrics) {
}
