package com.dvillacinda.canteragt.coach_assignment.controller;

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

import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentCreateRequest;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentResponse;
import com.dvillacinda.canteragt.coach_assignment.dto.CoachAssignmentUpdateRequest;
import com.dvillacinda.canteragt.coach_assignment.enums.CoachAssignmentStatus;
import com.dvillacinda.canteragt.coach_assignment.service.CoachAssignmentService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Role checks here are only the first gate; the academy scope (X-Academy-Id) is validated
 * against the database in {@link CoachAssignmentService} for every call.
 */
@RestController
@RequestMapping("/api/v1/coach-assignments")
@RequiredArgsConstructor
@Slf4j
public class CoachAssignmentController {

    private final CoachAssignmentService coachAssignmentService;

    @PostMapping("/create-coach-assignment")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<ApiResponse<CoachAssignmentResponse>> createCoachAssignment(
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated CoachAssignmentCreateRequest request, Authentication authentication) {
        log.info("Creating coach assignment");
        var response = coachAssignmentService.createCoachAssignment(academyId, request, authentication);
        log.info("Coach assignment created with id {}", response.coachAssignmentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Coach assignment created successfully", response));
    }

    @GetMapping("/get-coach-assignment-by-id/{id}")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH')")
    public ResponseEntity<ApiResponse<CoachAssignmentResponse>> getCoachAssignmentById(
            @PathVariable("id") UUID id, @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        log.info("Getting coach assignment with id {}", id);
        var response = coachAssignmentService.getCoachAssignmentById(id, academyId, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Coach assignment found successfully", response));
    }

    @GetMapping("/get-coach-assignments")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH')")
    public ResponseEntity<ApiResponse<List<CoachAssignmentResponse>>> getCoachAssignments(
            @RequestHeader("X-Academy-Id") UUID academyId, Authentication authentication) {
        log.info("Listing coach assignments");
        var response = coachAssignmentService.getCoachAssignments(academyId, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Coach assignments found successfully", response));
    }

    @PatchMapping("/update-coach-assignment-by-id/{id}")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<ApiResponse<CoachAssignmentResponse>> updateCoachAssignmentById(
            @PathVariable("id") UUID id, @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated CoachAssignmentUpdateRequest request, Authentication authentication) {
        log.info("Updating coach assignment with id {}", id);
        var response = coachAssignmentService.updateCoachAssignment(id, academyId, request, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Coach assignment updated successfully", response));
    }

    @PatchMapping("/update-coach-assignment-status-by-id/{id}/{status}")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<ApiResponse<CoachAssignmentResponse>> updateCoachAssignmentStatusById(
            @PathVariable("id") UUID id, @PathVariable CoachAssignmentStatus status,
            @RequestHeader("X-Academy-Id") UUID academyId, Authentication authentication) {
        log.info("Updating coach assignment status with id {}", id);
        var response = coachAssignmentService.updateCoachAssignmentStatusById(id, academyId, status, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Coach assignment status updated successfully", response));
    }
}
