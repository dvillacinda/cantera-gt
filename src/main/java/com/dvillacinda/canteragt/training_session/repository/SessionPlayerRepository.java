package com.dvillacinda.canteragt.training_session.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.training_session.entity.SessionPlayerEntity;
import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;

public interface SessionPlayerRepository extends JpaRepository<SessionPlayerEntity, UUID> {
    @EntityGraph(attributePaths = { "trainingSession", "player" })
    List<SessionPlayerEntity> findAllByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryId(
            UUID sessionId, UUID academyCategoryId);

    boolean existsByTrainingSession_SessionIdAndTrainingSession_AcademyCategory_AcademyCategoryIdAndPlayer_PlayerId(
            UUID sessionId, UUID academyCategoryId, UUID playerId);

    @Query("""
            select sp.player.playerId from SessionPlayerEntity sp
            where sp.trainingSession.sessionId = :sessionId
              and sp.trainingSession.academyCategory.academyCategoryId = :academyCategoryId
              and sp.attendanceStatus = :attendanceStatus
            order by sp.player.playerId
            """)
    List<UUID> findPlayerIdsBySessionAndAttendanceStatus(
            @Param("sessionId") UUID sessionId,
            @Param("academyCategoryId") UUID academyCategoryId,
            @Param("attendanceStatus") AttendanceStatus attendanceStatus);
}
