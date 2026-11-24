package com.dvillacinda.canteragt.evaluation_template.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationWarningCode;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationRepository;
import com.dvillacinda.canteragt.evaluation_template.service.SessionEventEvaluationContextService.EventContext;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricEntity;
import com.dvillacinda.canteragt.training_session.entity.SessionEventMetricId;
import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.repository.SessionEvaluationEventRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionEventMetricRepository;
import com.dvillacinda.canteragt.training_session.repository.SessionPlayerRepository;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionAccessPolicy;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionLock;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class SessionEventEvaluationContextServiceTest {
    private static final UUID ACADEMY_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID METRIC_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Mock SessionEvaluationEventRepository eventRepository;
    @Mock SessionEventMetricRepository metricRepository;
    @Mock SessionPlayerRepository sessionPlayerRepository;
    @Mock EvaluationRepository evaluationRepository;
    @Mock TrainingSessionAccessPolicy accessPolicy;
    @Mock TrainingSessionLock sessionLock;
    @Mock EntityManager entityManager;
    @Mock Authentication auth;

    private SessionEventEvaluationContextService service;
    private EvaluationTemplateEntity template;

    @BeforeEach
    void setUp() {
        service = new SessionEventEvaluationContextService(eventRepository, metricRepository, sessionPlayerRepository,
                evaluationRepository, accessPolicy, sessionLock, entityManager);
        template = EvaluationTemplateEntity.builder().templateId(UUID.randomUUID()).code("RONDO")
                .frequencyMinDays(7).groupSizeMin(4).build();
        lenient().when(metricRepository.findForEvents(List.of(EVENT_ID))).thenReturn(List.of(
                SessionEventMetricEntity.builder().id(new SessionEventMetricId(EVENT_ID, METRIC_ID)).build()));
    }

    // ---- open: rules 1, 2 and 4 ---------------------------------------------------------------------------------

    @Test
    void openAuthorizesThenLocksAndReReadsBeforeCheckingStates() {
        SessionEvaluationEventEntity event = givenEvent(TrainingSessionStatus.IN_PROGRESS,
                SessionEvaluationEventStatus.IN_PROGRESS);

        EventContext context = service.open(EVENT_ID, ACADEMY_ID, auth);

        InOrder order = inOrder(eventRepository, accessPolicy, sessionLock, entityManager, metricRepository);
        order.verify(eventRepository).findInAcademy(EVENT_ID, ACADEMY_ID);
        order.verify(accessPolicy).requireDataEntryAccess(auth, event.getTrainingSession());
        order.verify(sessionLock).acquire(event.getTrainingSession());
        order.verify(entityManager).refresh(event);
        order.verify(metricRepository).findForEvents(List.of(EVENT_ID));
        assertEquals(Set.of(METRIC_ID), context.metricCriterionIds());
        assertEquals(event.getTrainingSession(), context.session());
    }

    @Test
    void missingAcademyHeaderIs400AndUnknownEventIs404BeforeAuthorization() {
        assertThrows(BadRequestException.class, () -> service.open(EVENT_ID, null, auth));
        when(eventRepository.findInAcademy(EVENT_ID, ACADEMY_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.open(EVENT_ID, ACADEMY_ID, auth));
        verify(accessPolicy, never()).requireDataEntryAccess(any(), any());
    }

    /** E5: staff outside an in-progress session gets 403 from the policy before any state check or lock. */
    @Test
    void deniedDataEntryIsNotMaskedByTheStateChecks() {
        SessionEvaluationEventEntity event = givenEvent(TrainingSessionStatus.PLANNED,
                SessionEvaluationEventStatus.PLANNED);
        doThrow(new UnauthorizedAccessException("Session staff may enter data only while the training session is "
                + "in progress")).when(accessPolicy).requireDataEntryAccess(auth, event.getTrainingSession());

        assertThrows(UnauthorizedAccessException.class, () -> service.open(EVENT_ID, ACADEMY_ID, auth));
        verify(sessionLock, never()).acquire(any());
    }

    @ParameterizedTest
    @EnumSource(value = TrainingSessionStatus.class, names = { "PLANNED", "COMPLETED", "CANCELLED" })
    void sessionMustBeInProgress(TrainingSessionStatus status) {
        givenEvent(status, SessionEvaluationEventStatus.IN_PROGRESS);
        assertThrows(ConflictException.class, () -> service.open(EVENT_ID, ACADEMY_ID, auth));
    }

    @ParameterizedTest
    @EnumSource(value = SessionEvaluationEventStatus.class, names = { "PLANNED", "COMPLETED", "CANCELLED" })
    void eventMustBeInProgress(SessionEvaluationEventStatus status) {
        givenEvent(TrainingSessionStatus.IN_PROGRESS, status);
        assertThrows(ConflictException.class, () -> service.open(EVENT_ID, ACADEMY_ID, auth));
    }

    // ---- requireEvaluable: rules 3 and 7 ------------------------------------------------------------------------

    @Test
    void playerNotConvenedIs409() {
        EventContext context = context();
        when(sessionPlayerRepository.findByTrainingSession_SessionIdAndPlayer_PlayerId(SESSION_ID, PLAYER_ID))
                .thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> service.requireEvaluable(context, PLAYER_ID));
    }

    @ParameterizedTest
    @EnumSource(AttendanceStatus.class)
    void onlyPresentOrLatePlayersCanBeEvaluated(AttendanceStatus attendance) {
        EventContext context = context();
        when(sessionPlayerRepository.findByTrainingSession_SessionIdAndPlayer_PlayerId(SESSION_ID, PLAYER_ID))
                .thenReturn(Optional.of(SessionPlayerEntity.builder().attendanceStatus(attendance).build()));
        lenient().when(evaluationRepository.existsBySessionEvent_SessionEventIdAndPlayer_PlayerIdAndStatusNot(
                EVENT_ID, PLAYER_ID, "VOIDED")).thenReturn(false);

        if (attendance == AttendanceStatus.PRESENT || attendance == AttendanceStatus.LATE) {
            assertDoesNotThrow(() -> service.requireEvaluable(context, PLAYER_ID));
        } else {
            assertThrows(ConflictException.class, () -> service.requireEvaluable(context, PLAYER_ID));
        }
    }

    @Test
    void secondNonVoidedEvaluationInTheEventIs409() {
        EventContext context = context();
        when(sessionPlayerRepository.findByTrainingSession_SessionIdAndPlayer_PlayerId(SESSION_ID, PLAYER_ID))
                .thenReturn(Optional.of(SessionPlayerEntity.builder()
                        .attendanceStatus(AttendanceStatus.PRESENT).build()));
        when(evaluationRepository.existsBySessionEvent_SessionEventIdAndPlayer_PlayerIdAndStatusNot(
                EVENT_ID, PLAYER_ID, "VOIDED")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.requireEvaluable(context, PLAYER_ID));
    }

    // ---- warnings: rule 9 ---------------------------------------------------------------------------------------

    @Test
    void recentAndStillValidEvaluationAndSmallGroupProduceTheThreeWarnings() {
        givenLastEvaluation(TODAY.minusDays(3), TODAY.plusDays(10));
        givenAvailablePlayers(3);

        var codes = service.warnings(context(), PLAYER_ID, TODAY).stream().map(w -> w.code()).toList();

        assertEquals(List.of(EvaluationWarningCode.FREQUENCY_TOO_SOON, EvaluationWarningCode.PREVIOUS_STILL_VALID,
                EvaluationWarningCode.GROUP_TOO_SMALL), codes);
    }

    @Test
    void noWarningsWhenTheLastEvaluationIsOldAndTheGroupIsLargeEnough() {
        givenLastEvaluation(TODAY.minusDays(7), TODAY.minusDays(1));
        givenAvailablePlayers(4);

        assertTrue(service.warnings(context(), PLAYER_ID, TODAY).isEmpty());
    }

    @Test
    void firstEvaluationOfThePlayerOnlyChecksTheGroupSize() {
        when(evaluationRepository.findFirstByPlayer_PlayerIdAndTemplate_CodeAndStatusNotOrderByEvaluationDateDesc(
                PLAYER_ID, "RONDO", "VOIDED")).thenReturn(Optional.empty());
        givenAvailablePlayers(10);

        assertTrue(service.warnings(context(), PLAYER_ID, TODAY).isEmpty());
    }

    // ---- fixtures -----------------------------------------------------------------------------------------------

    private SessionEvaluationEventEntity givenEvent(TrainingSessionStatus sessionStatus,
            SessionEvaluationEventStatus eventStatus) {
        SessionEvaluationEventEntity event = event(sessionStatus, eventStatus);
        when(eventRepository.findInAcademy(EVENT_ID, ACADEMY_ID)).thenReturn(Optional.of(event));
        return event;
    }

    private SessionEvaluationEventEntity event(TrainingSessionStatus sessionStatus,
            SessionEvaluationEventStatus eventStatus) {
        TrainingSessionEntity session = TrainingSessionEntity.builder().sessionId(SESSION_ID).status(sessionStatus)
                .build();
        return SessionEvaluationEventEntity.builder().sessionEventId(EVENT_ID).trainingSession(session)
                .template(template).status(eventStatus).build();
    }

    private EventContext context() {
        SessionEvaluationEventEntity event = event(TrainingSessionStatus.IN_PROGRESS,
                SessionEvaluationEventStatus.IN_PROGRESS);
        return new EventContext(event, event.getTrainingSession(), Set.of(METRIC_ID));
    }

    private void givenLastEvaluation(LocalDate evaluatedOn, LocalDate validUntil) {
        EvaluationEntity last = new EvaluationEntity();
        last.setEvaluationDate(LocalDateTime.of(evaluatedOn, java.time.LocalTime.NOON));
        last.setValidUntil(validUntil);
        when(evaluationRepository.findFirstByPlayer_PlayerIdAndTemplate_CodeAndStatusNotOrderByEvaluationDateDesc(
                PLAYER_ID, "RONDO", "VOIDED")).thenReturn(Optional.of(last));
    }

    private void givenAvailablePlayers(long count) {
        when(sessionPlayerRepository.countByTrainingSession_SessionIdAndAttendanceStatusIn(
                SESSION_ID, SessionEventEvaluationContextService.EVALUABLE_ATTENDANCE)).thenReturn(count);
    }
}
