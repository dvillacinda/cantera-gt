package com.dvillacinda.canteragt.coach.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.user.dto.UserResponse;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoachResponse(
    UUID coachId,
    UserResponse userResponse
) {

}
