package com.dvillacinda.canteragt.academy.dto;

import com.dvillacinda.canteragt.academy.enums.Status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcademyCreateRequest(
    @NotBlank String name,
    @NotNull Status status
) {
    
}
