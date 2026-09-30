package com.dvillacinda.canteragt.player.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.player.dto.PlayerCreateRequest;
import com.dvillacinda.canteragt.player.dto.PlayerResponse;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlayerMapper {
    private final UserMapper userMapper;

    public PlayerResponse toResponse(PlayerEntity player) {
        return new PlayerResponse(
                player.getPlayerId(),
                userMapper.toResponse(player.getUser()),
                player.getFirstName(),
                player.getLastName(),
                player.getBirthDate(),
                player.getSex(),
                player.getPrincipalPositionId(),
                player.getSecondaryPosition(),
                player.getCreatedAt(),
                player.getUpdatedAt()
        );
    }

    public PlayerEntity toEntity(PlayerCreateRequest player){
        return toEntity(player, userMapper.toEntity(player.userCreateRequest()));
    }

    public PlayerEntity toEntity(PlayerCreateRequest player, UserEntity user){
        return PlayerEntity.builder()
                .user(user)
                .firstName(player.firstName())
                .lastName(player.lastName())
                .birthDate(player.birthDate())
                .sex(player.sex())
                .principalPositionId(player.principalPositionId())
                .secondaryPosition(player.secondaryPosition())
                .build();
    }



}
