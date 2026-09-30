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
import org.springframework.web.bind.annotation.RestController;

import com.dvillacinda.canteragt.player.dto.PlayerCreateRequest;
import com.dvillacinda.canteragt.player.dto.PlayerResponse;
import com.dvillacinda.canteragt.player.dto.PlayerUpdateRequest;
import com.dvillacinda.canteragt.player.service.PlayerService;
import com.dvillacinda.canteragt.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/player")
@RequiredArgsConstructor
@Slf4j
public class PlayerController {
    private final PlayerService playerService;

    @PostMapping("/create-player")
    public ResponseEntity<ApiResponse<PlayerResponse>> createPlayer(@Valid @RequestBody PlayerCreateRequest player) {
        log.info("Creating player");
        PlayerResponse response = playerService.createPlayer(player);
        log.info("Player created with id {}", response.playerId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, HttpStatus.CREATED, "Player created successfully", response));
    }

    @GetMapping("/get-player/{playerId}")
    public ResponseEntity<ApiResponse<PlayerResponse>> getPlayerById(@PathVariable UUID playerId) {
        log.info("Getting player with id {}", playerId);
        PlayerResponse response = playerService.getPlayerById(playerId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player found successfully", response));
    }

    @PatchMapping("/update-player/{playerId}")
    public ResponseEntity<ApiResponse<PlayerResponse>> updatePlayerById(@PathVariable UUID playerId,
            @Valid @RequestBody PlayerUpdateRequest player) {
        log.info("Updating player with id {}", playerId);
        PlayerResponse response = playerService.updatePlayer(playerId, player);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player updated successfully", response));
    }

    @DeleteMapping("/delete-player/{playerId}")
    public ResponseEntity<ApiResponse<Void>> deletePlayerById(@PathVariable UUID playerId) {
        log.info("Deleting player with id {}", playerId);
        playerService.deletePlayerById(playerId);
        return ResponseEntity.ok(new ApiResponse<>(true, HttpStatus.OK, "Player deleted successfully", null));
    }
}
