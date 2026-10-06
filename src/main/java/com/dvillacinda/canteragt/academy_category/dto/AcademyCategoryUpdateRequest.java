package com.dvillacinda.canteragt.academy_category.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;

public record AcademyCategoryUpdateRequest(
    UUID academyId,
    UUID categoryId,
    UUID seasonId,
    AcademyCategoryStatus status
) {
    
}
