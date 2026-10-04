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
import com.dvillacinda.canteragt.shared.enums.UserStatus;
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

    public boolean hasKeycloakRealmRole(String keycloakId, String role) {
        return keycloakUserService.hasRealmRole(keycloakId, role);
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
        keycloakUserService.deleteUser(user.getKeycloakId());
        userRepository.delete(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UserUpdateRequest request) {
        UserEntity existing = findById(userId);
        synchronize(existing,
                request.email() != null ? request.email() : existing.getEmail(),
                request.firstName() != null ? request.firstName() : existing.getFirstName(),
                request.lastName() != null ? request.lastName() : existing.getLastName(),
                existing.getStatus());
        return userMapper.toResponse(existing);
    }

    @Transactional
    public void updateNames(UUID userId, String firstName, String lastName) {
        UserEntity user = findById(userId);
        synchronize(user, user.getEmail(),
                firstName != null ? firstName : user.getFirstName(),
                lastName != null ? lastName : user.getLastName(),
                user.getStatus());
    }

    /** Disables the Keycloak account when the status does not allow logging in (INACTIVE, LOCKED). */
    @Transactional
    public UserResponse updateStatus(UUID userId, UserStatus status) {
        UserEntity user = findById(userId);
        synchronize(user, user.getEmail(), user.getFirstName(), user.getLastName(), status);
        return userMapper.toResponse(user);
    }

    private UserEntity findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
    }

    /** Applies the change in Keycloak first and reverts it there if the database write fails. */
    private void synchronize(UserEntity user, String email, String firstName, String lastName, UserStatus status) {
        String previousEmail = user.getEmail();
        String previousFirstName = user.getFirstName();
        String previousLastName = user.getLastName();
        UserStatus previousStatus = user.getStatus();
        keycloakUserService.updateUser(user.getKeycloakId(), email, firstName, lastName, status.canLogIn());
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setStatus(status);
        try {
            userRepository.saveAndFlush(user);
        } catch (RuntimeException failure) {
            try {
                keycloakUserService.updateUser(user.getKeycloakId(), previousEmail, previousFirstName,
                        previousLastName, previousStatus.canLogIn());
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
