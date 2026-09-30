package com.dvillacinda.canteragt.player.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.player.entity.PlayerEntity;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {

    boolean existsByUser_UserId(UUID userId);

}
