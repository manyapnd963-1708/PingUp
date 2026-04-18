package com.chatapp.config;

import com.chatapp.security.JwtAuthenticationFilter;
import com.chatapp.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * Security model:
 * - STATELESS: No HTTP sessions (each request authenticated via JWT).
 * - Public endpoints: /api/v1/auth/**, /ws/** (WebSocket handshake)
 * - Protected: everything else requires a valid JWT.
 *
 * Scaling Notes:
 * - STATELESS sessions are essential for horizontal scaling — no session affinity needed.
 * - CSRF disabled because JWTs are not cookie-based (CSRF attacks only affect cookies).
 * - BCrypt cost factor 12 is a good default; increase it as hardware speeds up.
 *   Too high = slow login UX; too low = weaker brute-force resistance.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * BCrypt password encoder. Cost factor 12 is a strong default.
     * Store only the hash — never the plaintext password.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * DAO authentication provider: loads user by username, checks BCrypt hash.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * AuthenticationManager is needed by AuthController to authenticate login requests.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Main security filter chain definition.
     *
     * Route access rules:
     * - /api/v1/auth/**  → public (login, register)
     * - /ws/**           → public (WebSocket upgrade; JWT validated at STOMP level)
     * - everything else  → requires authentication (valid JWT)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless JWT API
            .csrf(AbstractHttpConfigurer::disable)
            // Disable CORS here — configure it in WebMvcConfig for proper origin handling
            .cors(AbstractHttpConfigurer::disable)
            // STATELESS session — no HttpSession created or used
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()        // Login & register
                .requestMatchers("/ws/**").permitAll()                  // WebSocket handshake
                .requestMatchers("/actuator/health").permitAll()        // Health probe for K8s
                .anyRequest().authenticated()                          // Everything else needs JWT
            )
            .authenticationProvider(authenticationProvider())
            // JWT filter runs before Spring's UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
