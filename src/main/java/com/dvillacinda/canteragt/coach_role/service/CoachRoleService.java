package com.dvillacinda.canteragt.coach_role.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dvillacinda.canteragt.coach_role.dto.CoachRoleResponse;
import com.dvillacinda.canteragt.coach_role.mapper.CoachRoleMapper;
import com.dvillacinda.canteragt.coach_role.repository.CoachRoleRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CoachRoleService {
    private final CoachRoleRepository coachRoleRepository;
    private final CoachRoleMapper coachRoleMapper;

    public CoachRoleResponse getCoachRoleById(UUID coachRoleId) {
        var coachRole = coachRoleRepository.findById(coachRoleId)
                .orElseThrow(() -> new NotFoundException("Coach role with id " + coachRoleId + " not founded"));

        return coachRoleMapper.toResponse(coachRole);
    }
}
