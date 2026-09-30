package com.dvillacinda.canteragt.coach.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.coach.dto.CoachCreateRequest;
import com.dvillacinda.canteragt.coach.dto.CoachResponse;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.user.entity.UserEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CoachMapper {
    private final UserMapper userMapper;

    public CoachResponse toResponse(CoachEntity coach) {
        return new CoachResponse(
                coach.getCoachId(),
                userMapper.toResponse(coach.getUser()),
                coach.getFirstName(),
                coach.getLastName());
    }

    public CoachEntity toEntity(CoachCreateRequest coach) {
        return toEntity(coach, userMapper.toEntity(coach.userCreateRequest()));
    }

    public CoachEntity toEntity(CoachCreateRequest coach, UserEntity user) {
        return CoachEntity.builder()
                .user(user)
                .firstName(coach.firstName())
                .lastName(coach.lastName())
                .build();
    }
}
