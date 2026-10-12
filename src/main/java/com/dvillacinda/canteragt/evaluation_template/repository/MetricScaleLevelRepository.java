package com.dvillacinda.canteragt.evaluation_template.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dvillacinda.canteragt.evaluation_template.entity.MetricScaleLevelEntity;

public interface MetricScaleLevelRepository extends JpaRepository<MetricScaleLevelEntity, UUID> {
    List<MetricScaleLevelEntity> findByCriterion_CriterionIdOrderByLevel(UUID criterionId);
}
