package com.dvillacinda.canteragt.user.dto;

import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

        @NotBlank @Size(max = 36) String keycloakId,

        @NotBlank @Email @Size(max = 254) String email,

        @NotBlank @Size(max = 100) String username,

        @NotNull Status status

) {

}
