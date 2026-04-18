package com.chatapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs once per HTTP request.
 *
 * Process:
 * 1. Extract JWT from "Authorization: Bearer <token>" header.
 * 2. Validate the token (signature + expiry).
 * 3. Load the user from DB (or cache).
 * 4. Set the Authentication in SecurityContext.
 * 5. The request proceeds to the controller as an authenticated user.
 *
 * WebSocket note:
 * STOMP connections also carry JWTs — handled separately in WebSocketConfig
 * via ChannelInterceptor (see WebSocketConfig and ChatController).
 *
 * Scaling Note:
 * - Step 3 is the expensive part: DB lookup per request.
 * - Mitigate with Redis cache on UserDetailsService (cache TTL = JWT TTL).
 * - The filter itself is stateless — safe for horizontal scaling.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = "Authorization";

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set in SecurityContext — downstream controllers can access via SecurityContextHolder
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Authenticated user: {}", username);
            }
        } catch (Exception ex) {
            // Don't throw — let the SecurityContext remain empty (unauthenticated)
            // Spring Security will return 401 for protected endpoints
            logger.error("Failed to set user authentication: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT from the Authorization header.
     * Returns null if the header is missing or malformed.
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
