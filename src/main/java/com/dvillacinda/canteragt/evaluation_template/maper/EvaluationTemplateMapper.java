package com.dvillacinda.canteragt.evaluation_template.maper;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationTemplateResponse;
import com.dvillacinda.canteragt.evaluation_template.entity.EvaluationTemplateEntity;

@Component
public class EvaluationTemplateMapper {
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
                entity.getAcademy().getAcademyId(),
                entity.getChildTemplates().stream().map(child -> child.getTemplateId())
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDimensions());
    }
}
