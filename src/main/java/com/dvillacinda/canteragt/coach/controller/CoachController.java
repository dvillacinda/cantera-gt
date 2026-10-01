package com.dvillacinda.canteragt.coach.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import com.dvillacinda.canteragt.coach.dto.CoachCreateRequest;
import com.dvillacinda.canteragt.coach.dto.CoachResponse;
import com.dvillacinda.canteragt.coach.dto.CoachUpdateRequest;
import com.dvillacinda.canteragt.coach.service.CoachService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/coaches")
@RequiredArgsConstructor
@Slf4j
public class CoachController {

    private final CoachService coachService;
    
    @PostMapping("/create-coach")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<CoachResponse>> createCoach(@Valid @RequestBody CoachCreateRequest coach) {
        log.info("Creating coach");
        CoachResponse response = coachService.createCoach(coach);
        log.info("Coach created with id {}", response.coachId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Coach created successfully", response));
    }

    @DeleteMapping("/delete-coach-by-id/{coachId}")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN' , 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCoachById(@PathVariable UUID coachId) {
        log.info("Deleting coach with id {}", coachId);
        coachService.deleteCoachById(coachId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Coach deleted successfully", null));
    }

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'SYSTEM_ADMIN')")
    @PatchMapping("/update-coach-by-id/{coachId}")
    public ResponseEntity<ApiResponse<CoachResponse>> updateCoachById(@PathVariable UUID coachId,
            @Valid @RequestBody CoachUpdateRequest coach) {
        log.info("Updating coach with id {}", coachId);
        CoachResponse response = coachService.updateCoach(coachId, coach);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Coach updated successfully", response));
    }

    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN')")
    @GetMapping("/get-coach-by-id/{coachId}")
    public ResponseEntity<ApiResponse<CoachResponse>> getCoachById(@PathVariable UUID coachId) {
        log.info("Getting coach with id {}", coachId);
        CoachResponse response = coachService.getCoachById(coachId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Coach found successfully", response));
    }

}
