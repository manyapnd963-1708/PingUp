package com.chatapp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response body for all API errors.
 *
 * Consistent error format helps frontend clients handle errors predictably.
 * Fields:
 * - timestamp: when the error occurred
 * - status: HTTP status code
 * - error: short error type (e.g., "Not Found")
 * - message: human-readable description
 * - details: field-level validation errors (only present for 400 Bad Request)
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    // Field-level validation errors (present only for validation failures)
    private Map<String, String> details;
}
