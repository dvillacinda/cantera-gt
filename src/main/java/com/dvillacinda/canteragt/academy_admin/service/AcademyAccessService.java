package com.dvillacinda.canteragt.academy_admin.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy.repository.AcademyRepository;
import com.dvillacinda.canteragt.academy_admin.repository.AcademyAdminRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.player_assignment.repository.PlayerAssignmentRepository;
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
    private static final String PLAYER = "ROLE_PLAYER";

    private final UserRepository userRepository;
    private final AcademyRepository academyRepository;
    private final AcademyAdminRepository academyAdminRepository;
    private final CoachAssignmentRepository coachAssignmentRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAssignmentRepository playerAssignmentRepository;
    private final CoachRepository coachRepository;
    private final Clock clock;

    /** Write access: SYSTEM_ADMIN, or ACADEMY_ADMIN with an active administration assignment. */
    @Transactional(readOnly = true)
    public UUID requireAcademyAccess(Authentication authentication, UUID academyId) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            requireExistingAcademy(academyId);
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
            requireExistingAcademy(academyId);
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

    /**
     * Self-service access: the caller must hold the PLAYER role, be registered with an account that may
     * still log in (access tokens outlive a Keycloak disable) and own a player profile.
     * Returns that player's id, which is the only scope a player may read.
     */
    @Transactional(readOnly = true)
    public UUID requireAuthenticatedPlayerId(Authentication authentication) {
        requireAuthenticated(authentication);
        if (!hasRole(authentication, PLAYER)) {
            throw new UnauthorizedAccessException("Player role is required");
        }
        UserEntity user = requireRegisteredUser(authentication);
        if (user.getStatus() == null || !user.getStatus().canLogIn()) {
            throw new UnauthorizedAccessException("User account is not active");
        }
        return playerRepository.findByUser_UserId(user.getUserId())
                .map((playerEntity) -> playerEntity.getPlayerId())
                .orElseThrow(() -> new UnauthorizedAccessException("User has no player profile"));
    }

    @Transactional(readOnly = true)
    public void requireUserBelongsToAcademy(Authentication authentication, UUID academyId, UUID targetUserId) {
        requireAcademyAccess(authentication, academyId);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        // Resolve the target's actual profile type from persisted relationships. Caller roles describe
        // the authorizing actor and must never be used to infer the target user's academy membership.
        boolean belongs = (playerRepository.findByUser_UserId(targetUserId).isPresent()
                && playerAssignmentRepository.existsActiveAssignmentInAcademy(targetUserId, academyId, today))
                || (coachRepository.findByUser_UserId(targetUserId).isPresent()
                        && coachAssignmentRepository.existsActiveAssignmentForUserInAcademy(
                                targetUserId, academyId, today))
                || academyAdminRepository.existsByUser_UserIdAndAcademy_AcademyIdAndStatus(
                        targetUserId, academyId, Status.ACTIVE);
        if (!belongs) {
            throw new NotFoundException("User not found in the selected academy");
        }
    }

    /** Resolves a player's tenant scope through current player and coach assignments. */
    @Transactional(readOnly = true)
    public void requirePlayerAccess(Authentication authentication, UUID academyId, UUID playerId) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            requireExistingAcademy(academyId);
            return;
        }
        boolean admin = hasRole(authentication, ACADEMY_ADMIN);
        boolean coachRole = hasRole(authentication, COACH);
        if (!admin && !coachRole) {
            throw new UnauthorizedAccessException("Staff role is required");
        }
        UserEntity user = requireRegisteredUser(authentication);
        if (user.getStatus() == null || !user.getStatus().canLogIn()) {
            throw new UnauthorizedAccessException("User account is not active");
        }
        requireAcademyReadAccess(authentication, academyId);
        LocalDate today = LocalDate.now(clock);
        if (admin && playerAssignmentRepository.existsCurrentAssignmentInAcademy(playerId, academyId, today)) return;
        if (coachRole) {
            var coach = coachRepository.findByUser_UserId(user.getUserId())
                    .orElseThrow(() -> new UnauthorizedAccessException("Authenticated user has no coach profile"));
            boolean assigned = playerAssignmentRepository.findCurrentAssignments(playerId, today).stream()
                    .filter(a -> academyId.equals(a.getAcademyCategory().getAcademy().getAcademyId()))
                    .anyMatch(a -> coachAssignmentRepository.existsCurrentAssignmentForCategory(coach.getCoachId(),
                            a.getAcademyCategory().getAcademyCategoryId(), today));
            if (assigned) return;
        }
        throw new NotFoundException("Player not found in the selected academy or category");
    }

    /** Resolves a coach profile through a current assignment in the selected academy. */
    @Transactional(readOnly = true)
    public void requireCoachAccess(Authentication authentication, UUID academyId, UUID coachId) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, SYSTEM_ADMIN)) {
            requireExistingAcademy(academyId);
            return;
        }
        if (!hasRole(authentication, ACADEMY_ADMIN)) {
            throw new UnauthorizedAccessException("Academy administrator role is required");
        }
        UserEntity user = requireRegisteredUser(authentication);
        if (user.getStatus() == null || !user.getStatus().canLogIn()) {
            throw new UnauthorizedAccessException("User account is not active");
        }
        requireAcademyAccess(authentication, academyId);
        if (!coachAssignmentRepository.existsCurrentAssignmentForCoachInAcademy(coachId, academyId,
                LocalDate.now(clock))) {
            throw new NotFoundException("Coach not found in the selected academy");
        }
    }

    private boolean isAcademyAdminOf(UserEntity user, UUID academyId) {
        return academyAdminRepository.existsByUser_UserIdAndAcademy_AcademyIdAndStatus(
                user.getUserId(), academyId, Status.ACTIVE);
    }

    private void requireExistingAcademy(UUID academyId) {
        if (!academyRepository.existsById(academyId)) {
            throw new NotFoundException("Academy with id " + academyId + " not found");
        }
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
