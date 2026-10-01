package com.dvillacinda.canteragt.player.mapper;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.player.dto.PlayerCreateRequest;
import com.dvillacinda.canteragt.player.dto.PlayerResponse;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.position.entity.PositionEntity;
import com.dvillacinda.canteragt.position.mapper.PositionMapper;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlayerMapper {
    private final UserMapper userMapper;
    private final PositionMapper positionMapper;

    public PlayerResponse toResponse(PlayerEntity player) {
        return new PlayerResponse(
                player.getPlayerId(),
                userMapper.toResponse(player.getUser()),
                player.getBirthDate(),
                player.getSex(),
                positionMapper.toResponse(player.getPrincipalPosition()),
                player.getSecondaryPositions().stream()
                        .map(positionMapper::toResponse)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                player.getCreatedAt(),
                player.getUpdatedAt());
    }

    public PlayerEntity toEntity(PlayerCreateRequest player, UserEntity user, PositionEntity principalPosition,
            Set<PositionEntity> secondaryPositions) {
        return PlayerEntity.builder()
                .user(user)
                .birthDate(player.birthDate())
                .sex(player.sex())
                .principalPosition(principalPosition)
                .secondaryPositions(secondaryPositions)
                .build();
    }

}
