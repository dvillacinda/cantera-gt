package com.dvillacinda.canteragt.coach.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.user.dto.UserResponse;

@JsonConfig(namingStrategy = PropertyNamingStrategiesLOWER_CASE_WITH_UNDERSCORES)
public record CoachResponse(
    UUID coachId,
    UserResponse userResponse,
    String firstName,
    String lastName
) {

}
