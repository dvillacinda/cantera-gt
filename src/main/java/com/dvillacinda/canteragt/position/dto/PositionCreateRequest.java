package com.dvillacinda.canteragt.position.dto;

import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.position.enums.PositionLine;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PositionCreateRequest(
    @NotBlank String name,
    @NotNull PositionCode positionCode,
    @NotNull PositionLine positionLine
) {

}
