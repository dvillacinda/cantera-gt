package com.dvillacinda.canteragt.training_session.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.academy.entity.AcademyEntity;
import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationCriterionRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationTemplateRepository;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventCreateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionEventMetricsUpdateRequest;
import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricId;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.mapper.SessionEvaluationEventMapper;
import com.dvillacinda.canteragt.training_session.mapper.SessionPlayerMapper;
import com.dvillacinda.canteragt.training_session.repository.SessionEvaluationEventRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionEventMetricRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.repository.TrainingSessionRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class SessionEvaluationEventServiceTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T16:00:00Z"), ZoneId.of("America/Guatemala"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 10, 0);

    @Mock private TrainingSessionRepository trainingSessionRepository;
    @Mock private SessionEvaluationEventRepository eventRepository;
    @Mock private SessionEventMetricRepository metricRepository;
    @Mock private EvaluationTemplateRepository templateRepository;
    @Mock private EvaluationCriterionRepository criterionRepository;
    @Mock private TrainingSessionAccessPolicy accessPolicy;
    @Mock private TrainingSessionLock sessionLock;
    @Mock private SessionPlayerRepository sessionPlayerRepository;
    @Mock private EntityManager entityManager;
    @Mock private Authentication authentication;

    private SessionEvaluationEventService service;
    private EvaluationTemplateEntity template;
    /** Template catalogue: 4 scored metrics (3 active by default) and one non-scored component. */
    private EvaluationCriterionEntity first;
    private EvaluationCriterionEntity second;
    private EvaluationCriterionEntity third;
    private EvaluationCriterionEntity optional;
    private EvaluationCriterionEntity component;

    @BeforeEach
    void setUp() {
        service = new SessionEvaluationEventService(trainingSessionRepository, eventRepository, metricRepository,
                templateRepository, criterionRepository, accessPolicy, sessionLock,
                new SessionEvaluationEventMapper("America/Guatemala"), sessionPlayerRepository,
                new SessionPlayerMapper("America/Guatemala"), entityManager, CLOCK);
        template = template(true, EvaluationTemplateStatus.ACTIVE);
        first = criterion("A_FIRST", 1, true, true);
        second = criterion("B_SECOND", 2, true, true);
        third = criterion("C_THIRD", 3, true, true);
        optional = criterion("D_OPTIONAL", 4, false, true);
        component = criterion("E_COMPONENT", 5, false, false);
        lenient().when(criterionRepository.findAllForTemplate(TEMPLATE_ID))
                .thenReturn(List.of(first, second, third, optional, component));
        lenient().when(sessionLock.acquire(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(eventRepository.save(any())).thenAnswer(invocation -> {
            SessionEvaluationEventEntity event = invocation.getArgument(0);
            event.setSessionEventId(EVENT_ID);
            return event;
        });
    }

    // ---- create -------------------------------------------------------------------------------------------------

    @Test
    void createAssignsNextBlockOrderAndLoadsDefaultMetricsWhenNoneAreSent() {
        TrainingSessionEntity session = givenSession(TrainingSessionStatus.PLANNED);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(eventRepository.findMaxBlockOrder(SESSION_ID)).thenReturn(2);

        var response = service.create(SESSION_ID, ACADEMY_ID,
                new SessionEvaluationEventCreateRequest(TEMPLATE_ID, null), authentication);

        ArgumentCaptor<SessionEvaluationEventEntity> saved = ArgumentCaptor.forClass(SessionEvaluationEventEntity.class);
        verify(eventRepository).save(saved.capture());
        assertEquals((short) 3, saved.getValue().getBlockOrder());
        assertEquals(SessionEvaluationEventStatus.PLANNED, saved.getValue().getStatus());
        assertEquals(session, saved.getValue().getTrainingSession());
        assertEquals(3, response.blockOrder());
        assertEquals(List.of("A_FIRST", "B_SECOND", "C_THIRD"),
                response.metrics().stream().map(m -> m.code()).toList());

        ArgumentCaptor<SessionEventMetricEntity> rows = ArgumentCaptor.forClass(SessionEventMetricEntity.class);
        verify(entityManager, times(3)).persist(rows.capture());
        rows.getAllValues().forEach(row -> {
            assertEquals(EVENT_ID, row.getId().getSessionEventId());
            assertEquals(TEMPLATE_ID, row.getTemplateId());
        });
        verify(eventRepository).flush();
    }

    @Test
    void createAuthorizesThenLocksBeforeAnyRuleCheck() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        service.create(SESSION_ID, ACADEMY_ID,
                new SessionEvaluationEventCreateRequest(TEMPLATE_ID, List.of(optional.getCriterionId())), authentication);

        InOrder order = inOrder(accessPolicy, sessionLock, templateRepository, eventRepository);
        order.verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        order.verify(sessionLock).acquire(any());
        order.verify(templateRepository).findById(TEMPLATE_ID);
        order.verify(eventRepository).findMaxBlockOrder(SESSION_ID);
    }

    @Test
    void createKeepsChosenMetricsInDisplayOrder() {
        givenSession(TrainingSessionStatus.IN_PROGRESS);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        var response = service.create(SESSION_ID, ACADEMY_ID, new SessionEvaluationEventCreateRequest(TEMPLATE_ID,
                List.of(optional.getCriterionId(), first.getCriterionId())), authentication);

        assertEquals(List.of("A_FIRST", "D_OPTIONAL"), response.metrics().stream().map(m -> m.code()).toList());
        assertEquals(1, response.blockOrder());
    }

    @ParameterizedTest
    @EnumSource(value = TrainingSessionStatus.class, names = { "COMPLETED", "CANCELLED" })
    void createInClosedSessionIs409(TrainingSessionStatus status) {
        givenSession(status);

        assertThrows(ConflictException.class, () -> service.create(SESSION_ID, ACADEMY_ID,
                new SessionEvaluationEventCreateRequest(TEMPLATE_ID, null), authentication));
        verify(eventRepository, never()).save(any());
    }

    static Stream<Arguments> unusableTemplates() {
        return Stream.of(
                Arguments.of("not official", false, EvaluationTemplateStatus.ACTIVE),
                Arguments.of("retired", true, EvaluationTemplateStatus.RETIRED),
                Arguments.of("draft", true, EvaluationTemplateStatus.DRAFT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusableTemplates")
    void createRequiresAnOfficialActiveTemplate(String label, boolean official, EvaluationTemplateStatus status) {
        givenSession(TrainingSessionStatus.PLANNED);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template(official, status)));

        assertThrows(BadRequestException.class, () -> service.create(SESSION_ID, ACADEMY_ID,
                new SessionEvaluationEventCreateRequest(TEMPLATE_ID, null), authentication));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidMetricSelectionsWith400() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        List<List<UUID>> invalid = List.of(
                List.of(UUID.randomUUID()),                                                   // other template
                List.of(component.getCriterionId()),                                          // non-scored
                List.of(first.getCriterionId(), first.getCriterionId()),                      // duplicate
                List.of(first.getCriterionId(), second.getCriterionId(), third.getCriterionId(),
                        optional.getCriterionId()),                                           // 4th metric
                List.of());                                                                   // empty
        for (List<UUID> ids : invalid) {
            assertThrows(BadRequestException.class, () -> service.create(SESSION_ID, ACADEMY_ID,
                    new SessionEvaluationEventCreateRequest(TEMPLATE_ID, ids), authentication), ids.toString());
        }
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createWithTemplateWithoutDefaultsRequiresExplicitMetrics() {
        givenSession(TrainingSessionStatus.PLANNED);
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(criterionRepository.findAllForTemplate(TEMPLATE_ID)).thenReturn(List.of(optional));

        assertThrows(BadRequestException.class, () -> service.create(SESSION_ID, ACADEMY_ID,
                new SessionEvaluationEventCreateRequest(TEMPLATE_ID, null), authentication));
    }

    @Test
    void createInForeignAcademyIs404BeforeAuthorization() {
        when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(SESSION_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.create(SESSION_ID, ACADEMY_ID,
                new SessionEvaluationEventCreateRequest(TEMPLATE_ID, null), authentication));
        verify(accessPolicy, never()).requireManageAccess(any(), any(), any());
        verify(sessionLock, never()).acquire(any());
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------

    /**
     * Event lifecycle (DP-2.2) with the session IN_PROGRESS: origin x action → resulting status or 409.
     * A rejected transition never changes the event and never flushes.
     */
    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @CsvSource({
            "PLANNED,     START,    IN_PROGRESS",
            "PLANNED,     COMPLETE, CONFLICT",
            "PLANNED,     CANCEL,   CANCELLED",
            "PLANNED,     METRICS,  PLANNED",
            "IN_PROGRESS, START,    CONFLICT",
            "IN_PROGRESS, COMPLETE, COMPLETED",
            "IN_PROGRESS, CANCEL,   CANCELLED",
            "IN_PROGRESS, METRICS,  CONFLICT",
            "COMPLETED,   START,    CONFLICT",
            "COMPLETED,   COMPLETE, CONFLICT",
            "COMPLETED,   CANCEL,   CONFLICT",
            "COMPLETED,   METRICS,  CONFLICT",
            "CANCELLED,   START,    CONFLICT",
            "CANCELLED,   COMPLETE, CONFLICT",
            "CANCELLED,   CANCEL,   CONFLICT",
            "CANCELLED,   METRICS,  CONFLICT"
    })
    void lifecycleTransitionTable(SessionEvaluationEventStatus origin, String action, String expected) {
        SessionEvaluationEventEntity event = givenEvent(TrainingSessionStatus.IN_PROGRESS, origin);
        lenient().when(metricRepository.findForEvents(anyList())).thenReturn(List.of(metric(first)));

        Executable call = switch (action) {
            case "START" -> () -> service.start(EVENT_ID, ACADEMY_ID, authentication);
            case "COMPLETE" -> () -> service.complete(EVENT_ID, ACADEMY_ID, authentication);
            case "CANCEL" -> () -> service.cancel(EVENT_ID, ACADEMY_ID, authentication);
            case "METRICS" -> () -> service.replaceMetrics(EVENT_ID, ACADEMY_ID,
                    new SessionEventMetricsUpdateRequest(List.of(second.getCriterionId())), authentication);
            default -> throw new IllegalArgumentException(action);
        };

        if ("CONFLICT".equals(expected)) {
            assertThrows(ConflictException.class, call);
            assertEquals(origin, event.getStatus());
            verify(eventRepository, never()).flush();
            verify(metricRepository, never()).flush();
        } else {
            assertDoesNotThrow(call);
            assertEquals(SessionEvaluationEventStatus.valueOf(expected), event.getStatus());
            verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        }
    }

    @Test
    void transitionsLockTheSessionAndReReadTheEventBeforeCheckingItsStatus() {
        SessionEvaluationEventEntity event = givenEvent(TrainingSessionStatus.IN_PROGRESS,
                SessionEvaluationEventStatus.IN_PROGRESS);

        service.complete(EVENT_ID, ACADEMY_ID, authentication);

        InOrder order = inOrder(accessPolicy, sessionLock, entityManager, eventRepository);
        order.verify(accessPolicy).requireManageAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        order.verify(sessionLock).acquire(event.getTrainingSession());
        order.verify(entityManager).refresh(event);
        order.verify(eventRepository).flush();
    }

    @Test
    void startAndCompleteSetTheirTimestamps() {
        SessionEvaluationEventEntity event = givenEvent(TrainingSessionStatus.IN_PROGRESS,
                SessionEvaluationEventStatus.PLANNED);
        when(metricRepository.findForEvents(anyList())).thenReturn(List.of(metric(first)));

        service.start(EVENT_ID, ACADEMY_ID, authentication);
        assertEquals(NOW, event.getStartedAt());
        assertNull(event.getEndedAt());

        service.complete(EVENT_ID, ACADEMY_ID, authentication);
        assertEquals(NOW, event.getEndedAt());
    }

    @ParameterizedTest
    @EnumSource(value = TrainingSessionStatus.class, names = { "PLANNED", "COMPLETED", "CANCELLED" })
    void eventStartsOnlyWhileItsSessionIsInProgress(TrainingSessionStatus sessionStatus) {
        SessionEvaluationEventEntity event = givenEvent(sessionStatus, SessionEvaluationEventStatus.PLANNED);

        assertThrows(ConflictException.class, () -> service.start(EVENT_ID, ACADEMY_ID, authentication));
        assertEquals(SessionEvaluationEventStatus.PLANNED, event.getStatus());
    }

    @Test
    void eventWithoutMetricsCannotStart() {
        givenEvent(TrainingSessionStatus.IN_PROGRESS, SessionEvaluationEventStatus.PLANNED);
        when(metricRepository.findForEvents(anyList())).thenReturn(List.of());

        assertThrows(ConflictException.class, () -> service.start(EVENT_ID, ACADEMY_ID, authentication));
    }

    @Test
    void cancellingAnEventWithNonVoidedEvaluationsIs409() {
        SessionEvaluationEventEntity event = givenEvent(TrainingSessionStatus.IN_PROGRESS,
                SessionEvaluationEventStatus.IN_PROGRESS);
        when(eventRepository.hasNonVoidedEvaluations(EVENT_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.cancel(EVENT_ID, ACADEMY_ID, authentication));
        assertEquals(SessionEvaluationEventStatus.IN_PROGRESS, event.getStatus());
    }

    @Test
    void cancellingAPlannedEventLeavesItsTimestampsEmptyAndAnInProgressOneSetsTheEnd() {
        SessionEvaluationEventEntity planned = givenEvent(TrainingSessionStatus.IN_PROGRESS,
                SessionEvaluationEventStatus.PLANNED);
        service.cancel(EVENT_ID, ACADEMY_ID, authentication);
        assertNull(planned.getStartedAt());
        assertNull(planned.getEndedAt());

        SessionEvaluationEventEntity running = givenEvent(TrainingSessionStatus.IN_PROGRESS,
                SessionEvaluationEventStatus.IN_PROGRESS);
        service.cancel(EVENT_ID, ACADEMY_ID, authentication);
        assertEquals(NOW, running.getEndedAt());
    }

    @Test
    void eventOutsideTheAcademyIs404BeforeAuthorization() {
        when(eventRepository.findInAcademy(EVENT_ID, ACADEMY_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.start(EVENT_ID, ACADEMY_ID, authentication));
        assertThrows(NotFoundException.class, () -> service.get(EVENT_ID, ACADEMY_ID, authentication));
        verify(accessPolicy, never()).requireManageAccess(any(), any(), any());
        verify(accessPolicy, never()).requireReadAccess(any(), any(), any());
    }

    // ---- metrics ------------------------------------------------------------------------------------------------

    @Test
    void replaceMetricsDeletesRemovedOnesBeforeInsertingNewOnesAndKeepsTheRest() {
        givenEvent(TrainingSessionStatus.PLANNED, SessionEvaluationEventStatus.PLANNED);
        SessionEventMetricEntity keptRow = metric(first);
        SessionEventMetricEntity removedRow = metric(second);
        when(metricRepository.findForEvents(List.of(EVENT_ID))).thenReturn(List.of(keptRow, removedRow));

        var response = service.replaceMetrics(EVENT_ID, ACADEMY_ID, new SessionEventMetricsUpdateRequest(
                List.of(optional.getCriterionId(), first.getCriterionId())), authentication);

        InOrder order = inOrder(metricRepository, entityManager);
        order.verify(metricRepository).delete(removedRow);
        order.verify(metricRepository).flush();
        ArgumentCaptor<SessionEventMetricEntity> inserted = ArgumentCaptor.forClass(SessionEventMetricEntity.class);
        order.verify(entityManager).persist(inserted.capture());
        order.verify(metricRepository).flush();
        verify(metricRepository, never()).delete(keptRow);
        assertEquals(optional.getCriterionId(), inserted.getValue().getId().getCriterionId());
        assertEquals(List.of("A_FIRST", "D_OPTIONAL"), response.metrics().stream().map(m -> m.code()).toList());
    }

    @Test
    void replaceMetricsInClosedSessionIs409() {
        givenEvent(TrainingSessionStatus.COMPLETED, SessionEvaluationEventStatus.PLANNED);

        assertThrows(ConflictException.class, () -> service.replaceMetrics(EVENT_ID, ACADEMY_ID,
                new SessionEventMetricsUpdateRequest(List.of(first.getCriterionId())), authentication));
        verify(metricRepository, never()).delete(any());
    }

    // ---- reads --------------------------------------------------------------------------------------------------

    @Test
    void listGroupsMetricsPerEventWithOneMetricQuery() {
        TrainingSessionEntity session = givenSession(TrainingSessionStatus.IN_PROGRESS);
        SessionEvaluationEventEntity one = event(session, SessionEvaluationEventStatus.PLANNED, UUID.randomUUID(), 1);
        SessionEvaluationEventEntity two = event(session, SessionEvaluationEventStatus.PLANNED, UUID.randomUUID(), 2);
        when(eventRepository.findBySession(SESSION_ID)).thenReturn(List.of(one, two));
        when(metricRepository.findForEvents(anyList())).thenReturn(List.of(
                metric(one.getSessionEventId(), first), metric(two.getSessionEventId(), second),
                metric(two.getSessionEventId(), third)));

        var events = service.list(SESSION_ID, ACADEMY_ID, authentication);

        assertEquals(2, events.size());
        assertEquals(List.of("A_FIRST"), events.get(0).metrics().stream().map(m -> m.code()).toList());
        assertEquals(List.of("B_SECOND", "C_THIRD"), events.get(1).metrics().stream().map(m -> m.code()).toList());
        verify(metricRepository, times(1)).findForEvents(anyList());
        verify(accessPolicy).requireReadAccess(authentication, ACADEMY_ID, CATEGORY_ID);
    }

    // ---- pending players (T4.6) ---------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(SessionEvaluationEventStatus.class)
    void pendingPlayersAuthorizesReadAccessAndQueriesTheEventInAnyState(SessionEvaluationEventStatus status) {
        givenEvent(TrainingSessionStatus.IN_PROGRESS, status);
        SessionPlayerEntity entry = rosterEntry("Ana", "Lopez", PositionCode.CM, AttendanceStatus.LATE);
        when(sessionPlayerRepository.findPendingForEvent(SESSION_ID, CATEGORY_ID, EVENT_ID, AttendanceStatus.EVALUABLE))
                .thenReturn(List.of(entry));

        var pending = service.pendingPlayers(EVENT_ID, ACADEMY_ID, authentication);

        InOrder order = inOrder(accessPolicy, sessionPlayerRepository);
        order.verify(accessPolicy).requireReadAccess(authentication, ACADEMY_ID, CATEGORY_ID);
        order.verify(sessionPlayerRepository)
                .findPendingForEvent(SESSION_ID, CATEGORY_ID, EVENT_ID, AttendanceStatus.EVALUABLE);
        assertEquals(1, pending.size());
        assertEquals(entry.getPlayer().getPlayerId(), pending.get(0).playerId());
        assertEquals("Ana", pending.get(0).firstName());
        assertEquals("Lopez", pending.get(0).lastName());
        assertEquals("CM", pending.get(0).principalPosition());
        assertEquals(AttendanceStatus.LATE, pending.get(0).attendanceStatus());
        verify(sessionLock, never()).acquire(any());
    }

    @Test
    void pendingPlayersOnlyAsksForPresentOrLatePlayers() {
        assertEquals(EnumSet.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE), AttendanceStatus.EVALUABLE);
    }

    @Test
    void pendingPlayersWithNobodyLeftIsAnEmptyList() {
        givenEvent(TrainingSessionStatus.IN_PROGRESS, SessionEvaluationEventStatus.IN_PROGRESS);
        when(sessionPlayerRepository.findPendingForEvent(any(), any(), any(), any())).thenReturn(List.of());

        assertEquals(List.of(), service.pendingPlayers(EVENT_ID, ACADEMY_ID, authentication));
    }

    @Test
    void pendingPlayersOutsideTheAcademyIs404BeforeAuthorization() {
        when(eventRepository.findInAcademy(EVENT_ID, ACADEMY_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.pendingPlayers(EVENT_ID, ACADEMY_ID, authentication));
        verify(accessPolicy, never()).requireReadAccess(any(), any(), any());
        verify(sessionPlayerRepository, never()).findPendingForEvent(any(), any(), any(), any());
    }

    @Test
    void pendingPlayersStopsWhenReadAccessIsDenied() {
        givenEvent(TrainingSessionStatus.IN_PROGRESS, SessionEvaluationEventStatus.IN_PROGRESS);
        doThrow(new NotFoundException("out of scope"))
                .when(accessPolicy).requireReadAccess(authentication, ACADEMY_ID, CATEGORY_ID);

        assertThrows(NotFoundException.class, () -> service.pendingPlayers(EVENT_ID, ACADEMY_ID, authentication));
        verify(sessionPlayerRepository, never()).findPendingForEvent(any(), any(), any(), any());
    }

    private static SessionPlayerEntity rosterEntry(String firstName, String lastName, PositionCode position,
            AttendanceStatus attendance) {
        PositionEntity positionEntity = PositionEntity.builder().positionCode(position).build();
        UserEntity user = UserEntity.builder().firstName(firstName).lastName(lastName).build();
        PlayerEntity player = PlayerEntity.builder().playerId(UUID.randomUUID()).user(user)
                .principalPosition(positionEntity).build();
        return SessionPlayerEntity.builder().sessionPlayerId(UUID.randomUUID()).player(player)
                .attendanceStatus(attendance).build();
    }

    // ---- fixtures -----------------------------------------------------------------------------------------------

    private TrainingSessionEntity givenSession(TrainingSessionStatus status) {
        TrainingSessionEntity session = session(status);
        lenient().when(trainingSessionRepository.findBySessionIdAndAcademyCategory_Academy_AcademyId(
                SESSION_ID, ACADEMY_ID)).thenReturn(Optional.of(session));
        return session;
    }

    private SessionEvaluationEventEntity givenEvent(TrainingSessionStatus sessionStatus,
            SessionEvaluationEventStatus status) {
        SessionEvaluationEventEntity event = event(session(sessionStatus), status, EVENT_ID, 1);
        if (status != SessionEvaluationEventStatus.PLANNED) {
            event.setStartedAt(NOW.minusMinutes(10));
        }
        when(eventRepository.findInAcademy(EVENT_ID, ACADEMY_ID)).thenReturn(Optional.of(event));
        return event;
    }

    private SessionEvaluationEventEntity event(TrainingSessionEntity session, SessionEvaluationEventStatus status,
            UUID id, int blockOrder) {
        return SessionEvaluationEventEntity.builder()
                .sessionEventId(id)
                .trainingSession(session)
                .template(template)
                .blockOrder((short) blockOrder)
                .status(status)
                .build();
    }

    private static TrainingSessionEntity session(TrainingSessionStatus status) {
        AcademyEntity academy = new AcademyEntity();
        academy.setAcademyId(ACADEMY_ID);
        AcademyCategoryEntity category = new AcademyCategoryEntity();
        category.setAcademyCategoryId(CATEGORY_ID);
        category.setAcademy(academy);
        return TrainingSessionEntity.builder()
                .sessionId(SESSION_ID)
                .academyCategory(category)
                .name("Practice")
                .sessionDate(NOW)
                .status(status)
                .version(0L)
                .build();
    }

    private static EvaluationTemplateEntity template(boolean official, EvaluationTemplateStatus status) {
        EvaluationTemplateEntity template = new EvaluationTemplateEntity();
        template.setTemplateId(TEMPLATE_ID);
        template.setCode("JUEGO_REDUCIDO");
        template.setName("Juego reducido");
        template.setIsOfficial(official);
        template.setStatus(status);
        return template;
    }

    private EvaluationCriterionEntity criterion(String code, int displayOrder, boolean activeByDefault,
            boolean scored) {
        EvaluationCriterionEntity criterion = new EvaluationCriterionEntity();
        criterion.setCriterionId(UUID.randomUUID());
        criterion.setTemplate(template);
        criterion.setCode(code);
        criterion.setName(code);
        criterion.setDisplayOrder(displayOrder);
        criterion.setActiveByDefault(activeByDefault);
        criterion.setScored(scored);
        return criterion;
    }

    private SessionEventMetricEntity metric(EvaluationCriterionEntity criterion) {
        return metric(EVENT_ID, criterion);
    }

    private static SessionEventMetricEntity metric(UUID eventId, EvaluationCriterionEntity criterion) {
        return SessionEventMetricEntity.builder()
                .id(new SessionEventMetricId(eventId, criterion.getCriterionId()))
                .criterion(criterion)
                .templateId(TEMPLATE_ID)
                .build();
    }
}
