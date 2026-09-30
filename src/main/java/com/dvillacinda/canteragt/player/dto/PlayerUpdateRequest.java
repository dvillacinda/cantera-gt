package com.dvillacinda.canteragt.player.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.player.enums.Sex;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH semantics: null fields are left unchanged; provided fields must not be blank.
 * secondaryPosition is optional in the model, so an empty string clears it.
 */
public record PlayerUpdateRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String firstName,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String lastName,
        Sex sex,
        UUID principalPositionId,
        @Size(max = 255) String secondaryPosition) {
}
