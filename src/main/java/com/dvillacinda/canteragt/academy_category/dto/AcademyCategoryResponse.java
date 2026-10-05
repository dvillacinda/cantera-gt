package com.dvillacinda.canteragt.academy_category.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

public record AcademyCategoryResponse(
    UUID academyCategoryId,
    UUID academyId,
    UUID categoryId,
    UUID seasonId,
    Status status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    
}
