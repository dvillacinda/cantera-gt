package com.dvillacinda.canteragt.coach_role.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.coach_role.dto.CoachRoleCreateRequest;
import com.dvillacinda.canteragt.coach_role.dto.CoachRoleResponse;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;

@Component
public class CoachRoleMapper {

    public CoachRoleEntity toEntity(CoachRoleCreateRequest request) {
        return CoachRoleEntity.builder()
                .name(request.name())
                .coachRoleCode(request.coachRoleCode())
                .description(request.description())
                .build();
    }

    public CoachRoleEntity toEntity(CoachRoleResponse response) {
        return CoachRoleEntity.builder()
                .coachRoleId(response.coachRoleId())
                .name(response.name())
                .coachRoleCode(response.coachRoleCode())
                .description(response.description())
                .build();
    }

    public CoachRoleResponse toResponse(CoachRoleEntity entity) {
        return new CoachRoleResponse(
                entity.getCoachRoleId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCoachRoleCode());
    }
}
