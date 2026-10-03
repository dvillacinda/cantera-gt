package com.dvillacinda.canteragt.academy_admin.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcademyAdminUpdateRequest(
    UUID academyId,
    UUID userId,
    Status status

) {
    
}
