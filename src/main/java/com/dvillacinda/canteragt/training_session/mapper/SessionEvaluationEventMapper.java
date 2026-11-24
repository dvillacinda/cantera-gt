package com.dvillacinda.canteragt.training_session.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionEventMetricResponse;
import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;

@Component
public class SessionEvaluationEventMapper {
    private final ZoneId zoneId;

    public SessionEvaluationEventMapper(@Value("${app.time-zone:America/Guatemala}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    /** Expects the event's template to be fetched and the metric criteria to be loaded (see the repositories). */
    public SessionEvaluationEventResponse toResponse(
            SessionEvaluationEventEntity event, List<EvaluationCriterionEntity> metrics) {
        return new SessionEvaluationEventResponse(
                event.getSessionEventId(),
                event.getTrainingSession().getSessionId(),
                event.getTemplate().getTemplateId(),
                event.getTemplate().getCode(),
                event.getTemplate().getName(),
                event.getBlockOrder(),
                event.getStatus(),
                toOffset(event.getStartedAt()),
                toOffset(event.getEndedAt()),
                toOffset(event.getCreatedAt()),
                toOffset(event.getUpdatedAt()),
                metrics.stream().map(this::toMetricResponse).toList());
    }

    private SessionEventMetricResponse toMetricResponse(EvaluationCriterionEntity criterion) {
        return new SessionEventMetricResponse(
                criterion.getCriterionId(),
                criterion.getCode(),
                criterion.getName(),
                criterion.getUnit(),
                criterion.getDisplayOrder());
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(zoneId).toOffsetDateTime();
    }
}
