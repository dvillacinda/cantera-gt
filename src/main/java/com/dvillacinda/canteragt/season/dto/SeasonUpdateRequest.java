package com.dvillacinda.canteragt.season.dto;

import java.time.LocalDate;

import com.dvillacinda.canteragt.season.enums.SeasonStatus;

public record SeasonUpdateRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        SeasonStatus status) {

}
