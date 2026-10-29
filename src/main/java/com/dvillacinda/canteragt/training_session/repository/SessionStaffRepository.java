package com.dvillacinda.canteragt.training_session.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;

public interface SessionStaffRepository extends JpaRepository<SessionStaffEntity, UUID> {
    @EntityGraph(attributePaths = { "trainingSession", "coach" })
    List<SessionStaffEntity> findAllByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryId(
            UUID sessionId, UUID academyCategoryId);

    boolean existsByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryIdAndCoach_CoachId(
            UUID sessionId, UUID academyCategoryId, UUID coachId);
}
