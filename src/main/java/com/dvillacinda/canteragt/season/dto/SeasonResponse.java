package com.dvillacinda.canteragt.season.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.season.enums.SeasonStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SeasonResponse(
        UUID seasonId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        SeasonStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
