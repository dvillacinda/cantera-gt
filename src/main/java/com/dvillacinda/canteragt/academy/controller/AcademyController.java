package com.dvillacinda.canteragt.academy.controller;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.academy.dto.AcademyCreateRequest;
import com.dvillacinda.canteragt.academy.dto.AcademyResponse;
import com.dvillacinda.canteragt.academy.dto.AcademyUpdateRequest;
import com.dvillacinda.canteragt.academy.service.AcademyService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/academy")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@Slf4j
@RequiredArgsConstructor
public class AcademyController {
    private final AcademyService academyService;

    @GetMapping("/get-academy-by-id/{academyId}")
    public ResponseEntity<ApiResponse<AcademyResponse>> getAcademyById(@PathVariable UUID academyId) {
        log.info("Getting academy with id {}", academyId);
        AcademyResponse response = academyService.getAcademyById(academyId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Academy found successfully", response));
    }

    @GetMapping("/get-all-academies")
    public ResponseEntity<ApiResponse<Set<AcademyResponse>>> getAllAcademies() {
        log.info("Getting all academies");
        Set<AcademyResponse> responses = academyService.getAllAcademies();
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Academies found successfully", responses));
    }

    @PostMapping("/create-academy")
    public ResponseEntity<ApiResponse<AcademyResponse>> createAcademy(
            @Valid @RequestBody AcademyCreateRequest request) {
        log.info("Creating academy");
        AcademyResponse response = academyService.createAcademy(request);
        log.info("Academy created with id {}", response.academyId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Academy created successfully", response));
    }

    @PatchMapping("/update-academy-by-id/{academyId}")
    public ResponseEntity<ApiResponse<AcademyResponse>> updateAcademyById(@PathVariable UUID academyId,
            @Valid @RequestBody AcademyUpdateRequest request) {
        log.info("Updating academy with id {}", academyId);
        AcademyResponse response = academyService.updateAcademy(academyId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Academy updated successfully", response));
    }

}
