package com.chatapp.controller;

import com.chatapp.dto.request.LoginRequest;
import com.chatapp.dto.request.RegisterRequest;
import com.chatapp.dto.response.AuthResponse;
import com.chatapp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * Public endpoints (no JWT required):
 * POST /api/v1/auth/register  — create a new user account
 * POST /api/v1/auth/login     — authenticate and get a JWT
 *
 * @Valid triggers bean validation on the request body (NotBlank, Size, Email, etc.)
 * Validation errors are caught by GlobalExceptionHandler and returned as 400 Bad Request.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    /**
     * Register a new user.
     * Returns JWT and user info for immediate auto-login.
     *
     * POST /api/v1/auth/register
     * Body: { username, email, password, displayName? }
     * Response: { accessToken, tokenType, userId, username, email, displayName }
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Registration request for username: {}", request.getUsername());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate and get a JWT.
     *
     * POST /api/v1/auth/login
     * Body: { username, password }
     * Response: { accessToken, tokenType, userId, username, email }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login request for username: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
