package com.dvillacinda.canteragt.training_session.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventCreateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionEvaluationEventResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionEventMetricsUpdateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayerResponse;
import com.dvillacinda.canteragt.training_session.service.SessionEvaluationEventService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ACADEMY_ADMIN', 'COACH')")
public class SessionEvaluationEventController {
    private final SessionEvaluationEventService eventService;

    @PostMapping("/training-sessions/{sessionId}/evaluation-events")
    @Operation(summary = "Create an evaluation event in a session",
            description = "Head coach of the category or an admin in scope. The session must be PLANNED or "
                    + "IN_PROGRESS and the template official and ACTIVE. block_order is assigned by the server "
                    + "(next number in the session). Without metric_criterion_ids the template's default metrics are "
                    + "loaded; otherwise 1 to 3 scored metrics of the template. The event starts as PLANNED.")
    public ResponseEntity<ApiResponse<SessionEvaluationEventResponse>> create(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated SessionEvaluationEventCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, HttpStatus.CREATED,
                "Evaluation event created successfully",
                eventService.create(sessionId, academyId, request, authentication)));
    }

    @GetMapping("/training-sessions/{sessionId}/evaluation-events")
    @Operation(summary = "List the evaluation events of a session",
            description = "Events in block order, each with its template and active metrics.")
    public ResponseEntity<ApiResponse<List<SessionEvaluationEventResponse>>> list(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Evaluation events found successfully",
                eventService.list(sessionId, academyId, authentication)));
    }

    @GetMapping("/evaluation-events/{eventId}")
    @Operation(summary = "Get an evaluation event", description = "Event with its template and active metrics.")
    public ResponseEntity<ApiResponse<SessionEvaluationEventResponse>> get(
            @PathVariable UUID eventId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Evaluation event found successfully",
                eventService.get(eventId, academyId, authentication)));
    }

    @GetMapping("/evaluation-events/{eventId}/pending-players")
    @Operation(summary = "List the players still to observe in an evaluation event",
            description = "Players convened in the session with attendance PRESENT or LATE who have no evaluation in "
                    + "this event that is not voided (a player with only voided evaluations is pending again). "
                    + "Ordered by last name and first name. Same fields as the roster; available in any event state.")
    public ResponseEntity<ApiResponse<List<SessionPlayerResponse>>> pendingPlayers(
            @PathVariable UUID eventId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Pending players found successfully",
                eventService.pendingPlayers(eventId, academyId, authentication)));
    }

    @PutMapping("/evaluation-events/{eventId}/metrics")
    @Operation(summary = "Replace the active metrics of an event",
            description = "Full replacement with 1 to 3 scored metrics of the event's template. Only while the "
                    + "event is PLANNED and its session PLANNED or IN_PROGRESS.")
    public ResponseEntity<ApiResponse<SessionEvaluationEventResponse>> replaceMetrics(
            @PathVariable UUID eventId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated SessionEventMetricsUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Event metrics updated successfully",
                eventService.replaceMetrics(eventId, academyId, request, authentication)));
    }

    @PostMapping("/evaluation-events/{eventId}/start")
    @Operation(summary = "Start an evaluation event",
            description = "PLANNED → IN_PROGRESS. The session must be IN_PROGRESS and the event needs at least one "
                    + "active metric.")
    public ResponseEntity<ApiResponse<SessionEvaluationEventResponse>> start(
            @PathVariable UUID eventId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Evaluation event started successfully",
                eventService.start(eventId, academyId, authentication)));
    }

    @PostMapping("/evaluation-events/{eventId}/complete")
    @Operation(summary = "Complete an evaluation event", description = "IN_PROGRESS → COMPLETED.")
    public ResponseEntity<ApiResponse<SessionEvaluationEventResponse>> complete(
            @PathVariable UUID eventId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Evaluation event completed successfully",
                eventService.complete(eventId, academyId, authentication)));
    }

    @PostMapping("/evaluation-events/{eventId}/cancel")
    @Operation(summary = "Cancel an evaluation event",
            description = "PLANNED or IN_PROGRESS → CANCELLED. 409 while the event has evaluations that are not "
                    + "voided.")
    public ResponseEntity<ApiResponse<SessionEvaluationEventResponse>> cancel(
            @PathVariable UUID eventId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Evaluation event cancelled successfully",
                eventService.cancel(eventId, academyId, authentication)));
    }
}
