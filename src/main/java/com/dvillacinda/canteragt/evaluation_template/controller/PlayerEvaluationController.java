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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationCreateRequest;
import com.dvillacinda.canteragt.evaluation_template.dto.EvaluationResponse;
import com.dvillacinda.canteragt.evaluation_template.service.EvaluationService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/players/{playerId}/evaluations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COACH')")
public class PlayerEvaluationController {
    private final EvaluationService evaluationService;

    @PostMapping
    @Operation(summary = "Record an evaluation for a player",
            description = "Without session_event_id: the original rules (current assignments, active official "
                    + "template, results for every default metric). With session_event_id: X-Academy-Id is required; "
                    + "the session and the event must be IN_PROGRESS; the caller needs data entry access (current "
                    + "HEAD_COACH, or session staff while the session is IN_PROGRESS); the player must be convened "
                    + "with attendance PRESENT or LATE and have no evaluation in the event that is not voided; "
                    + "template and version must be the event's; results are required for the event's metrics. "
                    + "Non-blocking warnings are returned in warnings.")
    public ResponseEntity<ApiResponse<EvaluationResponse>> create(@PathVariable UUID playerId,
            @RequestHeader(value = "X-Academy-Id", required = false) UUID academyId,
            @Valid @RequestBody EvaluationCreateRequest request, Authentication authentication) {
        EvaluationResponse response = evaluationService.create(playerId, academyId, request, authentication);
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
