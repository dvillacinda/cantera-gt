package com.dvillacinda.canteragt.player.dto;

import java.time.LocalDate;
import java.util.Set;

import com.dvillacinda.canteragt.player.enums.Sex;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.user.dto.UserCreateRequest;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

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
        @NotNull PositionEntity principalPosition,
        Set<PositionEntity> secondaryPositions

) {

}
