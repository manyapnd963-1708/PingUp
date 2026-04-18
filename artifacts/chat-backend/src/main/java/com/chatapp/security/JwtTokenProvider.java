package com.chatapp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Generates, validates, and parses JWT (JSON Web Tokens).
 *
 * JWT Flow:
 * 1. User logs in → server generates JWT with username as subject.
 * 2. Client stores JWT (localStorage / memory).
 * 3. Client sends JWT in every request: Authorization: Bearer <token>
 * 4. JwtAuthenticationFilter validates the token and sets SecurityContext.
 *
 * Scaling Notes:
 * - JWTs are stateless — no DB lookup per request if the secret is known.
 * - For token revocation (logout, ban): maintain a Redis blacklist (token JTI → expiry).
 *   Check Redis before allowing the request. This is the only stateful part.
 * - For microservices: share the JWT secret via Kubernetes Secret or Vault.
 *   Each service validates tokens independently (no auth service call per request).
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration.ms}")
    private long jwtExpirationMs;

    /**
     * Generate a signed JWT for an authenticated user.
     * The token contains the username as subject and an expiry timestamp.
     */
    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .setSubject(userPrincipal.getUsername())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Generate a JWT directly from a username string.
     * Used after registration to auto-login the new user.
     */
    public String generateTokenFromUsername(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Extract the username (subject) from a JWT.
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }

    /**
     * Validate the JWT signature and expiry.
     * Returns false (instead of throwing) so the filter can handle gracefully.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Derive an HMAC-SHA256 key from the configured secret.
     * Key is derived at runtime — not stored as a field to avoid serialization issues.
     */
    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
