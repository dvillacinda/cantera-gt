package com.dvillacinda.canteragt.evaluation_template.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;

public interface EvaluationRepository extends JpaRepository<EvaluationEntity, UUID> {
    @EntityGraph(attributePaths = { "academyCategory", "player", "template", "evaluator", "results", "results.criterion" })
    Optional<EvaluationEntity> findByEvaluationIdAndEvaluator_CoachId(UUID evaluationId, UUID evaluatorCoachId);

    /** Duplicate check for (event, player); the partial unique index evaluations_event_player_uq is the safety net. */
    boolean existsBySessionEvent_SessionEventIdAndPlayer_PlayerIdAndStatusNot(
            UUID sessionEventId, UUID playerId, String status);

    /** Latest evaluation of the player with a template code (any version), for the frequency/validity warnings. */
    Optional<EvaluationEntity> findFirstByPlayer_PlayerIdAndTemplate_CodeAndStatusNotOrderByEvaluationDateDesc(
            UUID playerId, String templateCode, String status);
}
