package com.dvillacinda.canteragt.training_session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.academy_category.enums.AcademyCategoryStatus;
import com.dvillacinda.canteragt.academy_category.repository.AcademyCategoryRepository;
import com.dvillacinda.canteragt.academy_location.repository.AcademyLocationRepository;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse;
import com.dvillacinda.canteragt.auth.service.CurrentUserService;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionCreateRequest;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionFilter;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionUpdateRequest;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.TrainingSessionMapper;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionStaffRepository;
import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;
import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;
import com.dvillacinda.canteragt.training_session.repository.SessionEvaluationEventRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;

@ExtendWith(MockitoExtension.class)
class TrainingSessionServiceTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID COACH_ID = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T16:00:00Z"), ZONE);

    @Mock
    private TrainingSessionRepository trainingSessionRepository;
    @Mock
    private AcademyCategoryRepository academyCategoryRepository;
    @Mock
    private AcademyLocationRepository academyLocationRepository;
    @Mock
    private SessionPlayerRepository sessionPlayerRepository;
    @Mock
    private SessionStaffRepository sessionStaffRepository;
    @Mock
    private SessionEvaluationEventRepository eventRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private TrainingSessionAccessPolicy accessPolicy;
    @Mock
    private Authentication authentication;

    private TrainingSessionService service;
    private final TrainingSessionMapper mapper = new TrainingSessionMapper("America/Guatemala");

    @BeforeEach
    void setUp() {
        service = new TrainingSessionService(
                trainingSessionRepository,
                academyCategoryRepository,
                academyLocationRepository,
                sessionPlayerRepository,
                sessionStaffRepository,
                eventRepository,
                currentUserService,
                accessPolicy,
                mapper,
                CLOCK);
        ReflectionTestUtils.setField(service, "maxBackdateDays", 7);
        ReflectionTestUtils.setField(service, "maxFutureDays", 365);
    }

    @Test
    void createUsesAuthenticatedCoachAndInitializesPlannedState() {
        AcademyCategoryEntity category = category(AcademyCategoryStatus.ACTIVE);
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(category));
        when(authentication.getPrincipal()).thenReturn(jwt());
        when(currentUserService.getCurrentUser(any())).thenReturn(currentUserWithCoach());
        when(trainingSessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new TrainingSessionCreateRequest(
                "  Technical session  ",
                OffsetDateTime.parse("2026-11-17T10:00:00-06:00"),
                null,
                "Focus on passing");

        var response = service.create(CATEGORY_ID, ACADEMY_ID, request, authentication);

        ArgumentCaptor<TrainingSessionEntity> sessionCaptor = ArgumentCaptor.forClass(TrainingSessionEntity.class);
        verify(trainingSessionRepository).saveAndFlush(sessionCaptor.capture());
        TrainingSessionEntity saved = sessionCaptor.getValue();
        assertEquals(COACH_ID, saved.getCreatedByCoach().getCoachId());
        assertEquals(TrainingSessionStatus.PLANNED, saved.getStatus());
        assertEquals("Technical session", saved.getName());
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 0), saved.getSessionDate());
        assertEquals(CATEGORY_ID, response.academyCategoryId());

        ArgumentCaptor<SessionStaffEntity> staffCaptor = ArgumentCaptor.forClass(SessionStaffEntity.class);
        verify(sessionStaffRepository).saveAndFlush(staffCaptor.capture());
        assertEquals(SessionParticipationType.LEAD, staffCaptor.getValue().getParticipationType());
        assertEquals(COACH_ID, staffCaptor.getValue().getCoach().getCoachId());
        assertEquals(saved, staffCaptor.getValue().getTrainingSession());
    }

    @Test
    void createRejectsInactiveCategoryAndDoesNotPersist() {
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(category(AcademyCategoryStatus.CLOSED)));

        assertThrows(ConflictException.class, () -> service.create(
                CATEGORY_ID, ACADEMY_ID,
                new TrainingSessionCreateRequest("Practice", OffsetDateTime.now(CLOCK), null, null),
                authentication));

        verify(trainingSessionRepository, never()).saveAndFlush(any());
        verify(currentUserService, never()).getCurrentUser(any());
    }

    @Test
    void createRejectsActorWithoutCoachProfile() {
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(category(AcademyCategoryStatus.ACTIVE)));
        when(authentication.getPrincipal()).thenReturn(jwt());
        when(currentUserService.getCurrentUser(any())).thenReturn(new CurrentUserResponse(
                List.of("ACADEMY_ADMIN"), List.of(), null, null, null));

        assertThrows(UnauthorizedAccessException.class, () -> service.create(
                CATEGORY_ID, ACADEMY_ID,
                new TrainingSessionCreateRequest("Practice", OffsetDateTime.now(CLOCK), null, null),
                authentication));
        verify(trainingSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsLocationOutsideAcademyOrInactive() {
        UUID locationId = UUID.randomUUID();
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(category(AcademyCategoryStatus.ACTIVE)));
        when(authentication.getPrincipal()).thenReturn(jwt());
        when(currentUserService.getCurrentUser(any())).thenReturn(currentUserWithCoach());
        when(academyLocationRepository.findByLocationIdAndAcademy_AcademyIdAndStatus(
                locationId, ACADEMY_ID, com.dvillacinda.canteragt.shared.enums.Status.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.create(
                CATEGORY_ID, ACADEMY_ID,
                new TrainingSessionCreateRequest(
                        "Practice", OffsetDateTime.parse("2026-11-17T10:00:00-06:00"), locationId, null),
                authentication));

        verify(trainingSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsDatesOutsideConfiguredWindow() {
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(category(AcademyCategoryStatus.ACTIVE)));

        assertThrows(BadRequestException.class, () -> service.create(
                CATEGORY_ID, ACADEMY_ID,
                new TrainingSessionCreateRequest("Practice", OffsetDateTime.parse("2026-11-09T10:00:00-06:00"),
                        null, null),
                authentication));
        verify(trainingSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsDatesMoreThanConfiguredDaysInTheFuture() {
        when(academyCategoryRepository.findByAcademyCategoryIdAndAcademy_AcademyId(CATEGORY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(category(AcademyCategoryStatus.ACTIVE)));

        assertThrows(BadRequestException.class, () -> service.create(
                CATEGORY_ID, ACADEMY_ID,
                new TrainingSessionCreateRequest("Practice", OffsetDateTime.parse("2027-11-18T10:00:00-06:00"),
                        null, null),
                authentication));
        verify(trainingSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void listQueriesOnlyRequestedCategoryAndPassesFiltersToDatabase() {
        // The client sorts by the JSON name; the repository receives the entity property
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "session_date"));
        PageRequest repositoryPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "sessionDate"));
        when(trainingSessionRepository.searchByCategory(
                eq(CATEGORY_ID), eq(TrainingSessionStatus.PLANNED),
                eq(LocalDateTime.of(2026, 11, 17, 8, 0)),
                eq(LocalDateTime.of(2026, 11, 17, 18, 0)), eq(repositoryPageable)))
                .thenReturn(Page.empty(repositoryPageable));

        Page<?> result = service.list(
                CATEGORY_ID,
                ACADEMY_ID,
                new TrainingSessionFilter(
                        TrainingSessionStatus.PLANNED,
                        OffsetDateTime.parse("2026-11-17T08:00:00-06:00"),
                        OffsetDateTime.parse("2026-11-17T18:00:00-06:00")),
                pageable,
                authentication);

        assertEquals(0, result.getTotalElements());
        verify(accessPolicy).requireReadAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        verify(trainingSessionRepository).searchByCategory(
                CATEGORY_ID, TrainingSessionStatus.PLANNED,
                LocalDateTime.of(2026, 11, 17, 8, 0),
                LocalDateTime.of(2026, 11, 17, 18, 0), repositoryPageable);
    }

    @Test
    void listRejectsOversizedPageAndInvertedDateRange() {
        assertThrows(BadRequestException.class, () -> service.list(
                CATEGORY_ID, ACADEMY_ID, new TrainingSessionFilter(null, null, null),
                PageRequest.of(0, 51), authentication));
        assertThrows(BadRequestException.class, () -> service.list(
                CATEGORY_ID, ACADEMY_ID,
                new TrainingSessionFilter(
                        null,
                        OffsetDateTime.parse("2026-11-18T10:00:00-06:00"),
                        OffsetDateTime.parse("2026-11-17T10:00:00-06:00")),
                PageRequest.of(0, 20), authentication));
        verify(trainingSessionRepository, never()).searchByCategory(any(), any(), any(), any(), any());
    }

    @Test
    void listRejectsUnsupportedSortProperties() {
        assertThrows(BadRequestException.class, () -> service.list(
                CATEGORY_ID, ACADEMY_ID,
                new TrainingSessionFilter(null, null, null),
                PageRequest.of(0, 20, Sort.by("createdByCoach.user.email")),
                authentication));
        verifyNoInteractions(trainingSessionRepository);
    }

    @Test
    void updateIsLimitedToPlannedAndPersistsChanges() {
        TrainingSessionEntity session = session(TrainingSessionStatus.PLANNED);
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        when(trainingSessionRepository.saveAndFlush(session)).thenReturn(session);

        var response = service.update(
                SESSION_ID, ACADEMY_ID,
                new TrainingSessionUpdateRequest("Updated practice", null, null, "Updated notes"),
                authentication);

        assertEquals("Updated practice", response.name());
        assertEquals("Updated notes", response.notes());
        verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
    }

    @Test
    void updateRejectsCompletedSessionAndEmptyPatch() {
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session(TrainingSessionStatus.COMPLETED)));
        assertThrows(ConflictException.class, () -> service.update(
                SESSION_ID, ACADEMY_ID, new TrainingSessionUpdateRequest("Name", null, null, null), authentication));

        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session(TrainingSessionStatus.PLANNED)));
        assertThrows(BadRequestException.class, () -> service.update(
                SESSION_ID, ACADEMY_ID, new TrainingSessionUpdateRequest(null, null, null, null), authentication));
    }

    @Test
    void startSetsTimestampAndRejectsInvalidTransition() {
        TrainingSessionEntity session = session(TrainingSessionStatus.PLANNED);
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        when(trainingSessionRepository.saveAndFlush(session)).thenReturn(session);

        var response = service.start(SESSION_ID, ACADEMY_ID, authentication);

        assertEquals(TrainingSessionStatus.IN_PROGRESS, response.status());
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 0), session.getStartedAt());

        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        assertThrows(ConflictException.class, () -> service.start(SESSION_ID, ACADEMY_ID, authentication));
    }

    @Test
    void completeRequiresNoPendingPlayersAndSetsEndTimestamp() {
        TrainingSessionEntity session = session(TrainingSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.of(2026, 11, 17, 9, 0));
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        when(sessionPlayerRepository.findPlayerIdsBySessionAndAttendanceStatus(
                SESSION_ID, CATEGORY_ID, AttendanceStatus.PENDING))
                .thenReturn(List.of(UUID.randomUUID()));

        assertThrows(ConflictException.class, () -> service.complete(SESSION_ID, ACADEMY_ID, authentication));
        verify(trainingSessionRepository, never()).saveAndFlush(any());

        when(sessionPlayerRepository.findPlayerIdsBySessionAndAttendanceStatus(
                SESSION_ID, CATEGORY_ID, AttendanceStatus.PENDING))
                .thenReturn(List.of());
        when(trainingSessionRepository.saveAndFlush(session)).thenReturn(session);
        var response = service.complete(SESSION_ID, ACADEMY_ID, authentication);

        assertEquals(TrainingSessionStatus.COMPLETED, response.status());
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 0), session.getEndedAt());
    }

    @Test
    void cancelingInProgressSessionSetsEndTimestampAndCompletedCannotBeCancelled() {
        TrainingSessionEntity session = session(TrainingSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.of(2026, 11, 17, 9, 0));
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        when(trainingSessionRepository.saveAndFlush(session)).thenReturn(session);

        var response = service.cancel(SESSION_ID, ACADEMY_ID, authentication);

        assertEquals(TrainingSessionStatus.CANCELLED, response.status());
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 0), session.getEndedAt());
        assertThrows(ConflictException.class, () -> service.cancel(SESSION_ID, ACADEMY_ID, authentication));
    }

    /** E9 (ADR-0004, DP-2.2): a session with an event IN_PROGRESS can be neither completed nor cancelled. */
    @Test
    void sessionWithAnEventInProgressCannotBeCompletedOrCancelled() {
        TrainingSessionEntity session = session(TrainingSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.of(2026, 11, 17, 9, 0));
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        when(sessionPlayerRepository.findPlayerIdsBySessionAndAttendanceStatus(
                SESSION_ID, CATEGORY_ID, AttendanceStatus.PENDING)).thenReturn(List.of());
        when(eventRepository.existsByTrainingSession_SessionIdAndStatus(
                SESSION_ID, SessionEvaluationEventStatus.IN_PROGRESS)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.complete(SESSION_ID, ACADEMY_ID, authentication));
        assertThrows(ConflictException.class, () -> service.cancel(SESSION_ID, ACADEMY_ID, authentication));
        assertEquals(TrainingSessionStatus.IN_PROGRESS, session.getStatus());
        verify(trainingSessionRepository, never()).saveAndFlush(any());
    }

    /** T4.3-D5: cancelling a session cancels its PLANNED events (they have no evidence). */
    @Test
    void cancellingASessionCancelsItsPlannedEvents() {
        TrainingSessionEntity session = session(TrainingSessionStatus.PLANNED);
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        when(trainingSessionRepository.saveAndFlush(session)).thenReturn(session);
        SessionEvaluationEventEntity planned = SessionEvaluationEventEntity.builder()
                .status(SessionEvaluationEventStatus.PLANNED).build();
        when(eventRepository.findAllByTrainingSession_SessionIdAndStatus(
                SESSION_ID, SessionEvaluationEventStatus.PLANNED)).thenReturn(List.of(planned));

        service.cancel(SESSION_ID, ACADEMY_ID, authentication);

        assertEquals(SessionEvaluationEventStatus.CANCELLED, planned.getStatus());
        assertEquals(TrainingSessionStatus.CANCELLED, session.getStatus());
    }

    /**
     * Full lifecycle table (DP-1.2): origin status x action -> resulting status or 409 CONFLICT.
     * Any transition not listed as allowed must leave the session untouched and never reach the database.
     */
    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @CsvSource({
            "PLANNED,     START,    IN_PROGRESS",
            "PLANNED,     COMPLETE, CONFLICT",
            "PLANNED,     CANCEL,   CANCELLED",
            "PLANNED,     UPDATE,   PLANNED",
            "IN_PROGRESS, START,    CONFLICT",
            "IN_PROGRESS, COMPLETE, COMPLETED",
            "IN_PROGRESS, CANCEL,   CANCELLED",
            "IN_PROGRESS, UPDATE,   CONFLICT",
            "COMPLETED,   START,    CONFLICT",
            "COMPLETED,   COMPLETE, CONFLICT",
            "COMPLETED,   CANCEL,   CONFLICT",
            "COMPLETED,   UPDATE,   CONFLICT",
            "CANCELLED,   START,    CONFLICT",
            "CANCELLED,   COMPLETE, CONFLICT",
            "CANCELLED,   CANCEL,   CONFLICT",
            "CANCELLED,   UPDATE,   CONFLICT"
    })
    void lifecycleTransitionTable(TrainingSessionStatus origin, String action, String expected) {
        TrainingSessionEntity session = session(origin);
        if (origin != TrainingSessionStatus.PLANNED) {
            session.setStartedAt(LocalDateTime.of(2026, 11, 17, 9, 0));
        }
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        lenient().when(trainingSessionRepository.saveAndFlush(session)).thenReturn(session);
        lenient().when(sessionPlayerRepository.findPlayerIdsBySessionAndAttendanceStatus(
                SESSION_ID, CATEGORY_ID, AttendanceStatus.PENDING)).thenReturn(List.of());

        org.junit.jupiter.api.function.ThrowingSupplier<?> call = switch (action) {
            case "START" -> () -> service.start(SESSION_ID, ACADEMY_ID, authentication);
            case "COMPLETE" -> () -> service.complete(SESSION_ID, ACADEMY_ID, authentication);
            case "CANCEL" -> () -> service.cancel(SESSION_ID, ACADEMY_ID, authentication);
            case "UPDATE" -> () -> service.update(SESSION_ID, ACADEMY_ID,
                    new TrainingSessionUpdateRequest("Renamed", null, null, null), authentication);
            default -> throw new IllegalArgumentException(action);
        };

        if ("CONFLICT".equals(expected)) {
            assertThrows(ConflictException.class, call::get);
            assertEquals(origin, session.getStatus());
            verify(trainingSessionRepository, never()).saveAndFlush(any());
        } else {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(call);
            assertEquals(TrainingSessionStatus.valueOf(expected), session.getStatus());
            verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        }
    }

    @Test
    void versionConflictsBecomeHttpConflictErrors() {
        TrainingSessionEntity session = session(TrainingSessionStatus.PLANNED);
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.of(session));
        when(trainingSessionRepository.saveAndFlush(session))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        assertThrows(ConflictException.class, () -> service.start(SESSION_ID, ACADEMY_ID, authentication));
    }

    @Test
    void crossAcademySessionIsNotFoundBeforeAuthorization() {
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(SESSION_ID, ACADEMY_ID, authentication));
        verify(accessPolicy, never()).requireReadAccess(any(), any(), any());
    }

    private AcademyCategoryEntity category(AcademyCategoryStatus status) {
        AcademyEntity academy = new AcademyEntity();
        academy.setAcademyId(ACADEMY_ID);
        AcademyCategoryEntity category = new AcademyCategoryEntity();
        category.setAcademyCategoryId(CATEGORY_ID);
        category.setAcademy(academy);
        category.setStatus(status);
        return category;
    }

    private TrainingSessionEntity session(TrainingSessionStatus status) {
        return TrainingSessionEntity.builder()
                .sessionId(SESSION_ID)
                .academyCategory(category(AcademyCategoryStatus.ACTIVE))
                .createdByCoach(CoachEntity.builder().coachId(COACH_ID).build())
                .name("Practice")
                .sessionDate(LocalDateTime.of(2026, 11, 17, 10, 0))
                .status(status)
                .version(0L)
                .build();
    }

    private CurrentUserResponse currentUserWithCoach() {
        return new CurrentUserResponse(
                List.of("COACH"), List.of(), null, null, new CurrentUserResponse.CoachProfile(COACH_ID));
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("keycloak-user")
                .claim("email", "coach@example.test")
                .claim("preferred_username", "coach")
                .build();
    }
}
