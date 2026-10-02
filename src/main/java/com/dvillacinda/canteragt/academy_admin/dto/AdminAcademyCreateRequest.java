package com.dvillacinda.canteragt.academy_admin.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminAcademyCreateRequest(
    @NotNull UUID academyId,
    @NotNull UUID userId,
    @NotNull Status status
) {
    
}
