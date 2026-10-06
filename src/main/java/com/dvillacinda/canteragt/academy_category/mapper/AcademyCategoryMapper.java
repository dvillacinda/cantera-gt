package com.dvillacinda.canteragt.academy_category.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.dto.AcademyCategoryCreateRequest;
import com.dvillacinda.canteragt.academy_category.dto.AcademyCategoryResponse;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.category.entity.CategoryEntity;
import com.dvillacinda.canteragt.season.entity.SeasonEntity;

@Component
public class AcademyCategoryMapper {
    public AcademyCategoryEntity toEntity(AcademyCategoryCreateRequest request, AcademyEntity academy,
            SeasonEntity season, CategoryEntity category) {
        return AcademyCategoryEntity.builder()
                .academy(academy)
                .category(category)
                .season(season)
                .status(request.status())
                .build();
    }

    public AcademyCategoryEntity toEntity(AcademyCategoryResponse response) {
        return AcademyCategoryEntity.builder()
                .academyCategoryId(response.academyCategoryId())
                .status(response.status())
                .build();
    }

    public AcademyCategoryResponse toResponse(AcademyCategoryEntity entity) {
        return new AcademyCategoryResponse(
                entity.getAcademyCategoryId(),
                entity.getAcademy().getAcademyId(),
                entity.getCategory().getCategoryId(),
                entity.getSeason().getSeasonId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

}
