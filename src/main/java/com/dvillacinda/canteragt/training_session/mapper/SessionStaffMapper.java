package com.dvillacinda.canteragt.training_session.mapper;

import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.training_session.dto.SessionStaffResponse;
import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;

@Component
public class SessionStaffMapper {
    private final ZoneId zoneId;

    public SessionStaffMapper(@Value("${app.time-zone:America/Guatemala}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    /** Expects coach and coach.user to be fetched (see SessionStaffRepository). */
    public SessionStaffResponse toResponse(SessionStaffEntity entity) {
        return new SessionStaffResponse(
                entity.getSessionStaffId(),
                entity.getCoach().getCoachId(),
                entity.getCoach().getUser().getFirstName(),
                entity.getCoach().getUser().getLastName(),
                entity.getParticipationType(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().atZone(zoneId).toOffsetDateTime());
    }
}
