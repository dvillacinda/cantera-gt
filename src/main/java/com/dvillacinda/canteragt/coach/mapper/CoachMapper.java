package com.dvillacinda.canteragt.coach.mapper;

import org.springframework.stereotype.Component;

import com.dvillacinda.canteragt.coach.dto.CoachCreateRequest;
import com.dvillacinda.canteragt.coach.dto.CoachResponse;
import com.dvillacinda.canteragt.coach.entity.CoachEntity;
import com.dvillacinda.canteragt.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CoachMapper {
    private final UserMapper userMapper;

    public CoachResponse toResponse(CoachEntity coach) {
        return new CoachResponse(
                coach.getCoachId(),
                userMapper.toResponse(coach.getUser()));
    }

    public CoachEntity toEntity(CoachCreateRequest coach) {
        return CoachEntity.builder()
                .user(userMapper.toEntity(coach.userCreateRequest(), coach.firstName(), coach.lastName()))
                .build();
    }

    public CoachEntity toEntity(CoachResponse coach) {

        var userEntity = userMapper.toEntity(coach.userResponse());
        return CoachEntity.builder()
                .coachId(coach.coachId())
                .user(userEntity)
                .build();
    }
}
