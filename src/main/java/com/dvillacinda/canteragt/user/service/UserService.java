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
    public UserEntity createUserEntity(UserCreateRequest user, String firstName, String lastName) {
        UserEntity entity = userMapper.toEntity(user, firstName, lastName);
        entity.setKeycloakId(keycloakUserService.createUser(user, firstName, lastName));
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
        if (request.firstName() != null || request.lastName() != null) {
            String newFirstName = request.firstName() != null ? request.firstName() : existing.getFirstName();
            String newLastName = request.lastName() != null ? request.lastName() : existing.getLastName();
            synchronizeNames(existing, newFirstName, newLastName);
        }
        return userMapper.toResponse(userRepository.saveAndFlush(existing));
    }

    @Transactional
    public void updateNames(UUID userId, String firstName, String lastName) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
        String newFirstName = firstName != null ? firstName : user.getFirstName();
        String newLastName = lastName != null ? lastName : user.getLastName();
        synchronizeNames(user, newFirstName, newLastName);
        userRepository.saveAndFlush(user);
    }

    private void synchronizeNames(UserEntity user, String firstName, String lastName) {
        String previousFirstName = user.getFirstName();
        String previousLastName = user.getLastName();
        keycloakUserService.updateNames(user.getKeycloakId(), firstName, lastName);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        try {
            userRepository.saveAndFlush(user);
        } catch (RuntimeException failure) {
            try {
                keycloakUserService.updateNames(user.getKeycloakId(), previousFirstName, previousLastName);
            } catch (RuntimeException compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
        return userMapper.toResponse(user);
    }
}
