package com.dvillacinda.canteragt.coach_role.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.coach_role.entity.CoachRoleEntity;

public interface CoachRoleRepository extends JpaRepository<CoachRoleEntity, UUID> {
    
}
