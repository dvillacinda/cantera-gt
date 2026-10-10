package com.dvillacinda.canteragt.season.dto;

import java.time.LocalDate;

import com.dvillacinda.canteragt.season.enums.SeasonStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SeasonCreateRequest(
    @NotBlank String name,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull SeasonStatus status
) {
    
}
