package com.dvillacinda.canteragt.player.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.player.dto.PlayerCreateRequest;
import com.dvillacinda.canteragt.player.dto.PlayerResponse;
import com.dvillacinda.canteragt.player.dto.PlayerUpdateRequest;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.mapper.PlayerMapper;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlayerService {
    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final UserService userService;

    @Transactional
    public PlayerResponse createPlayer(PlayerCreateRequest player) {
        var user = userService.createUserEntity(player.userCreateRequest());
        PlayerEntity playerEntity = playerMapper.toEntity(player, user);
        PlayerEntity savedPlayer = playerRepository.save(playerEntity);
        return playerMapper.toResponse(savedPlayer);

    }

    @Transactional
    public void deletePlayerById(UUID playerId) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow(
                () -> new NotFoundException("Player with id " + playerId + " not found"));
        UUID userId = player.getUser().getUserId();
        playerRepository.delete(player);
        playerRepository.flush();
        userService.deleteUserById(userId);
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayerById(UUID playerId) {
        PlayerEntity player = playerRepository.findById(playerId).orElseThrow(
            () -> new NotFoundException("Player with id "+playerId+ " not found")
        );
        return playerMapper.toResponse(player);
    }

    @Transactional
    public PlayerResponse updatePlayer(UUID playerId, PlayerUpdateRequest request) {
        PlayerEntity existing = playerRepository.findById(playerId).orElseThrow(
                () -> new NotFoundException("Player with id " + playerId + " not found"));
        if (request.firstName() != null) existing.setFirstName(request.firstName());
        if (request.lastName() != null) existing.setLastName(request.lastName());
        if (request.sex() != null) existing.setSex(request.sex());
        if (request.principalPosition() != null) existing.setPrincipalPosition(request.principalPosition());
        if (request.secondaryPosition() != null) {
            existing.setSecondaryPosition(request.secondaryPosition().isBlank() ? null : request.secondaryPosition());
        }
        return playerMapper.toResponse(playerRepository.save(existing));
    }

}
