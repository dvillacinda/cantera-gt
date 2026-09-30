package com.dvillacinda.canteragt.shared.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
