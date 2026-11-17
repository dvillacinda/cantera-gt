package com.dvillacinda.canteragt.training_session.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.training_session.entity.TrainingSessionEntity;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;

public interface TrainingSessionRepository extends JpaRepository<TrainingSessionEntity, UUID> {
    @EntityGraph(attributePaths = { "academyCategory", "academyCategory.academy", "location", "createdByCoach" })
    @Query(value = """
            select s from TrainingSessionEntity s
            where s.academyCategory.academyCategoryId = :academyCategoryId
              and s.status = coalesce(:status, s.status)
              and s.sessionDate >= coalesce(:fromDate, s.sessionDate)
              and s.sessionDate <= coalesce(:toDate, s.sessionDate)
            """,
            countQuery = """
            select count(s) from TrainingSessionEntity s
            where s.academyCategory.academyCategoryId = :academyCategoryId
              and s.status = coalesce(:status, s.status)
              and s.sessionDate >= coalesce(:fromDate, s.sessionDate)
              and s.sessionDate <= coalesce(:toDate, s.sessionDate)
            """)
    Page<TrainingSessionEntity> searchByCategory(
            @Param("academyCategoryId") UUID academyCategoryId,
            @Param("status") TrainingSessionStatus status,
            @Param("fromDate") java.time.LocalDateTime fromDate,
            @Param("toDate") java.time.LocalDateTime toDate,
            Pageable pageable);

    @EntityGraph(attributePaths = { "academyCategory", "academyCategory.academy", "location", "createdByCoach" })
    Optional<TrainingSessionEntity> findBySessionIdAndAcademyCategory_AcademyCategoryId(
            UUID sessionId, UUID academyCategoryId);

    @EntityGraph(attributePaths = { "academyCategory", "academyCategory.academy", "location", "createdByCoach" })
    Optional<TrainingSessionEntity> findBySessionIdAndAcademyCategory_Academy_AcademyId(
            UUID sessionId, UUID academyId);
}
