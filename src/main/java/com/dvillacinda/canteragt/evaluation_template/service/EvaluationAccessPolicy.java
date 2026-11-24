package com.dvillacinda.canteragt.evaluation_template.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Who can read and void an evaluation (ADR-0004, DP-2.4; T4.5-D1 and D2), for evaluations inside and outside events.
 *
 * <ul>
 *   <li>The coach who created it and the current HEAD_COACH of its category can read and void it.</li>
 *   <li>SYSTEM_ADMIN and the ACADEMY_ADMIN of its academy can read it. They cannot void it, because
 *       {@code voided_by_coach_id} needs a coach.</li>
 *   <li>Anyone else gets nothing, which callers turn into 404 so the evaluation's existence is not revealed.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EvaluationAccessPolicy {
    private static final String SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";
    private static final String ACADEMY_ADMIN = "ROLE_ACADEMY_ADMIN";

    private final UserRepository userRepository;
    private final CoachRepository coachRepository;
    private final CoachAssignmentRepository coachAssignmentRepository;
    private final AcademyAccessService academyAccessService;
    private final Clock clock;

    public enum Level { NONE, READ, VOID }

    /** The caller's access, and the caller's coach profile when the access comes from being a coach. */
    public record Access(Level level, CoachEntity coach, UserEntity user) {
        public boolean canRead() {
            return level != Level.NONE;
        }
    }

    /** Expects the evaluation's evaluator and academy category (with its academy) to be loaded. */
    public Access accessOf(EvaluationEntity evaluation, Authentication authentication) {
        Optional<UserEntity> user = currentUser(authentication);
        Optional<CoachEntity> coach = user.flatMap(u -> coachRepository.findByUser_UserId(u.getUserId()));
        if (coach.isPresent() && canVoid(evaluation, coach.get().getCoachId())) {
            return new Access(Level.VOID, coach.get(), user.get());
        }
        if (isAdminInScope(authentication, evaluation.getAcademyCategory().getAcademy().getAcademyId())) {
            return new Access(Level.READ, coach.orElse(null), user.orElse(null));
        }
        return new Access(Level.NONE, null, null);
    }

    private boolean canVoid(EvaluationEntity evaluation, UUID coachId) {
        return evaluation.getEvaluator().getCoachId().equals(coachId)
                || coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(coachId,
                        evaluation.getAcademyCategory().getAcademyCategoryId(), CoachRoleCode.HEAD_COACH,
                        LocalDate.now(clock));
    }

    /** Write-level academy access: SYSTEM_ADMIN, or ACADEMY_ADMIN of that academy. Coaches never pass here. */
    private boolean isAdminInScope(Authentication authentication, UUID academyId) {
        if (!hasRole(authentication, SYSTEM_ADMIN) && !hasRole(authentication, ACADEMY_ADMIN)) {
            return false;
        }
        try {
            academyAccessService.requireAcademyAccess(authentication, academyId);
            return true;
        } catch (UnauthorizedAccessException | NotFoundException ex) {
            return false;
        }
    }

    private Optional<UserEntity> currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return userRepository.findByKeycloakId(jwt.getSubject());
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
