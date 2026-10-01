package com.dvillacinda.canteragt.position.repository;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.position.entity.PositionEntity;

public interface PositionRepository extends JpaRepository<PositionEntity, UUID> {
    public Set<PositionEntity> findAllById(Set<UUID> positionIds);
}
