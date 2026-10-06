package com.dvillacinda.canteragt.academy_category.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;

public record AcademyCategoryResponse(
    UUID academyCategoryId,
    UUID academyId,
    UUID categoryId,
    UUID seasonId,
    AcademyCategoryStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    
}
