package com.dvillacinda.canteragt.training_session.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.auth.dto.AuthenticatedUser;
import com.dvillacinda.canteragt.auth.service.CurrentUserService;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.repository.SessionStaffRepository;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrainingSessionAccessPolicy {
    private static final String SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";
    private static final String ACADEMY_ADMIN = "ROLE_ACADEMY_ADMIN";
    private static final String COACH = "ROLE_COACH";

    private final AcademyAccessService academyAccessService;
    private final AcademyCategoryRepository academyCategoryRepository;
    private final CoachAssignmentRepository coachAssignmentRepository;
    private final CurrentUserService currentUserService;
    private final SessionStaffRepository sessionStaffRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void requireManageAccess(Authentication authentication, UUID academyId, UUID academyCategoryId) {
        requireStaffRole(authentication);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            academyAccessService.requireAcademyAccess(authentication, academyId);
            requireCategoryInAcademy(academyCategoryId, academyId);
            return;
        }
        if (hasRole(authentication, ACADEMY_ADMIN)
                && hasAcademyScope(authentication, academyId, true)) {
            requireCategoryInAcademy(academyCategoryId, academyId);
            return;
        }
        if (!hasRole(authentication, COACH)) {
            throw new NotFoundException("Academy not found in the authenticated user's scope");
        }

        requireCategoryInAcademy(academyCategoryId, academyId);
        UUID coachId = requireCurrentCoachAssignment(authentication, academyCategoryId);
        academyAccessService.requireAcademyReadAccess(authentication, academyId);
        if (!coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                coachId, academyCategoryId, CoachRoleCode.HEAD_COACH, LocalDate.now(clock))) {
            throw new UnauthorizedAccessException("Head coach assignment is required to manage training sessions");
        }
        requireCategoryInAcademy(academyCategoryId, academyId);
    }

    @Transactional(readOnly = true)
    public void requireReadAccess(Authentication authentication, UUID academyId, UUID academyCategoryId) {
        requireStaffRole(authentication);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            academyAccessService.requireAcademyReadAccess(authentication, academyId);
            requireCategoryInAcademy(academyCategoryId, academyId);
            return;
        }
        if (hasRole(authentication, ACADEMY_ADMIN)
                && hasAcademyScope(authentication, academyId, false)) {
            requireCategoryInAcademy(academyCategoryId, academyId);
            return;
        }
        if (!hasRole(authentication, COACH)) {
            throw new NotFoundException("Academy not found in the authenticated user's scope");
        }

        requireCategoryInAcademy(academyCategoryId, academyId);
        requireCurrentCoachAssignment(authentication, academyCategoryId);
        academyAccessService.requireAcademyReadAccess(authentication, academyId);
        requireCategoryInAcademy(academyCategoryId, academyId);
    }

    @Transactional(readOnly = true)
    public void requireDataEntryAccess(Authentication authentication, TrainingSessionEntity session) {
        requireStaffRole(authentication);
        if (session == null || session.getAcademyCategory() == null
                || session.getAcademyCategory().getAcademy() == null) {
            throw new NotFoundException("Training session not found");
        }

        UUID academyId = session.getAcademyCategory().getAcademy().getAcademyId();
        UUID academyCategoryId = session.getAcademyCategory().getAcademyCategoryId();
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            academyAccessService.requireAcademyReadAccess(authentication, academyId);
            return;
        }
        if (hasRole(authentication, ACADEMY_ADMIN)
                && hasAcademyScope(authentication, academyId, false)) {
            return;
        }
        if (!hasRole(authentication, COACH)) {
            throw new NotFoundException("Training session not found in the authenticated user's scope");
        }

        UUID coachId = requireCurrentCoachAssignment(authentication, academyCategoryId);
        academyAccessService.requireAcademyReadAccess(authentication, academyId);
        if (coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                coachId, academyCategoryId, CoachRoleCode.HEAD_COACH, LocalDate.now(clock))) {
            return;
        }

        boolean assignedToSession = sessionStaffRepository
                .existsByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryIdAndCoach_CoachId(
                        session.getSessionId(), academyCategoryId, coachId);
        if (session.getStatus() != TrainingSessionStatus.IN_PROGRESS || !assignedToSession) {
            throw new UnauthorizedAccessException(
                    "Session staff may enter data only while the training session is in progress");
        }
    }

    private void requireStaffRole(Authentication authentication) {
        if (hasRole(authentication, SYSTEM_ADMIN)
                || hasRole(authentication, ACADEMY_ADMIN)
                || hasRole(authentication, COACH)) {
            return;
        }
        throw new UnauthorizedAccessException("Staff role is required for training session access");
    }

    private UUID requireCurrentCoachAssignment(
            Authentication authentication, UUID academyCategoryId) {
        var currentUser = currentUserService.getCurrentUser(AuthenticatedUser.fromAuthentication(authentication));
        if (currentUser.coach() == null) {
            throw new NotFoundException("Coach not found in the selected academy category");
        }
        UUID coachId = currentUser.coach().coachId();
        if (!coachAssignmentRepository.existsCurrentAssignmentForCategory(
                coachId, academyCategoryId, LocalDate.now(clock))) {
            throw new NotFoundException("Coach not found in the selected academy category");
        }
        return coachId;
    }

    private boolean hasAcademyScope(Authentication authentication, UUID academyId, boolean manage) {
        try {
            if (manage) {
                academyAccessService.requireAcademyAccess(authentication, academyId);
            } else {
                academyAccessService.requireAcademyReadAccess(authentication, academyId);
            }
            return true;
        } catch (UnauthorizedAccessException ex) {
            return false;
        }
    }

    private void requireCategoryInAcademy(UUID academyCategoryId, UUID academyId) {
        if (academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(
                academyCategoryId, academyId).isEmpty()) {
            throw new NotFoundException("Academy category not found in the selected academy");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
