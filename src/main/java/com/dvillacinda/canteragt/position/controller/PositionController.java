package com.dvillacinda.canteragt.position.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.position.dto.PositionCreateRequest;
import com.dvillacinda.canteragt.position.dto.PositionResponse;
import com.dvillacinda.canteragt.position.dto.PositionUpdateRequest;
import com.dvillacinda.canteragt.position.service.PositionService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Positions are a catalog seeded by Flyway (V6) and referenced by evaluation templates through their code:
 * any authenticated user can read it, only SYSTEM_ADMIN can change it.
 */
@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@Slf4j
public class PositionController {

    private final PositionService positionService;

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/create-position")
    public ResponseEntity<ApiResponse<PositionResponse>> createPosition(
            @Valid @RequestBody PositionCreateRequest request) {
        log.info("Creating position");
        PositionResponse response = positionService.createPosition(request);
        log.info("Position created with id {}", response.positionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Position created successfully", response));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PatchMapping("/update-position-by-id/{positionId}")
    public ResponseEntity<ApiResponse<PositionResponse>> updatePosition(@PathVariable UUID positionId,
            @Valid @RequestBody PositionUpdateRequest request) {
        log.info("Updating position with id {}", positionId);
        PositionResponse response = positionService.updatePosition(positionId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Position updated successfully", response));
    }

    @GetMapping("/get-all-positions")
    public ResponseEntity<ApiResponse<List<PositionResponse>>> getAllPositions() {
        log.info("Getting all positions");
        List<PositionResponse> response = positionService.getAllPositions();
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Positions found successfully", response));
    }

    @GetMapping("/get-position-by-id/{positionId}")
    public ResponseEntity<ApiResponse<PositionResponse>> getPositionById(@PathVariable UUID positionId) {
        log.info("Getting position with id {}", positionId);
        PositionResponse response = positionService.getResponseById(positionId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Position found successfully", response));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @DeleteMapping("/delete-position-by-id/{positionId}")
    public ResponseEntity<ApiResponse<Void>> deletePositionById(@PathVariable UUID positionId) {
        log.info("Deleting position with id {}", positionId);
        positionService.deletePositionById(positionId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Position deleted successfully", null));
    }

}
