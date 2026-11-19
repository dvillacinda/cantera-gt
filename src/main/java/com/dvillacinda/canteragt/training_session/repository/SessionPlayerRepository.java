package com.dvillacinda.canteragt.training_session.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    @Query("""
            select sp.player.playerId from SessionPlayerEntity sp
            where sp.trainingSession.sessionId = :sessionId
            """)
    List<UUID> findPlayerIdsBySession(@Param("sessionId") UUID sessionId);

    /** Roster with the player's name and position joined in a single query (no N+1). */
    @Query("""
            select sp from SessionPlayerEntity sp
              join fetch sp.player p
              join fetch p.user u
              join fetch p.principalPosition
            where sp.trainingSession.sessionId = :sessionId
            order by u.lastName, u.firstName, p.playerId
            """)
    List<SessionPlayerEntity> findRoster(@Param("sessionId") UUID sessionId);

    @Query("""
            select sp from SessionPlayerEntity sp
              join fetch sp.player p
              join fetch p.user u
              join fetch p.principalPosition
            where sp.trainingSession.sessionId = :sessionId
              and p.playerId in :playerIds
            order by u.lastName, u.firstName, p.playerId
            """)
    List<SessionPlayerEntity> findRosterEntries(
            @Param("sessionId") UUID sessionId, @Param("playerIds") Collection<UUID> playerIds);

    @Query("""
            select sp from SessionPlayerEntity sp
              join fetch sp.player p
              join fetch p.user
              join fetch p.principalPosition
            where sp.trainingSession.sessionId = :sessionId
              and p.playerId = :playerId
            """)
    Optional<SessionPlayerEntity> findRosterEntry(
            @Param("sessionId") UUID sessionId, @Param("playerId") UUID playerId);
}
