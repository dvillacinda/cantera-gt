package com.dvillacinda.canteragt.academy_admin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

public record AdminAcademyResponse(
    UUID academyAdminId,
    UUID academyId,
    UUID userId,
    Status status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    
}
