package com.dvillacinda.canteragt.user.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.user.dto.UserCreateRequest;
import com.dvillacinda.canteragt.user.dto.UserResponse;
import com.dvillacinda.canteragt.user.entity.UserEntity;

@Component
public class UserMapper {
    public UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getUserId(),
                user.getKeycloakId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public UserEntity toEntity(UserCreateRequest user, String firstName, String lastName) {
        return UserEntity.builder()
                .email(user.email())
                .username(user.username())
                .firstName(firstName)
                .lastName(lastName)
                .status(user.status())
                .build();
    }
}
