package com.example.demo.interfaces.api.error;

import java.time.Instant;
import java.util.Map;

/**
 * API-layer DTO used to serialize error payloads.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, Object> details
) {
    /**
     * Factory method that populates the common error attributes using the current timestamp.
     *
     * @param status  HTTP status code
     * @param error   stable error code
     * @param message human readable explanation
     * @param path    request path that produced the error
     * @return populated response object
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }
}
