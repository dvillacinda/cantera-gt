package com.dvillacinda.canteragt.evaluation_template.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationVoidRequest;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationRepository;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationAccessPolicy.Access;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationAccessPolicy.Level;
import com.dvillacinda.canteragt.shared.exception.BadRequestException;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.shared.exception.UnauthorizedAccessException;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionLock;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Auditable voiding of evaluations (T4.5; ADR-0004, DP-2.4). Nothing is deleted: the evaluation keeps its results
 * and becomes VOIDED with when, who and why. A voided evaluation frees the (event, player) unique index, so the
 * player can be evaluated again in that event. Voiding is allowed in any session or event state (T4.5-D3).
 */
@Service
@RequiredArgsConstructor
public class EvaluationVoidService {
    static final int MAX_REASON = 500;

    private final EvaluationRepository evaluationRepository;
    private final EvaluationAccessPolicy accessPolicy;
    private final TrainingSessionLock sessionLock;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * Order: evaluation of that player (404) → access: creator or current HEAD_COACH (others that can read get 403,
     * the rest 404) → active coach account (403) → session lock when the evaluation belongs to a session, and re-read
     * → only COMPLETED can be voided (409) → reason, author and time.
     */
    @Transactional
    public EvaluationResponse voidEvaluation(UUID playerId, UUID evaluationId, EvaluationVoidRequest request,
            Authentication authentication) {
        EvaluationEntity evaluation = evaluationRepository.findDetailedByEvaluationId(evaluationId)
                .filter(found -> found.getPlayer().getPlayerId().equals(playerId))
                .orElseThrow(() -> new NotFoundException("Evaluation not found"));
        Access access = accessPolicy.accessOf(evaluation, authentication);
        if (access.level() == Level.NONE) {
            throw new NotFoundException("Evaluation not found");
        }
        if (access.level() != Level.VOID) {
            throw new UnauthorizedAccessException(
                    "Only the evaluator or the category's current head coach can void an evaluation");
        }
        if (access.user().getStatus() == null || !access.user().getStatus().canLogIn()) {
            throw new UnauthorizedAccessException("User account is not active");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON) {
            throw new BadRequestException("reason must have between 1 and " + MAX_REASON + " characters");
        }

        if (evaluation.getTrainingSession() != null) {
            // DP-2.5: serialize with re-evaluation in the same event and with cancelling the event
            sessionLock.acquire(evaluation.getTrainingSession());
            entityManager.refresh(evaluation);
        }
        if ("VOIDED".equals(evaluation.getStatus())) {
            throw new ConflictException("Evaluation is already voided");
        }
        if (!"COMPLETED".equals(evaluation.getStatus())) {
            throw new ConflictException("Only COMPLETED evaluations can be voided, but it is " + evaluation.getStatus());
        }
        evaluation.setStatus("VOIDED");
        evaluation.setVoidedAt(LocalDateTime.now(clock));
        evaluation.setVoidedByCoach(access.coach());
        evaluation.setVoidReason(reason);
        return EvaluationService.toResponse(evaluationRepository.saveAndFlush(evaluation), List.of());
    }
}
