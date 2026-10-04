package com.dvillacinda.canteragt.category.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.category.dto.CategoryCreateRequest;
import com.dvillacinda.canteragt.category.dto.CategoryResponse;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.shared.enums.Status;

@Component
public class CategoryMapper {

    public CategoryEntity toEntity(CategoryCreateRequest request) {
        return CategoryEntity.builder()
                .name(request.name())
                .minAge(request.minAge())
                .maxAge(request.maxAge())
                .categoryCode(request.categoryCode())
                .status(Status.ACTIVE)
                .build();
    }
    public CategoryEntity toEntity(CategoryResponse response) {
        return CategoryEntity.builder()
                .name(response.name())
                .minAge(response.minAge())
                .maxAge(response.maxAge())
                .categoryCode(response.categoryCode())
                .status(Status.ACTIVE)
                .build();
    }

    public CategoryResponse toResponse(CategoryEntity category) {
        return new CategoryResponse(
                category.getCategoryId(),
                category.getName(),
                category.getMinAge(),
                category.getMaxAge(),
                category.getCategoryCode(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

}
