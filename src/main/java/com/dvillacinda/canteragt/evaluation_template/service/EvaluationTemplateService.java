package com.dvillacinda.canteragt.evaluation_template.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationTemplateResponse;
import com.dvillacinda.canteragt.evaluation_template.maper.EvaluationTemplateMapper;
import com.dvillacinda.canteragt.evaluation_template.repository.EvaluationTemplateRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.evaluation_template.enums.EvaluationTemplateStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationTemplateService {
    private final EvaluationTemplateRepository evaluationTemplateRepository;
    private final EvaluationTemplateMapper evaluationTemplateMapper;
    @Transactional (readOnly = true)
    public EvaluationTemplateResponse getEvaluationTemplateById(UUID templateId) {
        return evaluationTemplateMapper.toResponse(evaluationTemplateRepository
                .findByTemplateIdAndIsOfficialTrueAndStatus(templateId, EvaluationTemplateStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Templet with id: " + templateId + " not founded")));

    }


    @Transactional (readOnly = true)
    public List<EvaluationTemplateResponse> getAllEvaluationTemplates() {
        var all_templates = evaluationTemplateRepository.findByIsOfficialTrueAndStatus(EvaluationTemplateStatus.ACTIVE);
        return all_templates.stream().map(evaluationTemplateMapper::toResponse).toList();
    }

}
