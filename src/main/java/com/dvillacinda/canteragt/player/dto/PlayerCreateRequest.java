package com.dvillacinda.canteragt.player.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.user.dto.UserCreateRequest;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlayerCreateRequest(
        @NotNull @Valid UserCreateRequest userCreateRequest,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Past LocalDate birthDate,
        @NotNull Sex sex,
        @NotNull UUID principalPositionId,
        @Size(max = 10) Set<@NotNull UUID> secondaryPositionsIds

) {

}
