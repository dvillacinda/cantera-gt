package com.dvillacinda.canteragt.user.dto;

import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * PATCH semantics: null fields are left unchanged; provided fields must not be blank.
 * The username is not editable: it is the Keycloak login name (preferred_username).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserUpdateRequest(
        @Email @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 254) String email,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String firstName,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String lastName,
        Status status) {
}
