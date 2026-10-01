package com.dvillacinda.canteragt.player.mapper;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.player.dto.PlayerCreateRequest;
import com.dvillacinda.canteragt.player.dto.PlayerResponse;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.position.repository.PositionRepository;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlayerMapper {
    private final UserMapper userMapper;
    private final PositionRepository positionRepository;

    public PlayerResponse toResponse(PlayerEntity player) {
        return new PlayerResponse(
                player.getPlayerId(),
                userMapper.toResponse(player.getUser()),
                player.getUser().getFirstName(),
                player.getUser().getLastName(),
                player.getBirthDate(),
                player.getSex(),
                player.getPrincipalPosition(),
                player.getSecondaryPositions(),
                player.getCreatedAt(),
                player.getUpdatedAt());
    }

    public PlayerEntity toEntity(PlayerCreateRequest player, UserEntity user) {
        var position = positionRepository.findById(player.principalPositionId())
                .orElseThrow(() -> new NotFoundException("Position not found"));
        var positions = player.secondaryPositionsIds().stream().map(
                id -> positionRepository.findById(id).orElseThrow(() -> new NotFoundException("Position not found")))
                .collect(Collectors.toSet());

        return PlayerEntity.builder()
                .user(user)
                .birthDate(player.birthDate())
                .sex(player.sex())
                .principalPosition(position)
                .secondaryPositions(positions)
                .build();
    }

}
