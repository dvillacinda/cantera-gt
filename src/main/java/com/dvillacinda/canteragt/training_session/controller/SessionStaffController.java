package com.dvillacinda.canteragt.training_session.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffAddRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionStaffResponse;
import com.dvillacinda.canteragt.training_session.service.SessionStaffService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/training-sessions/{sessionId}/staff")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ACADEMY_ADMIN', 'COACH')")
public class SessionStaffController {
    private final SessionStaffService sessionStaffService;

    @GetMapping
    @Operation(summary = "List the session staff", description = "Coaches taking part in the session, LEAD first.")
    public ResponseEntity<ApiResponse<List<SessionStaffResponse>>> list(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Session staff found successfully",
                sessionStaffService.list(sessionId, academyId, authentication)));
    }

    @PostMapping
    @Operation(summary = "Add a coach to the session staff",
            description = "The coach needs a current assignment in the session's category. Only one LEAD per "
                    + "session. Allowed while PLANNED or IN_PROGRESS.")
    public ResponseEntity<ApiResponse<SessionStaffResponse>> add(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated SessionStaffAddRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, HttpStatus.CREATED,
                "Coach added to the session staff",
                sessionStaffService.addStaff(sessionId, academyId, request, authentication)));
    }

    @DeleteMapping("/{coachId}")
    @Operation(summary = "Remove a coach from the session staff",
            description = "Allowed while PLANNED or IN_PROGRESS; the LEAD cannot be removed while IN_PROGRESS.")
    public ResponseEntity<Void> remove(
            @PathVariable UUID sessionId,
            @PathVariable UUID coachId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        sessionStaffService.removeStaff(sessionId, academyId, coachId, authentication);
        return ResponseEntity.noContent().build();
    }
}
