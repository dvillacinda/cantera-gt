package com.dvillacinda.canteragt.season.dto;

import java.time.LocalDate;

import com.dvillacinda.canteragt.season.enums.SeasonStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SeasonUpdateRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        SeasonStatus status) {

}
