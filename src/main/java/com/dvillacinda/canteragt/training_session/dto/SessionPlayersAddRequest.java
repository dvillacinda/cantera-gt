package com.dvillacinda.canteragt.training_session.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Exactly one of {@code player_ids} (1..60 explicit players) or {@code all_active=true} (every player with a
 * current assignment in the session's category) must be sent; the service enforces the exclusivity.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionPlayersAddRequest(
        @Size(max = SessionPlayersAddRequest.MAX_BATCH_SIZE) List<@NotNull UUID> playerIds,
        Boolean allActive) {
    public static final int MAX_BATCH_SIZE = 60;

    public boolean isAllActive() {
        return Boolean.TRUE.equals(allActive);
    }

    public boolean hasPlayerIds() {
        return playerIds != null && !playerIds.isEmpty();
    }
}
