package com.dvillacinda.canteragt.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dvillacinda.canteragt.coach.repository.CoachRepository;
import com.dvillacinda.canteragt.player.repository.PlayerRepository;
import com.dvillacinda.canteragt.user.dto.UserCreateRequest;
import com.dvillacinda.canteragt.user.dto.UserResponse;
import com.dvillacinda.canteragt.user.dto.UserUpdateRequest;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;
import com.dvillacinda.canteragt.user.repository.UserRepository;
import com.dvillacinda.canteragt.auth.service.KeycloakUserService;
import com.dvillacinda.canteragt.shared.exception.ConflictException;
import com.dvillacinda.canteragt.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final CoachRepository coachRepository;
    private final PlayerRepository playerRepository;
    private final KeycloakUserService keycloakUserService;

    @Transactional
    public UserResponse createUser(UserCreateRequest user) {
        return userMapper.toResponse(createUserEntity(user));
    }

    @Transactional
    public UserEntity createUserEntity(UserCreateRequest user) {
        UserEntity entity = userMapper.toEntity(user);
        entity.setKeycloakId(keycloakUserService.createUser(user));
        try {
            return userRepository.saveAndFlush(entity);
        } catch (RuntimeException failure) {
            try {
                keycloakUserService.deleteUser(entity.getKeycloakId());
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            throw failure;
        }
    }

    public void deleteKeycloakUser(String keycloakId) {
        keycloakUserService.deleteUser(keycloakId);
    }

    public void assignKeycloakRole(String keycloakId, String role) {
        keycloakUserService.assignRealmRole(keycloakId, role);
    }

    @Transactional
    public void deleteUserById(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
        if (coachRepository.existsByUser_UserId(userId)) {
            throw new ConflictException("User with id " + userId + " is assigned to a coach, delete the coach instead");
        }
        if (playerRepository.existsByUser_UserId(userId)) {
            throw new ConflictException("User with id " + userId + " is assigned to a player, delete the player instead");
        }
        userRepository.delete(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UserUpdateRequest request) {
        UserEntity existing = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
        if (request.email() != null) existing.setEmail(request.email());
        if (request.username() != null) existing.setUsername(request.username());
        if (request.status() != null) existing.setStatus(request.status());
        return userMapper.toResponse(userRepository.saveAndFlush(existing));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
        return userMapper.toResponse(user);
    }
}
