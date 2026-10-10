package com.dvillacinda.canteragt.shared.dto;

import org.springframework.http.HttpStatus;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiResponse<T>(
    boolean success,
    HttpStatus status,
    String message,
    T data
) {

}
