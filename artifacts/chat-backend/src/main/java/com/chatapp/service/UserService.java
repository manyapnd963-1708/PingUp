package com.chatapp.service;

import com.chatapp.dto.response.UserResponse;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.model.User;
import com.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for user profile and presence operations.
 *
 * Scaling Notes:
 * - getUserById is called frequently: add @Cacheable("users") with Redis.
 * - Online status updates: emit to Redis pub/sub channel "user-presence"
 *   instead of writing to DB. Other services subscribe to update their caches.
 * - User search: replace DB LIKE queries with Elasticsearch for full-text search
 *   at scale (millions of users).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    /**
     * Get a user by ID. Throws 404 if not found.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = findUserById(userId);
        return mapToResponse(user);
    }

    /**
     * Search users by username prefix (for contact discovery).
     * At scale: delegate to Elasticsearch service.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(String prefix) {
        logger.debug("Searching users with prefix: {}", prefix);
        return userRepository.searchByUsernamePrefix(prefix)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Update a user's online status.
     * Called when a WebSocket connection is established or closed.
     *
     * Scaling Note: At high traffic, batch these updates or use Redis instead of DB writes.
     */
    @Transactional
    public void updateOnlineStatus(Long userId, boolean online) {
        User user = findUserById(userId);
        user.setOnline(online);
        userRepository.save(user);
        logger.debug("User {} is now {}", user.getUsername(), online ? "online" : "offline");
    }

    /**
     * Internal helper — load a User entity (not DTO) for service-to-service use.
     */
    @Transactional(readOnly = true)
    public User findUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    /**
     * Internal helper — load a User entity by username.
     */
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    /**
     * Map User entity to a public-safe UserResponse DTO.
     * Ensures sensitive fields (password hash) are never serialized to responses.
     */
    public UserResponse mapToResponse(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .avatarUrl(user.getAvatarUrl())
            .online(user.isOnline())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
