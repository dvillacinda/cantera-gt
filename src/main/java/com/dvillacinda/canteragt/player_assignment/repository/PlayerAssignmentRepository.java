package com.dvillacinda.canteragt.player_assignment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.player_assignment.entity.PlayerAssignmentEntity;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;

/**
 * Staff lookups are scoped by academy and player lookups by the owning player, so a caller can never
 * reach rows outside their scope by id.
 */
public interface PlayerAssignmentRepository extends JpaRepository<PlayerAssignmentEntity, UUID> {

    Optional<PlayerAssignmentEntity> findByPlayerAssignmentIdAndAcademyCategory_Academy_AcademyId(
            UUID playerAssignmentId, UUID academyId);

    List<PlayerAssignmentEntity> findByAcademyCategory_Academy_AcademyId(UUID academyId);

    Optional<PlayerAssignmentEntity> findByPlayerAssignmentIdAndPlayer_PlayerId(UUID playerAssignmentId,
            UUID playerId);

    List<PlayerAssignmentEntity> findByPlayer_PlayerIdOrderByStartDateDesc(UUID playerId);

    boolean existsByPlayer_PlayerIdAndAcademyCategory_AcademyCategoryIdAndStatus(UUID playerId,
            UUID academyCategoryId, PlayerAssignmentStatus status);

    @Query("""
            select count(pa) > 0 from PlayerAssignmentEntity pa
            where pa.player.user.userId = :userId
              and pa.academyCategory.academy.academyId = :academyId
              and pa.status = com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus.ACTIVE
              and pa.startDate <= :today
              and (pa.endDate is null or pa.endDate >= :today)
            """)
    boolean existsActiveAssignmentInAcademy(@Param("userId") UUID userId,
            @Param("academyId") UUID academyId, @Param("today") LocalDate today);

    @Query("""
            select pa from PlayerAssignmentEntity pa
            where pa.player.playerId = :playerId
              and pa.status = com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus.ACTIVE
              and pa.startDate <= :today and (pa.endDate is null or pa.endDate >= :today)
            order by pa.startDate desc
            """)
    List<PlayerAssignmentEntity> findCurrentAssignments(@Param("playerId") UUID playerId,
            @Param("today") LocalDate today);

    @Query("""
            select count(pa) > 0 from PlayerAssignmentEntity pa
            where pa.player.playerId = :playerId
              and pa.academyCategory.academy.academyId = :academyId
              and pa.status = com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus.ACTIVE
              and pa.startDate <= :today and (pa.endDate is null or pa.endDate >= :today)
            """)
    boolean existsCurrentAssignmentInAcademy(@Param("playerId") UUID playerId,
            @Param("academyId") UUID academyId, @Param("today") LocalDate today);

    @Query("""
            select count(pa) > 0 from PlayerAssignmentEntity pa
            where pa.player.playerId = :playerId
              and pa.academyCategory.academy.academyId = :academyId
              and pa.academyCategory.academyCategoryId = :categoryId
              and pa.status = com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus.ACTIVE
              and pa.startDate <= :today and (pa.endDate is null or pa.endDate >= :today)
            """)
    boolean existsCurrentAssignmentForCategory(@Param("playerId") UUID playerId,
            @Param("academyId") UUID academyId, @Param("categoryId") UUID categoryId,
            @Param("today") LocalDate today);
}
