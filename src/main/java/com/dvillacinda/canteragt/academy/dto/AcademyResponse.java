package com.dvillacinda.canteragt.academy.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcademyResponse(
    UUID academyId,
    String name,
    Status status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

}
