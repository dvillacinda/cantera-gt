package com.dvillacinda.canteragt.evaluation_template.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;

public interface EvaluationTemplateRepository extends JpaRepository <EvaluationTemplateEntity, UUID>{
    @EntityGraph(attributePaths = { "dimensions", "childTemplates" })
    List<EvaluationTemplateEntity> findByIsOfficialTrueAndStatus(
            com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus status);

    @EntityGraph(attributePaths = { "dimensions", "childTemplates" })
    java.util.Optional<EvaluationTemplateEntity> findByTemplateIdAndIsOfficialTrueAndStatus(UUID templateId,
            com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus status);
}
