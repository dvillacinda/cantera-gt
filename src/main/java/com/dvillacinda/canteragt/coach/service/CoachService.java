package com.dvillacinda.canteragt.coach.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.coach.dto.CoachCreateRequest;
import com.dvillacinda.canteragt.coach.dto.CoachResponse;
import com.dvillacinda.canteragt.coach.dto.CoachUpdateRequest;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.mapper.CoachMapper;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CoachService {
    private final CoachRepository coachRepository;
    private final CoachMapper coachMapper;
    private final UserService userService;

    @Transactional 
    public CoachResponse createCoach(CoachCreateRequest coach) {
        var user = userService.createUserEntity(coach.userCreateRequest());
        try {
            userService.assignKeycloakRole(user.getKeycloakId(), "COACH");
            CoachEntity coachEntity = coachMapper.toEntity(coach, user);
            CoachEntity savedCoach = coachRepository.saveAndFlush(coachEntity);
            return coachMapper.toResponse(savedCoach);
        } catch (RuntimeException failure) {
            try {
                userService.deleteKeycloakUser(user.getKeycloakId());
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            throw failure;
        }
    }

    @Transactional(readOnly = true) 
    public CoachResponse getCoachById(UUID coachId) {
        CoachEntity coach = coachRepository.findById(coachId).orElseThrow(
            () -> new NotFoundException("Coach with id " + coachId + " not found")
        );
        return coachMapper.toResponse(coach);
    }

    @Transactional 
    public void deleteCoachById(UUID coachId) {
        CoachEntity coach = coachRepository.findById(coachId).orElseThrow(
            () -> new NotFoundException("Coach with id " + coachId + " not found")
        );
        UUID userId = coach.getUser().getUserId();
        coachRepository.delete(coach);
        coachRepository.flush();
        userService.deleteUserById(userId);
    }

    @Transactional
    public CoachResponse updateCoach(UUID coachId, CoachUpdateRequest request) {
        CoachEntity existing = coachRepository.findById(coachId).orElseThrow(
            () -> new NotFoundException("Coach with id " + coachId + " not found")
        );
        if (request.firstName() != null) existing.setFirstName(request.firstName());
        if (request.lastName() != null) existing.setLastName(request.lastName());
        return coachMapper.toResponse(coachRepository.save(existing));
    }
}
