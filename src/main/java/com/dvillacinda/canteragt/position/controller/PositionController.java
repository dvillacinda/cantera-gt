package com.dvillacinda.canteragt.position.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.position.dto.PositionCreateRequest;
import com.dvillacinda.canteragt.position.dto.PositionResponse;
import com.dvillacinda.canteragt.position.service.PositionService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@Slf4j
public class PositionController {

    private final PositionService positionService;

    @PostMapping("/create-position")
    public ResponseEntity<ApiResponse<PositionResponse>> createPosition(
            @Valid @RequestBody PositionCreateRequest request) {
        log.info("Creating position");

        PositionResponse response = positionService.createPosition(request);
        log.info("Position created with id {}", response.positionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Position created successfully", response));
    }

}
