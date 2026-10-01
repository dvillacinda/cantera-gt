package com.dvillacinda.canteragt.player.dto;

import java.util.Set;
import java.util.UUID;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

/**
 * PATCH semantics: null fields are left unchanged; provided fields must not be blank.
 * secondaryPosition is optional in the model, so an empty string clears it.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlayerUpdateRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String firstName,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String lastName,
        Sex sex,
        UUID principalPositionId,
        @Size(max = 10) Set<@NotNull UUID> secondaryPositionsIds) {
}
