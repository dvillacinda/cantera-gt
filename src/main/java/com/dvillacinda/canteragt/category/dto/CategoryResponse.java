package com.dvillacinda.canteragt.category.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dvillacinda.canteragt.category.enums.CategoryCode;
import com.dvillacinda.canteragt.shared.enums.Status;

public record CategoryResponse(
        UUID categoryId,
        String name,
        Integer minAge,
        Integer maxAge,
        CategoryCode categoryCode,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
