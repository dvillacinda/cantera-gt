package com.dvillacinda.canteragt.auth.service;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.auth.dto.AuthenticatedUser;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse.CoachProfile;
import com.dvillacinda.canteragt.auth.dto.CurrentUserResponse.PlayerProfile;
import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.player.entity.PlayerEntity;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.position.mapper.PositionMapper;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;
import com.dvillacinda.canteragt.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final CoachRepository coachRepository;
    private final UserMapper userMapper;
    private final PositionMapper positionMapper;

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(AuthenticatedUser authenticated) {
        return userRepository.findByKeycloakId(authenticated.keycloakId())
                .map(user -> toResponse(authenticated, user))
                .orElseGet(() -> new CurrentUserResponse(authenticated.roles(), null, null, null));
    }

    private CurrentUserResponse toResponse(AuthenticatedUser authenticated, UserEntity user) {
        PlayerProfile player = playerRepository.findByUser_UserId(user.getUserId())
                .map(this::toPlayerProfile)
                .orElse(null);
        CoachProfile coach = coachRepository.findByUser_UserId(user.getUserId())
                .map(found -> new CoachProfile(found.getCoachId()))
                .orElse(null);
        return new CurrentUserResponse(authenticated.roles(), userMapper.toResponse(user), player, coach);
    }

    private PlayerProfile toPlayerProfile(PlayerEntity player) {
        return new PlayerProfile(
                player.getPlayerId(),
                player.getBirthDate(),
                player.getSex(),
                positionMapper.toResponse(player.getPrincipalPosition()),
                player.getSecondaryPositions().stream()
                        .map(positionMapper::toResponse)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
