package com.dvillacinda.canteragt.auth.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.position.dto.PositionResponse;
import com.dvillacinda.canteragt.user.dto.UserResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Profile of the authenticated user. {@code user} is null when the token belongs to a Keycloak
 * account that is not registered in CanteraGT (e.g. a SYSTEM_ADMIN created in the Keycloak console);
 * {@code player} and {@code coach} are null when the user has no such profile.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CurrentUserResponse(
        List<String> roles,
        List<AcademyProfile> academies,
        UserResponse user,
        PlayerProfile player,
        CoachProfile coach) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AcademyProfile(UUID academyId, String name) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PlayerProfile(
            UUID playerId,
            LocalDate birthDate,
            Sex sex,
            PositionResponse principalPosition,
            Set<PositionResponse> secondaryPositions) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CoachProfile(UUID coachId) {
    }
}
