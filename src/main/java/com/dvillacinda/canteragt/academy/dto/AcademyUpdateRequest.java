package com.dvillacinda.canteragt.academy.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AcademyUpdateRequest(
    String name
) {
    
}
