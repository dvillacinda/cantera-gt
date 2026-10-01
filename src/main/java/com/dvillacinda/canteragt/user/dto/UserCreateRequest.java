package com.dvillacinda.canteragt.user.dto;

import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserCreateRequest(

        @NotBlank @Email @Size(max = 254) String email,

        @NotBlank @Size(max = 100) String username,

        @NotNull Status status

) {

}
