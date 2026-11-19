package com.dvillacinda.canteragt.training_session.mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayerResponse;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;

@Component
public class SessionPlayerMapper {
    private final ZoneId zoneId;

    public SessionPlayerMapper(@Value("${app.time-zone:America/Guatemala}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    /** Expects player, player.user and player.principalPosition to be fetched (see SessionPlayerRepository). */
    public SessionPlayerResponse toResponse(SessionPlayerEntity entity) {
        PlayerEntity player = entity.getPlayer();
        return new SessionPlayerResponse(
                entity.getSessionPlayerId(),
                player.getPlayerId(),
                player.getUser().getFirstName(),
                player.getUser().getLastName(),
                player.getPrincipalPosition() == null ? null
                        : player.getPrincipalPosition().getPositionCode().name(),
                entity.getAttendanceStatus(),
                toOffsetDateTime(entity.getUpdatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(zoneId).toOffsetDateTime();
    }
}
