package com.dvillacinda.canteragt.season.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.season.dto.SeasonCreateRequest;
import com.dvillacinda.canteragt.season.dto.SeasonResponse;
import com.dvillacinda.canteragt.season.service.SeasonService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/seasons")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ACADEMY_ADMIN')")
public class SeasonController {
    private final SeasonService seasonService;

    @PostMapping("/create-season")
    public ResponseEntity<ApiResponse<SeasonResponse>> createSeason(
            @RequestBody @Validated SeasonCreateRequest request) {
        log.info("Create season");

        SeasonResponse response = seasonService.createSeason(request);
        log.info("Season created with id {}", response.seasonId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Season created successfully", response));
    }

    @GetMapping ("/get-season-by-id/{seasonId}")
    public ResponseEntity<ApiResponse<SeasonResponse>> getSeasonById(@PathVariable UUID seasonId) {
        log.info("Getting season with id {}", seasonId);
        SeasonResponse response = seasonService.getSeasonById(seasonId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Season found successfully", response));
    }

}
