package com.dvillacinda.canteragt.season.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

public record SeasonResponse(
        UUID seasonId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
