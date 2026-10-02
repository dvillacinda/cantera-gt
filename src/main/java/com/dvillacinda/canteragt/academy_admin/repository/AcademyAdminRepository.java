package com.dvillacinda.canteragt.academy_admin.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.academy_admin.entity.AcademyAdminEntity;

public interface AcademyAdminRepository extends JpaRepository<AcademyAdminEntity, UUID> {
    
}
