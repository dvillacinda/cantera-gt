package com.dvillacinda.canteragt.training_session.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.training_session.entity.SessionStaffEntity;
import com.dvillacinda.canteragt.training_session.enums.SessionParticipationType;

public interface SessionStaffRepository extends JpaRepository<SessionStaffEntity, UUID> {
    @EntityGraph(attributePaths = { "trainingSession", "coach" })
    List<SessionStaffEntity> findAllByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryId(
            UUID sessionId, UUID academyCategoryId);

    boolean existsByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryIdAndCoach_CoachId(
            UUID sessionId, UUID academyCategoryId, UUID coachId);

    boolean existsByTrainingSession_SessionIdAndCoach_CoachId(UUID sessionId, UUID coachId);

    boolean existsByTrainingSession_SessionIdAndParticipationType(
            UUID sessionId, SessionParticipationType participationType);

    /** Staff with the coach's name joined in a single query (no N+1). LEAD first, then by name. */
    @Query("""
            select ss from SessionStaffEntity ss
              join fetch ss.coach c
              join fetch c.user u
            where ss.trainingSession.sessionId = :sessionId
            order by case when ss.participationType = com.dvillacinda.canteragt.training_session.enums.SessionParticipationType.LEAD
                          then 0 else 1 end,
                     u.lastName, u.firstName, c.coachId
            """)
    List<SessionStaffEntity> findStaff(@Param("sessionId") UUID sessionId);

    @Query("""
            select ss from SessionStaffEntity ss
              join fetch ss.coach c
              join fetch c.user
            where ss.trainingSession.sessionId = :sessionId
              and c.coachId = :coachId
            """)
    Optional<SessionStaffEntity> findStaffEntry(@Param("sessionId") UUID sessionId, @Param("coachId") UUID coachId);
}
