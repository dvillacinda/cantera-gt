package com.dvillacinda.canteragt.evaluation_template.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationEntity;

public interface EvaluationRepository extends JpaRepository<EvaluationEntity, UUID> {
    @EntityGraph(attributePaths = { "academyCategory", "player", "template", "evaluator", "results", "results.criterion", "results.observations" })
    Optional<EvaluationEntity> findByEvaluationIdAndEvaluator_CoachId(UUID evaluationId, UUID evaluatorCoachId);
}
