package com.dvillacinda.canteragt.academy_admin.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy_admin.repository.AcademyAdminRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resolves tenant scope from the authenticated subject and active database assignments.
 * The Keycloak role only says what kind of user the caller is; the academy they may act on
 * always comes from the database, never from the X-Academy-Id header alone.
 */
@Service
@RequiredArgsConstructor
public class AcademyAccessService {
    private static final String SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";
    private static final String ACADEMY_ADMIN = "ROLE_ACADEMY_ADMIN";
    private static final String COACH = "ROLE_COACH";

    private final UserRepository userRepository;
    private final AcademyAdminRepository academyAdminRepository;
    private final CoachAssignmentRepository coachAssignmentRepository;
    private final Clock clock;

    /** Write access: SYSTEM_ADMIN, or ACADEMY_ADMIN with an active administration assignment. */
    @Transactional(readOnly = true)
    public UUID requireAcademyAccess(Authentication authentication, UUID academyId) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            return academyId;
        }
        if (!hasRole(authentication, ACADEMY_ADMIN)) {
            throw new UnauthorizedAccessException("Academy administrator role is required");
        }
        if (!isAcademyAdminOf(requireRegisteredUser(authentication), academyId)) {
            throw new UnauthorizedAccessException("No active administration assignment for the selected academy");
        }
        return academyId;
    }

    /**
     * Read access: everything {@link #requireAcademyAccess} allows, plus a COACH holding an assignment
     * in force today (ACTIVE and within its date range) in a category of the selected academy.
     */
    @Transactional(readOnly = true)
    public UUID requireAcademyReadAccess(Authentication authentication, UUID academyId) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            return academyId;
        }
        boolean isAdmin = hasRole(authentication, ACADEMY_ADMIN);
        boolean isCoach = hasRole(authentication, COACH);
        if (!isAdmin && !isCoach) {
            throw new UnauthorizedAccessException("Academy administrator or coach role is required");
        }

        UserEntity user = requireRegisteredUser(authentication);
        if (isAdmin && isAcademyAdminOf(user, academyId)) {
            return academyId;
        }
        if (isCoach && coachAssignmentRepository.existsActiveAssignmentInAcademy(
                user.getKeycloakId(), academyId, LocalDate.now(clock))) {
            return academyId;
        }
        throw new UnauthorizedAccessException("No active assignment for the selected academy");
    }

    @Transactional(readOnly = true)
    public void requireUserBelongsToAcademy(Authentication authentication, UUID academyId, UUID targetUserId) {
        requireAcademyAccess(authentication, academyId);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            return;
        }
        var activeAssignments = academyAdminRepository.findByUser_UserIdAndStatus(targetUserId, Status.ACTIVE);
        if (activeAssignments.size() != 1
                || !activeAssignments.getFirst().getAcademy().getAcademyId().equals(academyId)) {
            // User-to-player/coach academy membership is not modeled yet. Fail closed until it exists.
            throw new NotFoundException("User not found in the selected academy");
        }
    }

    private boolean isAcademyAdminOf(UserEntity user, UUID academyId) {
        return academyAdminRepository.existsByUser_UserIdAndAcademy_AcademyIdAndStatus(
                user.getUserId(), academyId, Status.ACTIVE);
    }

    private UserEntity requireRegisteredUser(Authentication authentication) {
        String keycloakId = ((Jwt) authentication.getPrincipal()).getClaimAsString("sub");
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UnauthorizedAccessException("User is not registered in this application"));
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedAccessException("Authenticated user is required");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
