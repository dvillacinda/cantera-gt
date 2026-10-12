package com.dvillacinda.canteragt.evaluation_template.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCreateRequest;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/players/{playerId}/evaluations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COACH')")
public class PlayerEvaluationController {
    private final EvaluationService evaluationService;

    @PostMapping
    public ResponseEntity<ApiResponse<EvaluationResponse>> create(@PathVariable UUID playerId,
            @Valid @RequestBody EvaluationCreateRequest request, Authentication authentication) {
        EvaluationResponse response = evaluationService.create(playerId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Evaluation recorded successfully", response));
    }

    @GetMapping("/{evaluationId}")
    public ResponseEntity<ApiResponse<EvaluationResponse>> get(@PathVariable UUID playerId,
            @PathVariable UUID evaluationId, Authentication authentication) {
        EvaluationResponse response = evaluationService.get(evaluationId, authentication);
        if (!response.playerId().equals(playerId)) {
            throw new com.dvillacinda.canteragt.shared.exception.NotFoundException("Evaluation not found");
        }
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Evaluation found successfully", response));
    }
}
