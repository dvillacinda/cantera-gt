package com.dvillacinda.canteragt.season.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.season.entity.SeasonEntity;

public interface SeasonRepository extends JpaRepository<SeasonEntity, UUID>{
    
}
