package com.dvillacinda.canteragt.position.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.position.dto.PositionCreateRequest;
import com.dvillacinda.canteragt.position.dto.PositionResponse;
import com.dvillacinda.canteragt.position.dto.PositionUpdateRequest;
import com.dvillacinda.canteragt.position.service.PositionService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@Slf4j
public class PositionController {

    private final PositionService positionService;

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'COACH', 'SYSTEM_ADMIN')")
    @PostMapping("/create-position")
    public ResponseEntity<ApiResponse<PositionResponse>> createPosition(
            @Valid @RequestBody PositionCreateRequest request) {
        log.info("Creating position");

        PositionResponse response = positionService.createPosition(request);
        log.info("Position created with id {}", response.positionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Position created successfully", response));
    }

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'COACH', 'SYSTEM_ADMIN')")
    @PatchMapping("/update-position-by-id/{position_id}")
    public ResponseEntity<ApiResponse<PositionResponse>> updatePosition(@PathVariable UUID position_id,
            @Valid @RequestBody PositionUpdateRequest request) {
        log.info("Updating position with id {}", position_id);
        PositionResponse response = positionService.updatePosition(position_id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Position updated successfully", response));
    }

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'COACH', 'SYSTEM_ADMIN')")
    @GetMapping("/get-position-by-id/{position_id}")
    public ResponseEntity<ApiResponse<PositionResponse>> getMethodName(@RequestParam UUID position_id) {
        log.info("Getting position with id {}", position_id);
        PositionResponse response = positionService.getResponseById(position_id);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Position found successfully", response));
    }

    @PreAuthorize ("hasAnyRole('ACADEMY_ADMIN', 'COACH', 'SYSTEM_ADMIN')")
    @DeleteMapping ("/delete-position-by-id/{position_id}")
    public ResponseEntity<ApiResponse<Void>> deletePositionById(@PathVariable UUID position_id) {
        log.info("Deleting position with id {}", position_id);
        positionService.deletePositionById(position_id);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Position deleted successfully", null));
    }
    

}
