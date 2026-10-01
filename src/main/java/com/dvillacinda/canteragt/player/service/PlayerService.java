package com.dvillacinda.canteragt.player.service;

import java.util.HashSet;
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
import com.dvillacinda.canteragt.position.repository.PositionRepository;
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
    private final PositionRepository positionRepository;

    public PlayerResponse createPlayer(PlayerCreateRequest request) {
        var user = userService.createUserEntity(request.userCreateRequest(), request.firstName(), request.lastName());
        try {
            userService.assignKeycloakRole(user.getKeycloakId(), "PLAYER");
            PlayerEntity playerEntity = playerMapper.toEntity(request, user);
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
        if (request.firstName() != null || request.lastName() != null) {
            userService.updateNames(existing.getUser().getUserId(), request.firstName(), request.lastName());
        }
        if (request.sex() != null){
            existing.setSex(request.sex());
        }
            
        if (request.principalPositionId() != null){
            PositionEntity position = positionRepository.findById(request.principalPositionId()).orElseThrow(
                () -> new NotFoundException("Position with id " + request.principalPositionId() + " not found")
            );
            existing.setPrincipalPosition(position);
        }

         if (request.secondaryPositionsIds() != null) {

            Set<PositionEntity> positions =
                new HashSet<>(
                    positionRepository.findAllById(
                        request.secondaryPositionsIds()
                    )
                );

            if (positions.size() !=
                request.secondaryPositionsIds().size()) {

                throw new NotFoundException(
                    "One or more positions not found"
                );
            }

            existing.setSecondaryPositions(positions);
        }
        return playerMapper.toResponse(playerRepository.save(existing));
    }

}
