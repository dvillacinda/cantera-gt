package com.dvillacinda.canteragt.coach_role.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.coach_role.enums.CoachRoleCode;

public record CoachRoleResponse(
        UUID coachRoleId,
        String name,
        String description,
        CoachRoleCode coachRoleCode) {

}
