package com.dvillacinda.canteragt.coach_assignment.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentCreateRequest;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentResponse;
import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;

/** Pure mapping: related entities are resolved (and tenant-checked) by the service, never here. */
@Component
public class CoachAssignmentMapper {

    public CoachAssignmentEntity toEntity(CoachAssignmentCreateRequest request, CoachEntity coach,
            AcademyCategoryEntity academyCategory, CoachRoleEntity coachRole) {
        return CoachAssignmentEntity.builder()
                .coach(coach)
                .academyCategory(academyCategory)
                .coachRole(coachRole)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(request.status())
                .build();
    }

    public CoachAssignmentResponse toResponse(CoachAssignmentEntity entity) {
        return new CoachAssignmentResponse(
                entity.getCoachAssignmentId(),
                entity.getAcademyCategory().getAcademyCategoryId(),
                entity.getCoach().getCoachId(),
                entity.getCoachRole().getCoachRoleId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
