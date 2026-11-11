package com.dvillacinda.canteragt.training_session.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
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

import com.dvillacinda.canteragt.academy_admin.service.AcademyAccessService;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse.CoachProfile;
import com.dvillacinda.canteragt.auth.service.CurrentUserService;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.repository.SessionStaffRepository;

@ExtendWith(MockitoExtension.class)
class TrainingSessionAccessPolicyTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID OTHER_ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID COACH_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Mock
    private AcademyAccessService academyAccessService;
    @Mock
    private AcademyCategoryRepository academyCategoryRepository;
    @Mock
    private CoachAssignmentRepository coachAssignmentRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private SessionStaffRepository sessionStaffRepository;

    private TrainingSessionAccessPolicy policy;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);
        policy = new TrainingSessionAccessPolicy(academyAccessService, academyCategoryRepository,
                coachAssignmentRepository, currentUserService, sessionStaffRepository, clock);
    }

    @Test
    void systemAdminCanManageReadAndEnterDataForExistingAcademy() {
        Authentication admin = token("kc-root", "ROLE_SYSTEM_ADMIN");
        categoryExists();

        assertDoesNotThrow(() -> policy.requireManageAccess(admin, ACADEMY_ID, CATEGORY_ID));
        assertDoesNotThrow(() -> policy.requireReadAccess(admin, ACADEMY_ID, CATEGORY_ID));
        assertDoesNotThrow(() -> policy.requireDataEntryAccess(admin, session(TrainingSessionStatus.PLANNED)));

        verify(academyAccessService).requireAcademyAccess(admin, ACADEMY_ID);
        verify(academyAccessService, times(2)).requireAcademyReadAccess(admin, ACADEMY_ID);
    }

    @Test
    void academyAdminCanManageReadAndEnterDataInAssignedAcademy() {
        Authentication admin = token("kc-admin", "ROLE_ACADEMY_ADMIN");
        categoryExists();

        assertDoesNotThrow(() -> policy.requireManageAccess(admin, ACADEMY_ID, CATEGORY_ID));
        assertDoesNotThrow(() -> policy.requireReadAccess(admin, ACADEMY_ID, CATEGORY_ID));
        assertDoesNotThrow(() -> policy.requireDataEntryAccess(admin, session(TrainingSessionStatus.PLANNED)));

        verify(academyAccessService).requireAcademyAccess(admin, ACADEMY_ID);
        verify(academyAccessService, times(2)).requireAcademyReadAccess(admin, ACADEMY_ID);
    }

    @Test
    void academyAdminOutsideAcademyReceivesNotFound() {
        Authentication admin = token("kc-admin", "ROLE_ACADEMY_ADMIN");
        when(academyAccessService.requireAcademyAccess(admin, OTHER_ACADEMY_ID))
                .thenThrow(new UnauthorizedAccessException("outside academy"));
        when(academyAccessService.requireAcademyReadAccess(admin, OTHER_ACADEMY_ID))
                .thenThrow(new UnauthorizedAccessException("outside academy"));

        assertThrows(NotFoundException.class,
                () -> policy.requireManageAccess(admin, OTHER_ACADEMY_ID, CATEGORY_ID));
        assertThrows(NotFoundException.class,
                () -> policy.requireReadAccess(admin, OTHER_ACADEMY_ID, CATEGORY_ID));
        assertThrows(NotFoundException.class,
                () -> policy.requireDataEntryAccess(admin, session(OTHER_ACADEMY_ID, CATEGORY_ID,
                        TrainingSessionStatus.PLANNED)));
    }

    @Test
    void headCoachCanManageReadAndEnterDataWithCurrentCategoryAssignment() {
        Authentication coach = coachWithCurrentAssignment(true);
        categoryExists();

        assertDoesNotThrow(() -> policy.requireManageAccess(coach, ACADEMY_ID, CATEGORY_ID));
        assertDoesNotThrow(() -> policy.requireReadAccess(coach, ACADEMY_ID, CATEGORY_ID));
        assertDoesNotThrow(() -> policy.requireDataEntryAccess(coach, session(TrainingSessionStatus.PLANNED)));

        verify(coachAssignmentRepository, times(2)).existsCurrentAssignmentForCategoryAndRole(
                COACH_ID, CATEGORY_ID, CoachRoleCode.HEAD_COACH, TODAY);
    }

    @Test
    void coachWithOtherCurrentRoleCanReadButCannotManage() {
        Authentication coach = coachWithCurrentAssignment(false);
        categoryExists();
        when(coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                COACH_ID, CATEGORY_ID, CoachRoleCode.HEAD_COACH, TODAY)).thenReturn(false);

        assertDoesNotThrow(() -> policy.requireReadAccess(coach, ACADEMY_ID, CATEGORY_ID));
        assertThrows(UnauthorizedAccessException.class,
                () -> policy.requireManageAccess(coach, ACADEMY_ID, CATEGORY_ID));
    }

    @Test
    void otherCurrentCoachCanEnterDataOnlyWhenStaffOnInProgressSession() {
        Authentication coach = coachWithCurrentAssignment(false);
        TrainingSessionEntity inProgress = session(TrainingSessionStatus.IN_PROGRESS);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                COACH_ID, CATEGORY_ID, CoachRoleCode.HEAD_COACH, TODAY)).thenReturn(false);
        when(sessionStaffRepository
                .existsByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryIdAndCoach_CoachId(
                        SESSION_ID, CATEGORY_ID, COACH_ID)).thenReturn(true);

        assertDoesNotThrow(() -> policy.requireDataEntryAccess(coach, inProgress));
    }

    @Test
    void otherCurrentCoachCannotEnterDataWhenNotSessionStaffOrSessionNotInProgress() {
        Authentication coach = coachWithCurrentAssignment(false);
        TrainingSessionEntity inProgress = session(TrainingSessionStatus.IN_PROGRESS);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                COACH_ID, CATEGORY_ID, CoachRoleCode.HEAD_COACH, TODAY)).thenReturn(false);
        when(sessionStaffRepository
                .existsByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryIdAndCoach_CoachId(
                        SESSION_ID, CATEGORY_ID, COACH_ID)).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class,
                () -> policy.requireDataEntryAccess(coach, inProgress));
        assertThrows(UnauthorizedAccessException.class,
                () -> policy.requireDataEntryAccess(coach, session(TrainingSessionStatus.PLANNED)));
    }

    @Test
    void coachWithoutCurrentCategoryAssignmentReceivesNotFound() {
        coachWithoutCurrentAssignment();
        Authentication coach = token("kc-coach", "ROLE_COACH");

        assertThrows(NotFoundException.class,
                () -> policy.requireReadAccess(coach, ACADEMY_ID, CATEGORY_ID));
        assertThrows(NotFoundException.class,
                () -> policy.requireManageAccess(coach, ACADEMY_ID, CATEGORY_ID));
        assertThrows(NotFoundException.class,
                () -> policy.requireDataEntryAccess(coach, session(TrainingSessionStatus.IN_PROGRESS)));

        verify(coachAssignmentRepository, times(3))
                .existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY);
    }

    @Test
    void futureCoachAssignmentReceivesNotFound() {
        coachWithoutCurrentAssignment();

        assertThrows(NotFoundException.class,
                () -> policy.requireReadAccess(token("kc-coach", "ROLE_COACH"), ACADEMY_ID, CATEGORY_ID));
        verify(coachAssignmentRepository).existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY);
    }

    @Test
    void expiredCoachAssignmentReceivesNotFound() {
        coachWithoutCurrentAssignment();

        assertThrows(NotFoundException.class,
                () -> policy.requireReadAccess(token("kc-coach", "ROLE_COACH"), ACADEMY_ID, CATEGORY_ID));
        verify(coachAssignmentRepository).existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY);
    }

    @Test
    void playerIsForbiddenForAllStaffOperations() {
        Authentication player = token("kc-player", "ROLE_PLAYER");

        assertThrows(UnauthorizedAccessException.class,
                () -> policy.requireManageAccess(player, ACADEMY_ID, CATEGORY_ID));
        assertThrows(UnauthorizedAccessException.class,
                () -> policy.requireReadAccess(player, ACADEMY_ID, CATEGORY_ID));
        assertThrows(UnauthorizedAccessException.class,
                () -> policy.requireDataEntryAccess(player, session(TrainingSessionStatus.IN_PROGRESS)));
    }

    @Test
    void mismatchedCategoryAndAcademyReceivesNotFound() {
        Authentication admin = token("kc-admin", "ROLE_ACADEMY_ADMIN");
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> policy.requireReadAccess(admin, ACADEMY_ID, CATEGORY_ID));
    }

    private Authentication coachWithCurrentAssignment(boolean headCoach) {
        Authentication coach = token("kc-coach", "ROLE_COACH");
        when(currentUserService.getCurrentUser(any())).thenReturn(new CurrentUserResponse(
                List.of("COACH"), List.of(), null, null, new CoachProfile(COACH_ID)));
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(true);
        if (headCoach) {
            when(coachAssignmentRepository.existsCurrentAssignmentForCategoryAndRole(
                    COACH_ID, CATEGORY_ID, CoachRoleCode.HEAD_COACH, TODAY)).thenReturn(true);
        }
        return coach;
    }

    private void coachWithoutCurrentAssignment() {
        categoryExists();
        when(currentUserService.getCurrentUser(any())).thenReturn(new CurrentUserResponse(
                List.of("COACH"), List.of(), null, null, new CoachProfile(COACH_ID)));
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(false);
    }

    private void categoryExists() {
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(new AcademyCategoryEntity()));
    }

    private static TrainingSessionEntity session(TrainingSessionStatus status) {
        return session(ACADEMY_ID, CATEGORY_ID, status);
    }

    private static TrainingSessionEntity session(UUID academyId, UUID categoryId, TrainingSessionStatus status) {
        var academy = com.dvillacinda.canteragt.academy.entity.AcademyEntity.builder()
                .academyId(academyId).build();
        var category = AcademyCategoryEntity.builder()
                .academyCategoryId(categoryId).academy(academy).build();
        return TrainingSessionEntity.builder()
                .sessionId(SESSION_ID)
                .academyCategory(category)
                .status(status)
                .build();
    }

    private static Authentication token(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        return new JwtAuthenticationToken(jwt,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }
}
