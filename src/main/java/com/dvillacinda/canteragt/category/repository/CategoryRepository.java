package com.dvillacinda.canteragt.category.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.category.entity.CategoryEntity;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID>{
    
}
