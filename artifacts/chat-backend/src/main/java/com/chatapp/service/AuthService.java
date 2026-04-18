package com.chatapp.service;

import com.chatapp.dto.request.LoginRequest;
import com.chatapp.dto.request.RegisterRequest;
import com.chatapp.dto.response.AuthResponse;
import com.chatapp.exception.DuplicateResourceException;
import com.chatapp.model.User;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.JwtTokenProvider;
import com.chatapp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling user authentication: registration and login.
 *
 * SOLID Principles applied:
 * - Single Responsibility: only handles auth (registration + login).
 * - Open/Closed: extend auth methods (e.g., OAuth) without modifying this class.
 * - Dependency Inversion: depends on interfaces (UserRepository, PasswordEncoder),
 *   not concrete implementations.
 *
 * Scaling Notes:
 * - Registration is a low-frequency operation — no caching needed.
 * - Login: validate against DB, then issue stateless JWT.
 *   No session stored server-side = unlimited horizontal scaling.
 * - For rate limiting (brute-force protection): use Redis (INCR + EXPIRE per IP/username).
 * - For OAuth2 (Google, GitHub login): add an OAuth2 provider config and integrate here.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    /**
     * Register a new user.
     * Validates uniqueness of username and email before persisting.
     * Password is hashed with BCrypt before storage.
     *
     * @throws DuplicateResourceException if username or email is already taken
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        logger.info("Registering new user: {}", request.getUsername());

        // Check uniqueness before attempting insert to give a clear error message
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username", "username", request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email", "email", request.getEmail());
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))  // BCrypt hash
            .displayName(request.getDisplayName() != null
                ? request.getDisplayName()
                : request.getUsername())
            .online(false)
            .build();

        userRepository.save(user);
        logger.info("User registered successfully: {}", user.getUsername());

        // Auto-login: generate JWT so the client can immediately use the API
        String token = tokenProvider.generateTokenFromUsername(user.getUsername());

        return AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .userId(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .build();
    }

    /**
     * Authenticate a user and return a JWT.
     * Delegates credential verification to Spring Security's AuthenticationManager.
     *
     * @throws org.springframework.security.core.AuthenticationException on bad credentials
     */
    public AuthResponse login(LoginRequest request) {
        logger.info("Login attempt for user: {}", request.getUsername());

        // Spring Security validates credentials against the DB via UserDetailsService
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // Store in SecurityContext (useful for method-level security in the same request)
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = tokenProvider.generateToken(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        logger.info("Login successful for user: {}", userPrincipal.getUsername());

        return AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .userId(userPrincipal.getId())
            .username(userPrincipal.getUsername())
            .email(userPrincipal.getEmail())
            .build();
    }
}
