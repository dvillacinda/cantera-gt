package com.dvillacinda.canteragt.academy_admin.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminCreateRequest;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminResponse;
import com.dvillacinda.canteragt.academy_admin.dto.AcademyAdminUpdateRequest;
import com.dvillacinda.canteragt.academy_admin.service.AcademyAdminService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.shared.enums.Status;

import jakarta.validation.Valid;
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

    @PatchMapping("/update-academy-admin-by-id/{academyAdminId}")
    public ResponseEntity<ApiResponse<AcademyAdminResponse>> updateAcademyAdminById(
            @PathVariable UUID academyAdminId, @Valid @RequestBody AcademyAdminUpdateRequest request) {
        log.info("Updating academy admin with id {}", academyAdminId);
        var response = academyAdminService.updateAcademyAdmin(academyAdminId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Academy admin updated successfully", response));
    }

    @PatchMapping("/update-academy-admin-status-by-id/{academyAdminId}/{status}")
    public ResponseEntity<ApiResponse<AcademyAdminResponse>> updateAcademyAdminStatusById(
            @PathVariable UUID academyAdminId, @PathVariable Status status) {
        log.info("Updating academy admin status with id {}", academyAdminId);
        var response = academyAdminService.updateAcademyAdminStatusById(academyAdminId, status);
        return ResponseEntity.ok(
                new ApiResponse<>(true, HttpStatus.OK, "Academy admin status updated successfully", response));
    }
}
