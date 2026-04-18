package com.chatapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for user registration requests.
 *
 * DTOs (Data Transfer Objects) decouple the API contract from the domain model.
 * This prevents accidental exposure of internal fields (e.g., password hash, IDs)
 * and allows the API shape to evolve independently of the database schema.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;
}
