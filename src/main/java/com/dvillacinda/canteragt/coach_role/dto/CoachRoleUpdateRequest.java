package com.dvillacinda.canteragt.coach_role.dto;

import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CoachRoleUpdateRequest(
        String name,
        String description,
        CoachRoleCode coachRoleCode) {

}
