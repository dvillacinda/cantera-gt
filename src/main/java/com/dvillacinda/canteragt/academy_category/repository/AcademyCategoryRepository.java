package com.dvillacinda.canteragt.academy_category.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;

public interface AcademyCategoryRepository extends JpaRepository<AcademyCategoryEntity, UUID> {
    
}
