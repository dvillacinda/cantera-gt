package com.dvillacinda.canteragt.position.dto;

import java.util.UUID;

import com.dvillacinda.canteragt.position.enums.PositionCode;
import com.dvillacinda.canteragt.position.enums.PositionLine;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PositionResponse(
    UUID positionId,
    PositionCode positionCode,
    PositionLine positionLine,
    String name

) {

}
