package com.dvillacinda.canteragt.season.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.season.dto.SeasonCreateRequest;
import com.dvillacinda.canteragt.season.dto.SeasonResponse;
import com.dvillacinda.canteragt.season.entity.SeasonEntity;

@Component
public class SeasonMapper {
    public SeasonResponse toResponse(SeasonEntity entity) {
        return new SeasonResponse(
                entity.getSeasonId(),
                entity.getName(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public SeasonEntity toEntity(SeasonCreateRequest request) {
        return SeasonEntity.builder()
                .name(request.name())
                .status(request.status())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
    }

    public SeasonEntity toEntity(SeasonResponse response) {
        return SeasonEntity.builder()
                .seasonId(response.seasonId())
                .name(response.name())
                .status(response.status())
                .startDate(response.startDate())
                .endDate(response.endDate())
                .build();
    }
}
