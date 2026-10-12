package com.dvillacinda.canteragt.evaluation_template.maper;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCriterionResponse;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationTemplateResponse;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationCriterionEntity;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationCriterionRepository;
import com.dvillacinda.canteragt.evaluation_template.repository.MetricScaleLevelRepository;

@Component
@RequiredArgsConstructor
public class EvaluationTemplateMapper {
    private final EvaluationCriterionRepository criterionRepository;
    private final MetricScaleLevelRepository scaleLevelRepository;

    public EvaluationTemplateResponse toResponse(EvaluationTemplateEntity entity) {
        return new EvaluationTemplateResponse(
                entity.getTemplateId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getCode(),
                entity.getObjective(),
                entity.getInstructions(),
                entity.getIsSessionIntegrated(),
                entity.getGroupSizeMin(),
                entity.getGroupSizeMax(),
                entity.getFrequencyMinDays(),
                entity.getFrequencyMaxDays(),
                entity.getValidityDays(),
                entity.getScoreAggregation(),
                entity.getIsOfficial(),
                entity.getAcademy() == null ? null : entity.getAcademy().getAcademyId(),
                entity.getChildTemplates().stream().map(child -> child.getTemplateId())
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDimensions(),
                criterionRepository.findAllForTemplate(entity.getTemplateId()).stream()
                        .map(this::toCriterionResponse).toList());
    }

    private EvaluationCriterionResponse toCriterionResponse(EvaluationCriterionEntity criterion) {
        return new EvaluationCriterionResponse(
                criterion.getCriterionId(), criterion.getDimensionCode(), criterion.getCode(), criterion.getName(),
                criterion.getDescription(), criterion.getWeight(), criterion.getMinScore(), criterion.getMaxScore(),
                criterion.getDisplayOrder(), criterion.getMetricType(), criterion.getUnit(),
                criterion.getAttemptsCount(), criterion.getMinOpportunities(), criterion.getScaleMin(),
                criterion.getScaleMax(), criterion.isCommentRequired(), criterion.isActiveByDefault(),
                criterion.isScored(), criterion.getLateralityMode(), criterion.getScoringMethod(),
                criterion.getScoringConfig(), scaleLevelRepository.findByCriterion_CriterionIdOrderByLevel(
                        criterion.getCriterionId()).stream()
                        .map(level -> new EvaluationCriterionResponse.ScaleLevel(
                                level.getLevel(), level.getLabel(), level.getDescriptor(), level.getScore()))
                        .toList());
    }
}
