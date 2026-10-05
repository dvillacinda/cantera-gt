package com.dvillacinda.canteragt.academy_admin.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.academy_admin.entity.AcademyAdminEntity;
import com.dvillacinda.canteragt.shared.enums.Status;

public interface AcademyAdminRepository extends JpaRepository<AcademyAdminEntity, UUID> {
    boolean existsByUser_UserIdAndAcademy_AcademyIdAndStatus(UUID userId, UUID academyId, Status status);

    boolean existsByUser_UserId(UUID userId);

    List<AcademyAdminEntity> findByUser_UserIdAndStatus(UUID userId, Status status);

    List<AcademyAdminEntity> findByUser_KeycloakIdAndStatus(String keycloakId, Status status);
}
