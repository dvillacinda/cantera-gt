package com.dvillacinda.canteragt.academy_admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminCreateRequest;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminResponse;
import com.dvillacinda.canteragt.academy_admin.service.AcademyAdminService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/academy-admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@Slf4j
@RequiredArgsConstructor
public class AcademyAdminController {
    private final AcademyAdminService academyAdminService;

    @PostMapping("/create-academy")
    public ResponseEntity<ApiResponse<AcademyAdminResponse>> createAcademy(
            @RequestBody AcademyAdminCreateRequest request) {
        log.info("Creating academy admin");
        var response = academyAdminService.createAcademyAdmin(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Academy admin created successfully", response));
    }

    // TODO: implement update academy admin
}
