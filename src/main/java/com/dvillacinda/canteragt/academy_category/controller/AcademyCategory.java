package com.dvillacinda.canteragt.academy_category.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.academy_category.dto.AcademyCategoryCreateRequest;
import com.dvillacinda.canteragt.academy_category.dto.AcademyCategoryResponse;
import com.dvillacinda.canteragt.academy_category.service.AcademyCategoryService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/academy-category")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ACADEMY_ADMIN')")
public class AcademyCategory {

    private final AcademyCategoryService academyCategoryService;

    @PostMapping("/create-academy-category")
    public ResponseEntity<ApiResponse<AcademyCategoryResponse>> createAcademyCategory(
            @RequestHeader("X-Academy-Id") UUID academyId,
            @RequestBody @Validated AcademyCategoryCreateRequest request, Authentication authentication) {
        log.info("Creating academy category");

        var response = academyCategoryService.createAcademyCategory(academyId, request, authentication);

        log.info("Academy category created with id {}", response.academyCategoryId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Academy category created successfully", response));
    }

    @GetMapping("/get-academy-category-by-id/{id}")
    public ResponseEntity<ApiResponse<AcademyCategoryResponse>> getAcademyCategoryById(
            @PathVariable("id") UUID id, @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        log.info("Getting academy category with id {}", id);
        var response = academyCategoryService.getAcademyCategoryById(id, academyId, authentication);
        return ResponseEntity
                .ok(new ApiResponse<>(true, HttpStatus.OK, "Academy category found successfully", response));
    }

}
