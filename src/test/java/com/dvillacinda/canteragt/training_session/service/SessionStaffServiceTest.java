package com.dvillacinda.canteragt.training_session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.coach_assignment.repository.CoachAssignmentRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffAddRequest;
import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.SessionStaffMapper;
import com.dvillacinda.canteragt.training_session.repository.SessionStaffRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class SessionStaffServiceTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID COACH_ID = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    /** 00:30 UTC on the 18th is still the 17th in Guatemala. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T00:30:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private SessionStaffRepository sessionStaffRepository;
    @Mock private CoachAssignmentRepository coachAssignmentRepository;
    @Mock private TrainingSessionAccessPolicy accessPolicy;
    @Mock private EntityManager entityManager;
    @Mock private TrainingSessionLock sessionLock;
    @Mock private Authentication authentication;

    private SessionStaffService service;

    @BeforeEach
    void setUp() {
        service = new SessionStaffService(trainingSessionRepository, sessionStaffRepository,
                coachAssignmentRepository, accessPolicy, new SessionStaffMapper("America/Guatemala"),
                entityManager, sessionLock, CLOCK);
        lenient().when(sessionLock.acquire(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(entityManager.getReference(eq(CoachEntity.class), any()))
                .thenAnswer(invocation -> CoachEntity.builder().coachId(invocation.getArgument(1)).build());
    }

    // ---- Rule 1: current assignment in the session's category (Clock date) ----------------------------------

    @ParameterizedTest
    @EnumSource(value = SessionParticipationType.class)
    void coachWithCurrentAssignmentIsAddedWithRequestedRole(SessionParticipationType type) {
        TrainingSessionEntity session = givenSession(TrainingSessionStatus.PLANNED);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(true);
        lenient().when(sessionStaffRepository.existsByTrainingSession_SessionIdAndParticipationType(
                SESSION_ID, SessionParticipationType.LEAD)).thenReturn(false);
        when(sessionStaffRepository.findStaffEntry(SESSION_ID, COACH_ID)).thenReturn(Optional.of(entry(type)));

        var response = service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, type), authentication);

        ArgumentCaptor<SessionStaffEntity> saved = ArgumentCaptor.forClass(SessionStaffEntity.class);
        verify(sessionStaffRepository).saveAndFlush(saved.capture());
        assertEquals(type, saved.getValue().getParticipationType());
        assertEquals(session, saved.getValue().getTrainingSession());
        assertEquals(COACH_ID, saved.getValue().getCoach().getCoachId());
        assertEquals(type, response.participationType());
        verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
    }

    @Test
    void coachWithoutCurrentAssignmentIsRejectedWith400() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, SessionParticipationType.ASSISTANT), authentication));
        verify(sessionStaffRepository, never()).saveAndFlush(any());
    }

    // ---- Rule 2: single LEAD (T3.6-A) -----------------------------------------------------------------------

    @Test
    void secondLeadIs409() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(true);
        when(sessionStaffRepository.existsByTrainingSession_SessionIdAndParticipationType(
                SESSION_ID, SessionParticipationType.LEAD)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, SessionParticipationType.LEAD), authentication));
        verify(sessionStaffRepository, never()).saveAndFlush(any());
    }

    @Test
    void sessionRowIsLockedBeforeTheLeadCheck() {
        TrainingSessionEntity session = givenSession(TrainingSessionStatus.PLANNED);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(true);
        when(sessionStaffRepository.findStaffEntry(SESSION_ID, COACH_ID))
                .thenReturn(Optional.of(entry(SessionParticipationType.LEAD)));

        service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, SessionParticipationType.LEAD), authentication);

        InOrder order = inOrder(accessPolicy, sessionLock, sessionStaffRepository);
        order.verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        order.verify(sessionLock).acquire(session);
        order.verify(sessionStaffRepository).existsByTrainingSession_SessionIdAndParticipationType(
                SESSION_ID, SessionParticipationType.LEAD);
    }

    // ---- Rule 3: duplicate (session, coach) -> 409, never 500 ------------------------------------------------

    @Test
    void coachAlreadyInStaffIs409() {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(true);
        when(sessionStaffRepository.existsByTrainingSession_SessionIdAndCoach_CoachId(SESSION_ID, COACH_ID))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, SessionParticipationType.SUPPORT), authentication));
        verify(sessionStaffRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueConstraintRaceBecomesConflictNot500() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(coachAssignmentRepository.existsCurrentAssignmentForCategory(COACH_ID, CATEGORY_ID, TODAY))
                .thenReturn(true);
        when(sessionStaffRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_session_staff"));

        assertThrows(ConflictException.class, () -> service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, SessionParticipationType.ASSISTANT), authentication));
    }

    // ---- Rule 4: only PLANNED / IN_PROGRESS -----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = TrainingSessionStatus.class, names = { "COMPLETED", "CANCELLED" })
    void closedSessionsRejectStaffChanges(TrainingSessionStatus status) {
        givenSession(status);

        assertThrows(ConflictException.class, () -> service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, SessionParticipationType.ASSISTANT), authentication));
        assertThrows(ConflictException.class,
                () -> service.removeStaff(SESSION_ID, ACADEMY_ID, COACH_ID, authentication));
        verify(coachAssignmentRepository, never()).existsCurrentAssignmentForCategory(any(), any(), any());
        verify(sessionStaffRepository, never()).saveAndFlush(any());
        verify(sessionStaffRepository, never()).delete(any());
    }

    // ---- Rule 6: LEAD cannot be removed while IN_PROGRESS (T3.6-C) ------------------------------------------

    @Test
    void leadCannotBeRemovedWhileInProgress() {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        when(sessionStaffRepository.findStaffEntry(SESSION_ID, COACH_ID))
                .thenReturn(Optional.of(entry(SessionParticipationType.LEAD)));

        assertThrows(ConflictException.class,
                () -> service.removeStaff(SESSION_ID, ACADEMY_ID, COACH_ID, authentication));
        verify(sessionStaffRepository, never()).delete(any());
    }

    @Test
    void leadCanBeRemovedWhilePlanned() {
        givenSession(TrainingSessionStatus.PLANNED);
        SessionStaffEntity lead = entry(SessionParticipationType.LEAD);
        when(sessionStaffRepository.findStaffEntry(SESSION_ID, COACH_ID)).thenReturn(Optional.of(lead));

        service.removeStaff(SESSION_ID, ACADEMY_ID, COACH_ID, authentication);

        verify(sessionStaffRepository).delete(lead);
        verify(sessionLock).flush();
    }

    @ParameterizedTest
    @EnumSource(value = SessionParticipationType.class, names = { "ASSISTANT", "SUPPORT" })
    void nonLeadCanBeRemovedWhileInProgress(SessionParticipationType type) {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        SessionStaffEntity member = entry(type);
        when(sessionStaffRepository.findStaffEntry(SESSION_ID, COACH_ID)).thenReturn(Optional.of(member));

        service.removeStaff(SESSION_ID, ACADEMY_ID, COACH_ID, authentication);

        verify(sessionStaffRepository).delete(member);
    }

    @Test
    void removingCoachNotInStaffIs404() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionStaffRepository.findStaffEntry(SESSION_ID, COACH_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.removeStaff(SESSION_ID, ACADEMY_ID, COACH_ID, authentication));
    }

    // ---- Scope and read -------------------------------------------------------------------------------------

    @Test
    void sessionOutsideAcademyIs404BeforeAuthorization() {
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.list(SESSION_ID, ACADEMY_ID, authentication));
        assertThrows(NotFoundException.class, () -> service.addStaff(SESSION_ID, ACADEMY_ID,
                new SessionStaffAddRequest(COACH_ID, SessionParticipationType.ASSISTANT), authentication));
        verify(accessPolicy, never()).requireReadAccess(any(), any(), any());
        verify(accessPolicy, never()).requireManageAccess(any(), any(), any());
    }

    @Test
    void staffReadUsesReadAccessAndExposesNameAndRoleOnly() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(sessionStaffRepository.findStaff(SESSION_ID)).thenReturn(List.of(entry(SessionParticipationType.LEAD)));

        var staff = service.list(SESSION_ID, ACADEMY_ID, authentication);

        verify(accessPolicy).requireReadAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        verify(accessPolicy, never()).requireManageAccess(any(), any(), any());
        assertEquals(1, staff.size());
        assertEquals("Luis", staff.getFirst().firstName());
        assertEquals(SessionParticipationType.LEAD, staff.getFirst().participationType());
    }

    // ---- fixtures -------------------------------------------------------------------------------------------

    private TrainingSessionEntity givenSession(TrainingSessionStatus status) {
        AcademyEntity academy = new AcademyEntity();
        academy.setAcademyId(ACADEMY_ID);
        AcademyCategoryEntity category = new AcademyCategoryEntity();
        category.setAcademyCategoryId(CATEGORY_ID);
        category.setAcademy(academy);
        category.setStatus(AcademyCategoryStatus.ACTIVE);
        TrainingSessionEntity session = TrainingSessionEntity.builder()
                .sessionId(SESSION_ID).academyCategory(category).status(status).version(0L).build();
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        return session;
    }

    private SessionStaffEntity entry(SessionParticipationType type) {
        UserEntity user = UserEntity.builder().firstName("Luis").lastName("Pérez")
                .email("hidden@example.test").keycloakId("hidden-kc").build();
        return SessionStaffEntity.builder()
                .sessionStaffId(UUID.randomUUID())
                .coach(CoachEntity.builder().coachId(COACH_ID).user(user).build())
                .participationType(type)
                .build();
    }
}
