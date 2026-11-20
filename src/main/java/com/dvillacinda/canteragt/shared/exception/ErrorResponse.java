package com.dvillacinda.canteragt.shared.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Error body returned by every endpoint. {@code field_errors} maps a request field (in the snake_case the client
 * sends, e.g. {@code player_ids} or {@code results[0].raw_value}) or a rejected item id to its message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ErrorResponse(
    int status,
    String error,
    String message,
    Map<String, String> fieldErrors
) {

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
