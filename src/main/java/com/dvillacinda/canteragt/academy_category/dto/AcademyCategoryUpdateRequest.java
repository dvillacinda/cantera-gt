package com.dvillacinda.canteragt.academy_category.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.shared.enums.Status;

public record AcademyCategoryUpdateRequest(
    UUID academyId,
    UUID categoryId,
    UUID seasonId,
    Status status
) {
    
}
