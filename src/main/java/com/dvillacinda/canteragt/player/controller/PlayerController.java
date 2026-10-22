package com.dvillacinda.canteragt.player.controller;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.dvillacinda.canteragt.player.dto.PlayerCreateRequest;
import com.dvillacinda.canteragt.player.dto.PlayerResponse;
import com.dvillacinda.canteragt.player.dto.PlayerSelfProfileResponse;
import com.dvillacinda.canteragt.player.dto.PlayerUpdateRequest;
import com.dvillacinda.canteragt.player.service.PlayerService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;
import com.dvillacinda.canteragt.shared.enums.UserStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerController {
    private final PlayerService playerService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ApiResponse<PlayerSelfProfileResponse>> getOwnProfile(Authentication authentication) {
        PlayerSelfProfileResponse response = playerService.getOwnProfile(authentication);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player profile found successfully", response));
    }

    @PostMapping("/create-player")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<PlayerResponse>> createPlayer(@Valid @RequestBody PlayerCreateRequest player) {
        log.info("Creating player");
        PlayerResponse response = playerService.createPlayer(player);
        log.info("Player created with id {}", response.playerId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Player created successfully", response));
    }

    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH')")
    @GetMapping("/get-player-by-id/{playerId}")
    public ResponseEntity<ApiResponse<PlayerResponse>> getPlayerById(@PathVariable UUID playerId,
            @RequestHeader("X-Academy-Id") UUID academyId, Authentication authentication) {
        log.info("Getting player with id {}", playerId);
        PlayerResponse response = playerService.getPlayerById(playerId, academyId, authentication);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player found successfully", response));
    }

    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH')")
    @PatchMapping("/update-player-by-id/{playerId}")
    public ResponseEntity<ApiResponse<PlayerResponse>> updatePlayerById(@PathVariable UUID playerId,
            @RequestHeader("X-Academy-Id") UUID academyId, Authentication authentication,
            @Valid @RequestBody PlayerUpdateRequest player) {
        log.info("Updating player with id {}", playerId);
        PlayerResponse response = playerService.updatePlayer(playerId, player, academyId, authentication);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player updated successfully", response));
    }

    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    @DeleteMapping("/delete-player-by-id/{playerId}")
    public ResponseEntity<ApiResponse<Void>> deletePlayerById(@PathVariable UUID playerId,
            @RequestHeader("X-Academy-Id") UUID academyId, Authentication authentication) {
        log.info("Deleting player with id {}", playerId);
        playerService.deletePlayerById(playerId, academyId, authentication);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player deleted successfully", null));
    }

    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'COACH')")
    @PatchMapping("/update-player-status-by-id/{playerId}/{status}")
    public ResponseEntity<ApiResponse<PlayerResponse>> updatePlayerStatusById(@PathVariable UUID playerId,
            @PathVariable UserStatus status, @RequestHeader("X-Academy-Id") UUID academyId,
            Authentication authentication) {
        log.info("Updating player status with id {}", playerId);
        PlayerResponse response = playerService.updatePlayerStatusById(playerId, status, academyId, authentication);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player status updated successfully", response));
    }
}
