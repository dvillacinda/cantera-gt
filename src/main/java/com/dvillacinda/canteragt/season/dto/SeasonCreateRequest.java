package com.dvillacinda.canteragt.season.dto;

import java.time.LocalDate;

import com.dvillacinda.canteragt.shared.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SeasonCreateRequest(
    @NotBlank String name,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull Status status
) {
    
}
