package com.dvillacinda.canteragt.coach_role.dto;

import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;

public record CoachRoleUpdateRequest(
        String name,
        String description,
        CoachRoleCode coachRoleCode) {

}
