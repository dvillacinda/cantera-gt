package com.dvillacinda.canteragt.evaluation_template.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationTemplateResponse;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationTemplateService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/evaluation-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ACADEMY_ADMIN', 'COACH')")
@Slf4j
public class EvaluationTemplateController {

    private final EvaluationTemplateService evaluationTemplateService;

    @GetMapping("/get-all-templates")
    public ResponseEntity<ApiResponse<List<EvaluationTemplateResponse>>> getAllEvaluationTemplates() {
        log.info("Getting all templates");
        var templates = evaluationTemplateService.getAllEvaluationTemplates();
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Templates found successfully", templates));

    }

    @GetMapping("/get-template-by-id/{templateId}")
    public ResponseEntity<ApiResponse<EvaluationTemplateResponse>> getEvaluationTemplateById(
            @PathVariable UUID templateId) {
        log.info("Getting template with id {}", templateId);
        var template = evaluationTemplateService.getEvaluationTemplateById(templateId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Template found successfully", template));
    }
}
