package com.dvillacinda.canteragt.training_session.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.training_session.entity.SessionEvaluationEventEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionEvaluationEventStatus;

public interface SessionEvaluationEventRepository extends JpaRepository<SessionEvaluationEventEntity, UUID> {

    /** Event scoped to the academy, with its session, category and template joined (404 outside the academy). */
    @Query("""
            select e from SessionEvaluationEventEntity e
              join fetch e.trainingSession s
              join fetch s.academyCategory c
              join fetch c.academy a
              join fetch e.template
            where e.sessionEventId = :eventId
              and a.academyId = :academyId
            """)
    Optional<SessionEvaluationEventEntity> findInAcademy(
            @Param("eventId") UUID eventId, @Param("academyId") UUID academyId);

    /** Events of a session in block order, with their template joined in the same query. */
    @Query("""
            select e from SessionEvaluationEventEntity e
              join fetch e.template
            where e.trainingSession.sessionId = :sessionId
            order by e.blockOrder
            """)
    List<SessionEvaluationEventEntity> findBySession(@Param("sessionId") UUID sessionId);

    @Query("""
            select coalesce(max(e.blockOrder), 0) from SessionEvaluationEventEntity e
            where e.trainingSession.sessionId = :sessionId
            """)
    int findMaxBlockOrder(@Param("sessionId") UUID sessionId);

    boolean existsByTrainingSession_SessionIdAndStatus(UUID sessionId, SessionEvaluationEventStatus status);

    List<SessionEvaluationEventEntity> findAllByTrainingSession_SessionIdAndStatus(
            UUID sessionId, SessionEvaluationEventStatus status);

    /** evaluations.session_event_id is not mapped on EvaluationEntity yet (T4.4), hence the native query. */
    @Query(value = """
            select exists (select 1 from evaluations
                           where session_event_id = :eventId and status <> 'VOIDED')
            """, nativeQuery = true)
    boolean hasNonVoidedEvaluations(@Param("eventId") UUID eventId);
}
