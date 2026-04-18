package com.chatapp.controller;

import com.chatapp.dto.response.UserResponse;
import com.chatapp.security.UserPrincipal;
import com.chatapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user-related endpoints.
 *
 * All endpoints require authentication (JWT).
 * The authenticated user is injected via @AuthenticationPrincipal.
 *
 * GET /api/v1/users/me        — get current user profile
 * GET /api/v1/users/{id}      — get any user by ID
 * GET /api/v1/users/search?q= — search users by username prefix
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    /**
     * Get the currently authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        logger.debug("Fetching profile for user: {}", currentUser.getUsername());
        return ResponseEntity.ok(userService.getUserById(currentUser.getId()));
    }

    /**
     * Get any user's public profile by ID.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    /**
     * Search users by username prefix.
     * Used for the "New conversation" contact search feature.
     *
     * GET /api/v1/users/search?q=john
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
        @RequestParam String q
    ) {
        if (q == null || q.trim().length() < 1) {
            return ResponseEntity.badRequest().build();
        }
        logger.debug("User search with query: {}", q);
        return ResponseEntity.ok(userService.searchUsers(q.trim()));
    }
}
