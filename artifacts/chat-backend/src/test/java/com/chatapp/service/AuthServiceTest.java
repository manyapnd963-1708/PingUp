package com.chatapp.service;

import com.chatapp.dto.request.LoginRequest;
import com.chatapp.dto.request.RegisterRequest;
import com.chatapp.dto.response.AuthResponse;
import com.chatapp.exception.DuplicateResourceException;
import com.chatapp.model.User;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.JwtTokenProvider;
import com.chatapp.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 *
 * Uses Mockito to mock all dependencies:
 * - No database, no Spring context, no network needed.
 * - Fast (milliseconds), deterministic, isolated.
 *
 * Test naming convention: methodName_StateUnderTest_ExpectedBehavior
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setUsername("testuser");
        validRegisterRequest.setEmail("test@example.com");
        validRegisterRequest.setPassword("password123");
        validRegisterRequest.setDisplayName("Test User");

        validLoginRequest = new LoginRequest();
        validLoginRequest.setUsername("testuser");
        validLoginRequest.setPassword("password123");
    }

    // ==============================
    //  Registration Tests
    // ==============================

    @Test
    @DisplayName("register - valid request - returns AuthResponse with token")
    void register_ValidRequest_ReturnsAuthResponseWithToken() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            // Simulate DB assigning an ID
            return User.builder()
                .id(1L)
                .username(u.getUsername())
                .email(u.getEmail())
                .password(u.getPassword())
                .displayName(u.getDisplayName())
                .build();
        });
        when(tokenProvider.generateTokenFromUsername("testuser")).thenReturn("jwt-token-123");

        // Act
        AuthResponse result = authService.register(validRegisterRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("jwt-token-123");
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");

        // Verify password was hashed (not stored in plain text)
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register - duplicate username - throws DuplicateResourceException")
    void register_DuplicateUsername_ThrowsDuplicateResourceException() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(validRegisterRequest))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Username");

        // Verify user was NOT saved
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register - duplicate email - throws DuplicateResourceException")
    void register_DuplicateEmail_ThrowsDuplicateResourceException() {
        // Arrange
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(validRegisterRequest))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Email");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register - no display name - uses username as display name")
    void register_NoDisplayName_UsesUsernameAsDisplayName() {
        // Arrange
        validRegisterRequest.setDisplayName(null);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder()
                .id(1L)
                .username(u.getUsername())
                .email(u.getEmail())
                .password(u.getPassword())
                .displayName(u.getDisplayName())
                .build();
        });
        when(tokenProvider.generateTokenFromUsername(any())).thenReturn("token");

        // Act
        authService.register(validRegisterRequest);

        // Assert — verify saved user has username as displayName
        verify(userRepository).save(argThat(user ->
            "testuser".equals(user.getDisplayName())
        ));
    }

    // ==============================
    //  Login Tests
    // ==============================

    @Test
    @DisplayName("login - valid credentials - returns AuthResponse with token")
    void login_ValidCredentials_ReturnsAuthResponseWithToken() {
        // Arrange
        UserPrincipal userPrincipal = new UserPrincipal(1L, "testuser", "test@example.com", "hashed");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userPrincipal, null, Collections.emptyList()
        );
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token-abc");

        // Act
        AuthResponse result = authService.login(validLoginRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("jwt-token-abc");
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("login - bad credentials - propagates AuthenticationException")
    void login_BadCredentials_PropagatesAuthenticationException() {
        // Arrange
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(validLoginRequest))
            .isInstanceOf(BadCredentialsException.class);
    }
}
