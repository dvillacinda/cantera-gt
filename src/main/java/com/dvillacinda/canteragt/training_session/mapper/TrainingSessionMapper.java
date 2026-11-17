package com.dvillacinda.canteragt.training_session.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.training_session.dto.TrainingSessionResponse;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;

@Component
public class TrainingSessionMapper {
    private final ZoneId zoneId;

    public TrainingSessionMapper(@Value("${app.time-zone:America/Guatemala}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    public TrainingSessionResponse toResponse(TrainingSessionEntity entity) {
        return new TrainingSessionResponse(
                entity.getSessionId(),
                entity.getAcademyCategory().getAcademyCategoryId(),
                entity.getLocation() == null ? null : entity.getLocation().getLocationId(),
                entity.getCreatedByCoach().getCoachId(),
                entity.getName(),
                toOffsetDateTime(entity.getSessionDate()),
                entity.getStatus(),
                entity.getNotes(),
                toOffsetDateTime(entity.getStartedAt()),
                toOffsetDateTime(entity.getEndedAt()),
                toOffsetDateTime(entity.getCreatedAt()),
                toOffsetDateTime(entity.getUpdatedAt()),
                entity.getVersion());
    }

    public LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value.atZoneSameInstant(zoneId).toLocalDateTime();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(zoneId).toOffsetDateTime();
    }
}
