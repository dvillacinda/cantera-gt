package com.dvillacinda.canteragt.training_session.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;

public interface SessionPlayerRepository extends JpaRepository<SessionPlayerEntity, UUID> {
    @EntityGraph(attributePaths = { "trainingSession", "player" })
    List<SessionPlayerEntity> findAllByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryId(
            UUID sessionId, UUID academyCategoryId);

    boolean existsByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryIdAndPlayer_PlayerId(
            UUID sessionId, UUID academyCategoryId, UUID playerId);
}
