package com.dvillacinda.canteragt.evaluation_template.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;

public interface EvaluationCriterionRepository extends JpaRepository<EvaluationCriterionEntity, UUID> {
    @Query("select c from EvaluationCriterionEntity c where c.template.templateId = :templateId order by c.displayOrder")
    List<EvaluationCriterionEntity> findAllForTemplate(@Param("templateId") UUID templateId);
}
