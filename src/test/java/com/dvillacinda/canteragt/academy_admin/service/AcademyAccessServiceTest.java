package com.dvillacinda.canteragt.academy_admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.dvillacinda.canteragt.academy_admin.repository.AcademyAdminRepository;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AcademyAccessServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final UUID OWN_ACADEMY = UUID.randomUUID();
    private static final UUID OTHER_ACADEMY = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private AcademyAdminRepository academyAdminRepository;
    @Mock
    private CoachAssignmentRepository coachAssignmentRepository;
    @Mock
    private PlayerRepository playerRepository;

    private AcademyAccessService academyAccessService;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);
        academyAccessService = new AcademyAccessService(userRepository, academyAdminRepository,
                coachAssignmentRepository, playerRepository, fixed);
    }

    private static Authentication token(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        return new JwtAuthenticationToken(jwt,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private UserEntity registered(String keycloakId) {
        UserEntity user = UserEntity.builder().userId(UUID.randomUUID()).keycloakId(keycloakId)
                .status(UserStatus.ACTIVE).build();
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void coachWithAssignmentInForceCanReadOwnAcademy() {
        registered("kc-coach");
        when(coachAssignmentRepository.existsActiveAssignmentInAcademy("kc-coach", OWN_ACADEMY, TODAY))
                .thenReturn(true);

        assertEquals(OWN_ACADEMY,
                academyAccessService.requireAcademyReadAccess(token("kc-coach", "ROLE_COACH"), OWN_ACADEMY));
    }

    @Test
    void coachCannotReadAnotherAcademy() {
        registered("kc-coach");
        when(coachAssignmentRepository.existsActiveAssignmentInAcademy("kc-coach", OTHER_ACADEMY, TODAY))
                .thenReturn(false);

        assertThrows(UnauthorizedAccessException.class,
                () -> academyAccessService.requireAcademyReadAccess(token("kc-coach", "ROLE_COACH"), OTHER_ACADEMY));
    }

    @Test
    void coachNeverGetsWriteAccessEvenInOwnAcademy() {
        assertThrows(UnauthorizedAccessException.class,
                () -> academyAccessService.requireAcademyAccess(token("kc-coach", "ROLE_COACH"), OWN_ACADEMY));
        verifyNoInteractions(userRepository, coachAssignmentRepository);
    }

    @Test
    void userWithoutCoachOrAdminRoleIsRejectedBeforeAnyLookup() {
        assertThrows(UnauthorizedAccessException.class,
                () -> academyAccessService.requireAcademyReadAccess(token("kc-player", "ROLE_PLAYER"), OWN_ACADEMY));
        verifyNoInteractions(userRepository, academyAdminRepository, coachAssignmentRepository);
    }

    @Test
    void coachRoleWithoutRegisteredUserIsRejected() {
        when(userRepository.findByKeycloakId("kc-ghost")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedAccessException.class,
                () -> academyAccessService.requireAcademyReadAccess(token("kc-ghost", "ROLE_COACH"), OWN_ACADEMY));
    }

    @Test
    void academyAdminCanReadOnlyAdministeredAcademy() {
        UserEntity admin = registered("kc-admin");
        when(academyAdminRepository.existsByUser_UserIdAndAcademy_AcademyIdAndStatus(
                admin.getUserId(), OTHER_ACADEMY, Status.ACTIVE)).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class, () -> academyAccessService
                .requireAcademyReadAccess(token("kc-admin", "ROLE_ACADEMY_ADMIN"), OTHER_ACADEMY));
        verifyNoInteractions(coachAssignmentRepository);
    }

    @Test
    void adminWhoIsAlsoCoachFallsBackToCoachAssignmentForReads() {
        UserEntity user = registered("kc-both");
        when(academyAdminRepository.existsByUser_UserIdAndAcademy_AcademyIdAndStatus(
                user.getUserId(), OWN_ACADEMY, Status.ACTIVE)).thenReturn(false);
        when(coachAssignmentRepository.existsActiveAssignmentInAcademy("kc-both", OWN_ACADEMY, TODAY))
                .thenReturn(true);

        assertEquals(OWN_ACADEMY, academyAccessService.requireAcademyReadAccess(
                token("kc-both", "ROLE_ACADEMY_ADMIN", "ROLE_COACH"), OWN_ACADEMY));
    }

    @Test
    void systemAdminReadsAnyAcademyWithoutLookups() {
        assertEquals(OTHER_ACADEMY, academyAccessService
                .requireAcademyReadAccess(token("kc-root", "ROLE_SYSTEM_ADMIN"), OTHER_ACADEMY));
        verifyNoInteractions(userRepository, academyAdminRepository, coachAssignmentRepository);
    }

    @Test
    void playerResolvesOwnPlayerId() {
        UserEntity user = registered("kc-player");
        UUID playerId = UUID.randomUUID();
        when(playerRepository.findByUser_UserId(user.getUserId()))
                .thenReturn(Optional.of(PlayerEntity.builder().playerId(playerId).user(user).build()));

        assertEquals(playerId,
                academyAccessService.requireAuthenticatedPlayerId(token("kc-player", "ROLE_PLAYER")));
    }

    @Test
    void nonPlayerRoleIsRejectedBeforeAnyLookup() {
        assertThrows(UnauthorizedAccessException.class,
                () -> academyAccessService.requireAuthenticatedPlayerId(token("kc-coach", "ROLE_COACH")));
        verifyNoInteractions(userRepository, playerRepository);
    }

    @Test
    void playerRoleWithoutPlayerProfileIsRejected() {
        UserEntity user = registered("kc-player");
        when(playerRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedAccessException.class,
                () -> academyAccessService.requireAuthenticatedPlayerId(token("kc-player", "ROLE_PLAYER")));
    }

    @Test
    void lockedPlayerWithStillValidTokenIsRejected() {
        registered("kc-player").setStatus(UserStatus.LOCKED);

        assertThrows(UnauthorizedAccessException.class,
                () -> academyAccessService.requireAuthenticatedPlayerId(token("kc-player", "ROLE_PLAYER")));
        verifyNoInteractions(playerRepository);
    }
}
