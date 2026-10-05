package com.dvillacinda.canteragt.academy_category.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.academy_category.entity.AcademyCategoryEntity;

public interface AcademyCategoryRepository extends JpaRepository<AcademyCategoryEntity, UUID> {
    Optional<AcademyCategoryEntity> findByAcademyCategoryIdAndAcademy_AcademyId(UUID academyCategoryId,
            UUID academyId);
}
