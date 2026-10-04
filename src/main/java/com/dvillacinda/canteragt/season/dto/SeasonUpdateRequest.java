package com.dvillacinda.canteragt.season.dto;

import java.time.LocalDate;

import com.dvillacinda.canteragt.shared.enums.Status;

public record SeasonUpdateRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Status status) {

}
