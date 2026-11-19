package com.dvillacinda.canteragt.training_session.dto;

import com.dvillacinda.canteragt.training_session.enums.AttendanceStatus;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AttendanceUpdateRequest(@NotNull AttendanceStatus attendanceStatus) {
}
