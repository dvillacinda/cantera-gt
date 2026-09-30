package com.dvillacinda.canteragt.player.dto;

import java.time.LocalDate;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.user.dto.UserCreateRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record PlayerCreateRequest(
        @NotNull @Valid UserCreateRequest userCreateRequest,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Past LocalDate birthDate,
        @NotNull Sex sex,
        @NotBlank @Size(max = 30) String principalPosition,
        @Size(max = 255) String secondaryPosition

) {

}
