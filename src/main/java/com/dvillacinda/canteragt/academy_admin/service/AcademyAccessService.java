package com.dvillacinda.canteragt.academy_admin.service;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.academy_admin.repository.AcademyAdminRepository;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Resolves tenant scope from the authenticated subject and active database assignments. */
@Service
@RequiredArgsConstructor
public class AcademyAccessService {
    private final UserRepository userRepository;
    private final AcademyAdminRepository academyAdminRepository;

    @Transactional(readOnly = true)
    public UUID requireAcademyAccess(Authentication authentication, UUID academyId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedAccessException("Authenticated user is required");
        }
        if (hasRole(authentication, "ROLE_SYSTEM_ADMIN")) {
            return academyId;
        }
        if (!hasRole(authentication, "ROLE_ACADEMY_ADMIN")) {
            throw new UnauthorizedAccessException("Academy administrator role is required");
        }

        String keycloakId = ((Jwt) authentication.getPrincipal()).getClaimAsString("sub");
        var user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UnauthorizedAccessException("User is not registered in this application"));
        if (!academyAdminRepository.existsByUser_UserIdAndAcademy_AcademyIdAndStatus(
                user.getUserId(), academyId, Status.ACTIVE)) {
            throw new UnauthorizedAccessException("No active administration assignment for the selected academy");
        }
        return academyId;
    }

    @Transactional(readOnly = true)
    public void requireUserBelongsToAcademy(Authentication authentication, UUID academyId, UUID targetUserId) {
        requireAcademyAccess(authentication, academyId);
        if (hasRole(authentication, "ROLE_SYSTEM_ADMIN")) {
            return;
        }
        var activeAssignments = academyAdminRepository.findByUser_UserIdAndStatus(targetUserId, Status.ACTIVE);
        if (activeAssignments.size() != 1
                || !activeAssignments.getFirst().getAcademy().getAcademyId().equals(academyId)) {
            // User-to-player/coach academy membership is not modeled yet. Fail closed until it exists.
            throw new NotFoundException("User not found in the selected academy");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
