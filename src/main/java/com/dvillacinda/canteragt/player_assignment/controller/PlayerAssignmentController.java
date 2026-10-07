package com.dvillacinda.canteragt.player_assignment.controller;

import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentCreateRequest;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentResponse;
import com.dvillacinda.canteragt.player_assignment.dto.PlayerAssignmentUpdateRequest;
import com.dvillacinda.canteragt.player_assignment.enums.PlayerAssignmentStatus;
import com.dvillacinda.canteragt.player_assignment.service.PlayerAssignmentService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Role checks here are only the first gate. Staff endpoints validate the academy scope (X-Academy-Id)
 * against the database, and the "my" endpoints resolve the caller's own player profile from the token;
 * both happen in {@link PlayerAssignmentService} for every call.
 */
@RestController
@RequestMapping("/api/v1/player-assignments")
@RequiredArgsConstructor
@Slf4j
public class PlayerAssignmentController {

    private final PlayerAssignmentService playerAssignmentService;

    @PostMapping("/create-player-assignment")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<ApiResponse<PlayerAssignmentResponse>> createPlayerAssignment(
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated PlayerAssignmentCreateRequest request, Authentication authentication) {
        log.info("Creating player assignment");
        var response = playerAssignmentService.createPlayerAssignment(academyId, request, authentication);
        log.info("Player assignment created with id {}", response.playerAssignmentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Player assignment created successfully", response));
    }

    @GetMapping("/get-player-assignment-by-id/{id}")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH')")
    public ResponseEntity<ApiResponse<PlayerAssignmentResponse>> getPlayerAssignmentById(
            @PathVariable("id") UUID id, @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        log.info("Getting player assignment with id {}", id);
        var response = playerAssignmentService.getPlayerAssignmentById(id, academyId, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Player assignment found successfully", response));
    }

    @GetMapping("/get-player-assignments")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH')")
    public ResponseEntity<ApiResponse<List<PlayerAssignmentResponse>>> getPlayerAssignments(
            @RequestHeader("X-Academy-Id") UUID academyId, Authentication authentication) {
        log.info("Listing player assignments");
        var response = playerAssignmentService.getPlayerAssignments(academyId, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Player assignments found successfully", response));
    }

    @GetMapping("/get-my-player-assignment-by-id/{id}")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ApiResponse<PlayerAssignmentResponse>> getMyPlayerAssignmentById(
            @PathVariable("id") UUID id, Authentication authentication) {
        log.info("Getting own player assignment with id {}", id);
        var response = playerAssignmentService.getMyPlayerAssignmentById(id, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Player assignment found successfully", response));
    }

    @GetMapping("/get-my-player-assignments")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ApiResponse<List<PlayerAssignmentResponse>>> getMyPlayerAssignments(
            Authentication authentication) {
        log.info("Listing own player assignments");
        var response = playerAssignmentService.getMyPlayerAssignments(authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Player assignments found successfully", response));
    }

    @PatchMapping("/update-player-assignment-by-id/{id}")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<ApiResponse<PlayerAssignmentResponse>> updatePlayerAssignmentById(
            @PathVariable("id") UUID id, @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated PlayerAssignmentUpdateRequest request, Authentication authentication) {
        log.info("Updating player assignment with id {}", id);
        var response = playerAssignmentService.updatePlayerAssignment(id, academyId, request, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Player assignment updated successfully", response));
    }

    @PatchMapping("/update-player-assignment-status-by-id/{id}/{status}")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<ApiResponse<PlayerAssignmentResponse>> updatePlayerAssignmentStatusById(
            @PathVariable("id") UUID id, @PathVariable PlayerAssignmentStatus status,
            @RequestHeader("X-Academy-Id") UUID academyId, Authentication authentication) {
        log.info("Updating player assignment status with id {}", id);
        var response = playerAssignmentService.updatePlayerAssignmentStatusById(id, academyId, status,
                authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Player assignment status updated successfully", response));
    }
}
