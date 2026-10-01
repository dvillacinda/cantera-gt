package com.dvillacinda.canteragt.coach.dto;

import com.dvillacinda.canteragt.user.dto.UserCreateRequest;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming (PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoachCreateRequest(
    @NotNull @Valid UserCreateRequest userCreateRequest,
    @NotBlank @Size (max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName
) {
    
}
