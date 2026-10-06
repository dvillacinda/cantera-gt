package com.dvillacinda.canteragt.coach_assignment.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.academy_category.mapper.AcademyCategoryMapper;
import com.dvillacinda.canteragt.academy_category.service.AcademyCategoryService;
import com.dvillacinda.canteragt.coach.mapper.CoachMapper;
import com.dvillacinda.canteragt.coach.service.CoachService;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentCreateRequest;
import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_role.mapper.CoachRoleMapper;
import com.dvillacinda.canteragt.coach_role.service.CoachRoleService;

import lombok.RequiredArgsConstructor;

@Component 
@RequiredArgsConstructor 
public class CoachAssignmentMapper {
    private final CoachService coachService;
    private final AcademyCategoryService academyCategoryService;
    private final CoachRoleService coachRoleService;

    private final CoachMapper coachMapper;
    private final AcademyCategoryMapper academyCategoryMapper;
    private final CoachRoleMapper coachRoleMapper;


    public CoachAssignmentEntity toEntity(CoachAssignmentCreateRequest request) {
        
        var coach = coachService.getCoachById(request.coachId());
        var academyCategory = academyCategoryService.getAcademyCategoryById(request.academyCategoryId(), null, null);
        var coachRole = coachRoleService.getCoachRoleById(request.coachRoleId());
        
        return CoachAssignmentEntity.builder()
            .coach(coachMapper.toEntity(coach))
            .academyCategory(academyCategoryMapper.toEntity(academyCategory))
            .coachRole(coachRoleMapper.toEntity(coachRole))
            .build();
            
    }
}
