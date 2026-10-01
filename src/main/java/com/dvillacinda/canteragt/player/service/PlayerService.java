package com.dvillacinda.canteragt.player.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.player.dto.PlayerCreateRequest;
import com.dvillacinda.canteragt.player.dto.PlayerResponse;
import com.dvillacinda.canteragt.player.dto.PlayerUpdateRequest;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.mapper.PlayerMapper;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.service.PositionService;
import com.dvillacinda.canteragt.shared.enums.Status;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PlayerService {
    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final UserService userService;
    private final PositionService positionService;

    public PlayerResponse createPlayer(PlayerCreateRequest request) {
        PositionEntity principalPosition = positionService.getEntityById(request.principalPositionId());
        Set<PositionEntity> secondaryPositions = request.secondaryPositionsIds() == null
                ? Collections.emptySet()
                : positionService.getEntitiesByIds(request.secondaryPositionsIds());
        validatePositions(principalPosition, secondaryPositions);
        var user = userService.createUserEntity(request.userCreateRequest(), request.firstName(), request.lastName());
        try {
            userService.assignKeycloakRole(user.getKeycloakId(), "PLAYER");
            PlayerEntity playerEntity = playerMapper.toEntity(request, user, principalPosition, secondaryPositions);
            PlayerEntity savedPlayer = playerRepository.saveAndFlush(playerEntity);
            return playerMapper.toResponse(savedPlayer);
        } catch (RuntimeException failure) {
            try {
                userService.deleteKeycloakUser(user.getKeycloakId());
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            throw failure;
        }

    }

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
                () -> new NotFoundException("Player with id " + playerId + " not found"));
        return playerMapper.toResponse(player);
    }

    public PlayerResponse updatePlayer(UUID playerId, PlayerUpdateRequest request) {
        PlayerEntity existing = playerRepository.findById(playerId).orElseThrow(
                () -> new NotFoundException("Player with id " + playerId + " not found"));
        PositionEntity finalPrincipal = request.principalPositionId() == null
                ? existing.getPrincipalPosition()
                : positionService.getEntityById(request.principalPositionId());
        Set<PositionEntity> finalSecondary = request.secondaryPositionsIds() == null
                ? existing.getSecondaryPositions()
                : positionService.getEntitiesByIds(request.secondaryPositionsIds());
        validatePositions(finalPrincipal, finalSecondary);

        if (request.firstName() != null || request.lastName() != null) {
            userService.updateNames(existing.getUser().getUserId(), request.firstName(), request.lastName());
        }
        if (request.sex() != null) {
            existing.setSex(request.sex());
        }
        if (request.principalPositionId() != null) {
            existing.setPrincipalPosition(finalPrincipal);
        }
        if (request.secondaryPositionsIds() != null) {
            existing.setSecondaryPositions(finalSecondary);
        }
        return playerMapper.toResponse(playerRepository.save(existing));
    }

    private void validatePositions(PositionEntity principal, Collection<PositionEntity> secondary) {
        if (secondary.stream().anyMatch(position -> position.getPositionId().equals(principal.getPositionId()))) {
            throw new ConflictException("Principal position cannot also be a secondary position");
        }
    }

    public PlayerResponse updatePlayerStatusById(UUID playerId, Status status) {
        PlayerEntity existing = playerRepository.findById(playerId).orElseThrow(
                () -> new NotFoundException("Player with id " + playerId + " not found"));
        userService.updateStatus(existing.getUser().getUserId(), status);
        return playerMapper.toResponse(existing);
    }

}
