package com.dvillacinda.canteragt.evaluation_template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationVoidRequest;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationRepository;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationAccessPolicy.Access;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationAccessPolicy.Level;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.shared.enums.UserStatus;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionLock;
import com.dvillacinda.canteragt.user.entity.UserEntity;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class EvaluationVoidServiceTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID EVALUATION_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T16:00:00Z"), ZoneId.of("America/Guatemala"));

    @Mock EvaluationRepository evaluationRepository;
    @Mock EvaluationAccessPolicy accessPolicy;
    @Mock TrainingSessionLock sessionLock;
    @Mock EntityManager entityManager;
    @Mock Authentication auth;

    private EvaluationVoidService service;
    private EvaluationEntity evaluation;
    private CoachEntity head;
    private UserEntity activeUser;

    @BeforeEach
    void setUp() {
        service = new EvaluationVoidService(evaluationRepository, accessPolicy, sessionLock, entityManager, CLOCK);
        head = CoachEntity.builder().coachId(UUID.randomUUID()).build();
        activeUser = UserEntity.builder().userId(UUID.randomUUID()).status(UserStatus.ACTIVE).build();
        evaluation = new EvaluationEntity();
        evaluation.setEvaluationId(EVALUATION_ID);
        evaluation.setPlayer(PlayerEntity.builder().playerId(PLAYER_ID).build());
        evaluation.setEvaluator(CoachEntity.builder().coachId(UUID.randomUUID()).build());
        evaluation.setTemplate(EvaluationTemplateEntity.builder().templateId(UUID.randomUUID()).version(1).build());
        evaluation.setStatus("COMPLETED");
        lenient().when(evaluationRepository.findDetailedByEvaluationId(EVALUATION_ID)).thenReturn(Optional.of(evaluation));
        lenient().when(accessPolicy.accessOf(evaluation, auth)).thenReturn(new Access(Level.VOID, head, activeUser));
        lenient().when(evaluationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void voidingRecordsTrimmedReasonAuthorFromTheJwtAndServerTime() {
        var response = service.voidEvaluation(PLAYER_ID, EVALUATION_ID, new EvaluationVoidRequest("  Wrong player  "),
                auth);

        assertEquals("VOIDED", evaluation.getStatus());
        assertEquals("Wrong player", evaluation.getVoidReason());
        assertEquals(head, evaluation.getVoidedByCoach());
        assertEquals(LocalDateTime.of(2026, 11, 23, 10, 0), evaluation.getVoidedAt());
        assertEquals("VOIDED", response.status());
        assertEquals(head.getCoachId(), response.voidedByCoachId());
        assertEquals("Wrong player", response.voidReason());
        verify(evaluationRepository).saveAndFlush(evaluation);
    }

    @Test
    void secondVoidIs409AndKeepsTheFirstAudit() {
        evaluation.setStatus("VOIDED");
        evaluation.setVoidReason("first");

        assertThrows(ConflictException.class, () -> service.voidEvaluation(PLAYER_ID, EVALUATION_ID,
                new EvaluationVoidRequest("second"), auth));
        assertEquals("first", evaluation.getVoidReason());
        verify(evaluationRepository, never()).saveAndFlush(any());
    }

    @Test
    void outOfScopeIs404AndReadOnlyAccessIs403() {
        when(accessPolicy.accessOf(evaluation, auth)).thenReturn(new Access(Level.NONE, null, null));
        assertThrows(NotFoundException.class, () -> service.voidEvaluation(PLAYER_ID, EVALUATION_ID,
                new EvaluationVoidRequest("x"), auth));

        when(accessPolicy.accessOf(evaluation, auth)).thenReturn(new Access(Level.READ, null, activeUser));
        assertThrows(UnauthorizedAccessException.class, () -> service.voidEvaluation(PLAYER_ID, EVALUATION_ID,
                new EvaluationVoidRequest("x"), auth));
        assertEquals("COMPLETED", evaluation.getStatus());
    }

    @Test
    void unknownEvaluationOrAnotherPlayersIdIs404BeforeTheAccessCheck() {
        when(evaluationRepository.findDetailedByEvaluationId(EVALUATION_ID)).thenReturn(Optional.of(evaluation));
        assertThrows(NotFoundException.class, () -> service.voidEvaluation(UUID.randomUUID(), EVALUATION_ID,
                new EvaluationVoidRequest("x"), auth));
        when(evaluationRepository.findDetailedByEvaluationId(EVALUATION_ID)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.voidEvaluation(PLAYER_ID, EVALUATION_ID,
                new EvaluationVoidRequest("x"), auth));
        verify(accessPolicy, never()).accessOf(any(), any());
    }

    @Test
    void inactiveAccountCannotVoid() {
        UserEntity inactive = UserEntity.builder().userId(UUID.randomUUID()).status(UserStatus.INACTIVE).build();
        when(accessPolicy.accessOf(evaluation, auth)).thenReturn(new Access(Level.VOID, head, inactive));

        assertThrows(UnauthorizedAccessException.class, () -> service.voidEvaluation(PLAYER_ID, EVALUATION_ID,
                new EvaluationVoidRequest("x"), auth));
    }

    @Test
    void blankReasonIs400EvenWhenCalledWithoutBeanValidation() {
        assertThrows(BadRequestException.class, () -> service.voidEvaluation(PLAYER_ID, EVALUATION_ID,
                new EvaluationVoidRequest("   "), auth));
        assertThrows(BadRequestException.class, () -> service.voidEvaluation(PLAYER_ID, EVALUATION_ID,
                new EvaluationVoidRequest("x".repeat(501)), auth));
        assertNull(evaluation.getVoidedAt());
    }

    /** DP-2.5: an evaluation of a session is voided under the session lock, re-read before its status is checked. */
    @Test
    void sessionEvaluationIsLockedAndReReadBeforeTheStatusCheck() {
        TrainingSessionEntity session = TrainingSessionEntity.builder().sessionId(UUID.randomUUID()).build();
        evaluation.setTrainingSession(session);

        service.voidEvaluation(PLAYER_ID, EVALUATION_ID, new EvaluationVoidRequest("x"), auth);

        InOrder order = inOrder(accessPolicy, sessionLock, entityManager, evaluationRepository);
        order.verify(accessPolicy).accessOf(evaluation, auth);
        order.verify(sessionLock).acquire(session);
        order.verify(entityManager).refresh(evaluation);
        order.verify(evaluationRepository).saveAndFlush(evaluation);
    }

    @Test
    void standaloneEvaluationNeedsNoLock() {
        service.voidEvaluation(PLAYER_ID, EVALUATION_ID, new EvaluationVoidRequest("x"), auth);

        verify(sessionLock, never()).acquire(any());
        verify(entityManager, never()).refresh(any());
    }
}
