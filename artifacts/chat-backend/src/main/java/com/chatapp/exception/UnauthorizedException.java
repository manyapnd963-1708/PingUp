package com.chatapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for business-level authorization failures.
 * Example: a non-member tries to send a message to a group.
 * Maps to HTTP 401 Unauthorized (or use 403 Forbidden for "authenticated but not permitted").
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
