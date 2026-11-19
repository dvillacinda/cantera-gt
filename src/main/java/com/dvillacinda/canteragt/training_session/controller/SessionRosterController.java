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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.training_session.dto.AttendanceUpdateRequest;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayerResponse;
import com.dvillacinda.canteragt.training_session.dto.SessionPlayersAddRequest;
import com.dvillacinda.canteragt.training_session.service.SessionRosterService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/training-sessions/{sessionId}/players")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ACADEMY_ADMIN', 'COACH')")
public class SessionRosterController {
    private final SessionRosterService sessionRosterService;

    @GetMapping
    @Operation(summary = "List the session roster",
            description = "Players called to the session with name, principal position and attendance.")
    public ResponseEntity<ApiResponse<List<SessionPlayerResponse>>> list(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Session roster found successfully",
                sessionRosterService.list(sessionId, academyId, authentication)));
    }

    @PostMapping
    @Operation(summary = "Call players to the session",
            description = "Send player_ids (max 60) or all_active=true. Players need a current assignment in the "
                    + "session's category. An invalid batch is rejected as a whole and lists the rejected ids.")
    public ResponseEntity<ApiResponse<List<SessionPlayerResponse>>> add(
            @PathVariable UUID sessionId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated SessionPlayersAddRequest request,
            Authentication authentication) {
        List<SessionPlayerResponse> added = sessionRosterService.addPlayers(
                sessionId, academyId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, HttpStatus.CREATED,
                "Players added to the session: " + added.size(), added));
    }

    @DeleteMapping("/{playerId}")
    @Operation(summary = "Remove a player from the session",
            description = "Allowed while PLANNED or IN_PROGRESS and only if attendance is still PENDING.")
    public ResponseEntity<Void> remove(
            @PathVariable UUID sessionId,
            @PathVariable UUID playerId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        sessionRosterService.removePlayer(sessionId, academyId, playerId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{playerId}/attendance")
    @Operation(summary = "Record a player's attendance",
            description = "Only while the session is IN_PROGRESS. PENDING cannot be set explicitly.")
    public ResponseEntity<ApiResponse<SessionPlayerResponse>> updateAttendance(
            @PathVariable UUID sessionId,
            @PathVariable UUID playerId,
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated AttendanceUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Attendance updated successfully",
                sessionRosterService.updateAttendance(sessionId, academyId, playerId, request, authentication)));
    }
}
