package com.dvillacinda.canteragt.coach_assignment.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.coach_assignment.entity.CoachAssignmentEntity;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;

/** Every lookup is scoped by academy so a caller can never reach another tenant's rows by id. */
public interface CoachAssignmentRepository extends JpaRepository<CoachAssignmentEntity, UUID> {

    Optional<CoachAssignmentEntity> findByCoachAssignmentIdAndAcademyCategory_Academy_AcademyId(
            UUID coachAssignmentId, UUID academyId);

    List<CoachAssignmentEntity> findByAcademyCategory_Academy_AcademyId(UUID academyId);

    boolean existsByCoach_CoachIdAndAcademyCategory_AcademyCategoryIdAndStatus(UUID coachId,
            UUID academyCategoryId, CoachAssignmentStatus status);

    /** Membership check used for tenant isolation: the coach must hold an assignment that is in force today. */
    @Query("""
            select count(ca) > 0 from CoachAssignmentEntity ca
            where ca.coach.user.keycloakId = :keycloakId
              and ca.academyCategory.academy.academyId = :academyId
              and ca.status = com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus.ACTIVE
              and ca.startDate <= :today
              and (ca.endDate is null or ca.endDate >= :today)
            """)
    boolean existsActiveAssignmentInAcademy(@Param("keycloakId") String keycloakId,
            @Param("academyId") UUID academyId, @Param("today") LocalDate today);
}
