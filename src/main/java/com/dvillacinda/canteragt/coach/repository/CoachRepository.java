package com.dvillacinda.canteragt.coach.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.coach.entity.CoachEntity;

public interface CoachRepository extends JpaRepository<CoachEntity, UUID> {

    boolean existsByUser_UserId(UUID userId);

}
