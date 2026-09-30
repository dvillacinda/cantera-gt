package com.dvillacinda.canteragt.user.dto;

import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH semantics: null fields are left unchanged; provided fields must not be blank.
 */
public record UserUpdateRequest(
        @Email @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 254) String email,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 100) String username,
        Status status) {
}
