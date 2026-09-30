package com.dvillacinda.canteragt.shared.dto;

import org.springframework.http.HttpStatus;

public record ApiResponse<T>(
    boolean success,
    HttpStatus status,
    String message,
    T data
) {

}
