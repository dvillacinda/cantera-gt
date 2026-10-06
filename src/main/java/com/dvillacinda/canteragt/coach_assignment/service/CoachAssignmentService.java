package com.dvillacinda.canteragt.coach_assignment.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentCreateRequest;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentResponse;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentUpdateRequest;
import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_assignment.mapper.CoachAssignmentMapper;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;
import com.dvillacinda.canteragt.coach_role.repository.CoachRoleRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Every method first resolves the academy the caller may act on, then only touches rows of that academy.
 * Resources of other academies answer 404 (not 403) so their existence is not disclosed.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CoachAssignmentService {
    private final CoachAssignmentRepository coachAssignmentRepository;
    private final AcademyCategoryRepository academyCategoryRepository;
    private final CoachRepository coachRepository;
    private final CoachRoleRepository coachRoleRepository;
    private final CoachAssignmentMapper coachAssignmentMapper;
    private final AcademyAccessService academyAccessService;

    public CoachAssignmentResponse createCoachAssignment(UUID requestedAcademyId,
            CoachAssignmentCreateRequest request, Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyAccess(authentication, requestedAcademyId);

        AcademyCategoryEntity academyCategory = findAcademyCategoryInAcademy(request.academyCategoryId(), academyId);
        CoachEntity coach = findCoach(request.coachId());
        CoachRoleEntity coachRole = findCoachRole(request.coachRoleId());
        requireValidDateRange(request.startDate(), request.endDate());

        if (request.status() == CoachAssignmentStatus.ACTIVE
                && coachAssignmentRepository.existsByCoach_CoachIdAndAcademyCategory_AcademyCategoryIdAndStatus(
                        coach.getCoachId(), academyCategory.getAcademyCategoryId(), CoachAssignmentStatus.ACTIVE)) {
            throw new ConflictException("Coach already has an active assignment in this academy category");
        }

        return coachAssignmentMapper.toResponse(coachAssignmentRepository
                .save(coachAssignmentMapper.toEntity(request, coach, academyCategory, coachRole)));
    }

    @Transactional(readOnly = true)
    public CoachAssignmentResponse getCoachAssignmentById(UUID coachAssignmentId, UUID requestedAcademyId,
            Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyReadAccess(authentication, requestedAcademyId);
        return coachAssignmentMapper.toResponse(findCoachAssignmentInAcademy(coachAssignmentId, academyId));
    }

    @Transactional(readOnly = true)
    public List<CoachAssignmentResponse> getCoachAssignments(UUID requestedAcademyId, Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyReadAccess(authentication, requestedAcademyId);
        return coachAssignmentRepository.findByAcademyCategory_Academy_AcademyId(academyId).stream()
                .map(coachAssignmentMapper::toResponse)
                .toList();
    }

    public CoachAssignmentResponse updateCoachAssignment(UUID coachAssignmentId, UUID requestedAcademyId,
            CoachAssignmentUpdateRequest request, Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyAccess(authentication, requestedAcademyId);
        CoachAssignmentEntity existing = findCoachAssignmentInAcademy(coachAssignmentId, academyId);

        if (request.academyCategoryId() != null) {
            // Prevents moving an assignment into a category of another academy.
            existing.setAcademyCategory(findAcademyCategoryInAcademy(request.academyCategoryId(), academyId));
        }
        if (request.coachId() != null) {
            existing.setCoach(findCoach(request.coachId()));
        }
        if (request.coachRoleId() != null) {
            existing.setCoachRole(findCoachRole(request.coachRoleId()));
        }
        if (request.startDate() != null) {
            existing.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            existing.setEndDate(request.endDate());
        }
        if (request.status() != null) {
            existing.setStatus(request.status());
        }
        requireValidDateRange(existing.getStartDate(), existing.getEndDate());

        return coachAssignmentMapper.toResponse(coachAssignmentRepository.save(existing));
    }

    public CoachAssignmentResponse updateCoachAssignmentStatusById(UUID coachAssignmentId, UUID requestedAcademyId,
            CoachAssignmentStatus status, Authentication authentication) {
        UUID academyId = academyAccessService.requireAcademyAccess(authentication, requestedAcademyId);
        CoachAssignmentEntity existing = findCoachAssignmentInAcademy(coachAssignmentId, academyId);
        existing.setStatus(status);
        return coachAssignmentMapper.toResponse(coachAssignmentRepository.save(existing));
    }

    private CoachAssignmentEntity findCoachAssignmentInAcademy(UUID coachAssignmentId, UUID academyId) {
        return coachAssignmentRepository
                .findByCoachAssignmentIdAndAcademyCategory_Academy_AcademyId(coachAssignmentId, academyId)
                .orElseThrow(() -> new NotFoundException(
                        "Coach assignment with id " + coachAssignmentId + " not found"));
    }

    private AcademyCategoryEntity findAcademyCategoryInAcademy(UUID academyCategoryId, UUID academyId) {
        return academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(academyCategoryId, academyId)
                .orElseThrow(() -> new NotFoundException(
                        "AcademyCategory with id " + academyCategoryId + " not found"));
    }

    private CoachEntity findCoach(UUID coachId) {
        return coachRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("Coach with id " + coachId + " not found"));
    }

    private CoachRoleEntity findCoachRole(UUID coachRoleId) {
        return coachRoleRepository.findById(coachRoleId)
                .orElseThrow(() -> new NotFoundException("Coach role with id " + coachRoleId + " not found"));
    }

    private void requireValidDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
    }
}
