package com.dvillacinda.canteragt.training_session.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;

public interface TrainingSessionRepository extends JpaRepository<TrainingSessionEntity, UUID> {
    @EntityGraph(attributePaths = { "academyCategory", "location", "createdByCoach" })
    Page<TrainingSessionEntity> findAllByAcademyCategory_AcademyCategoryId(
            UUID academyCategoryId, Pageable pageable);

    @EntityGraph(attributePaths = { "academyCategory", "location", "createdByCoach" })
    Optional<TrainingSessionEntity> findBySessionIdAndAcademyCategory_AcademyCategoryId(
            UUID sessionId, UUID academyCategoryId);
}
