package com.dvillacinda.canteragt.shared.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * A batch request was rejected as a whole. {@code rejected} maps each offending item id to a machine-readable
 * reason and is returned to the client in {@link ErrorResponse#fieldErrors()}.
 */
public class RejectedItemsException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, String> rejected;

    public RejectedItemsException(HttpStatus status, String message, Map<String, String> rejected) {
        super(message);
        this.status = status;
        this.rejected = Map.copyOf(rejected);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getRejected() {
        return rejected;
    }
}
