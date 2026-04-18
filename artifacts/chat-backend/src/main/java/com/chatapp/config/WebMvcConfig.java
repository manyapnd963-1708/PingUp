package com.chatapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 *
 * Allows frontend apps (React, Vue, mobile webviews) to call the REST API
 * from different origins during development.
 *
 * IMPORTANT for Production:
 * - Replace "*" with your specific allowed origins (e.g., "https://app.yourdomain.com").
 * - Never allow all origins ("*") in production — this is a security risk.
 * - Use environment variables to configure origins per environment.
 *
 * Scaling Notes:
 * - If running behind a load balancer or API Gateway (AWS ALB, Kong, NGINX),
 *   CORS headers can be handled at the gateway layer instead of the application.
 * - This reduces per-request overhead on application servers.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")  // TODO: Restrict in production
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);  // Cache preflight response for 1 hour
    }
}
