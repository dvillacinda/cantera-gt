package com.dvillacinda.canteragt.training_session.controller;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.shared.dto.PageResponse;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionCreateRequest;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionFilter;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionResponse;
import com.dvillacinda.canteragt.training_session.dto.TrainingSessionUpdateRequest;
import com.dvillacinda.canteragt.training_session.enums.TrainingSessionStatus;
import com.dvillacinda.canteragt.training_session.service.TrainingSessionService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ACADEMY_ADMIN', 'COACH')")
public class TrainingSessionController {
    private final TrainingSessionService trainingSessionService;

    @PostMapping("/academy-categories/{academyCategoryId}/training-sessions")
    @Operation(summary = "Create a training session", description = "Creates a PLANNED session in an active category.")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> create(
            @PathVariable UUID academyCategoryId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated TrainingSessionCreateRequest request,
            Authentication authentication) {
        TrainingSessionResponse response = trainingSessionService.create(
                academyCategoryId, academyId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ApiResponse<>(true, HttpStatus.CREATED, "Training session created successfully", response));
    }

    @GetMapping("/academy-categories/{academyCategoryId}/training-sessions")
    @Operation(summary = "List training sessions", description = "Lists sessions in the selected category with filters.")
    public ResponseEntity<ApiResponse<PageResponse<TrainingSessionResponse>>> list(
            @PathVariable UUID academyCategoryId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestParam(name = "status", required = false) TrainingSessionStatus status,
            @RequestParam(name = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @ParameterObject @PageableDefault(size = 20, sort = "session_date", direction = Direction.DESC)
                    Pageable pageable,
            Authentication authentication) {
        PageResponse<TrainingSessionResponse> response = PageResponse.from(trainingSessionService.list(
                academyCategoryId, academyId, new TrainingSessionFilter(status, from, to), pageable, authentication));
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Training sessions found successfully",
                response));
    }

    @GetMapping("/training-sessions/{sessionId}")
    @Operation(summary = "Get a training session", description = "Returns a session within the selected academy.")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> get(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        TrainingSessionResponse response = trainingSessionService.get(sessionId, academyId, authentication);
        return ResponseEntity.ok(
                new ApiResponse<>(true, HttpStatus.OK, "Training session found successfully", response));
    }

    @PatchMapping("/training-sessions/{sessionId}")
    @Operation(summary = "Edit a planned training session", description = "Only PLANNED sessions can be edited.")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> update(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated TrainingSessionUpdateRequest request,
            Authentication authentication) {
        TrainingSessionResponse response = trainingSessionService.update(
                sessionId, academyId, request, authentication);
        return ResponseEntity.ok(
                new ApiResponse<>(true, HttpStatus.OK, "Training session updated successfully", response));
    }

    @PostMapping("/training-sessions/{sessionId}/start")
    @Operation(summary = "Start a planned training session")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> start(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return lifecycleResponse(trainingSessionService.start(sessionId, academyId, authentication),
                "Training session started successfully");
    }

    @PostMapping("/training-sessions/{sessionId}/complete")
    @Operation(summary = "Complete an in-progress training session")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> complete(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return lifecycleResponse(trainingSessionService.complete(sessionId, academyId, authentication),
                "Training session completed successfully");
    }

    @PostMapping("/training-sessions/{sessionId}/cancel")
    @Operation(summary = "Cancel a planned or in-progress training session")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> cancel(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return lifecycleResponse(trainingSessionService.cancel(sessionId, academyId, authentication),
                "Training session cancelled successfully");
    }

    private ResponseEntity<ApiResponse<TrainingSessionResponse>> lifecycleResponse(
            TrainingSessionResponse response, String message) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, message, response));
    }
}
