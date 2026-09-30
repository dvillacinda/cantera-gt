package com.dvillacinda.canteragt.coach.dto;

import com.dvillacinda.canteragt.user.dto.UserCreateRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CoachCreateRequest(
    @NotNull @Valid UserCreateRequest userCreateRequest,
    @NotBlank @Size (max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName
) {
    
}
