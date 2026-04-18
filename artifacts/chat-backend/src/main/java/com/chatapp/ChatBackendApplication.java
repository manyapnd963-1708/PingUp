package com.chatapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point for the Chat Backend Application.
 *
 * Architecture Overview:
 * ┌──────────────┐    ┌──────────────┐    ┌───────────────┐    ┌────────────┐
 * │  Controller  │───▶│   Service    │───▶│  Repository   │───▶│ PostgreSQL │
 * │  (REST/WS)   │    │  (Business)  │    │  (JPA/ORM)    │    │    DB      │
 * └──────────────┘    └──────────────┘    └───────────────┘    └────────────┘
 *        │                    │
 *   JWT Filter           Domain Models
 *   Spring Security      (User, Chat, Message)
 *
 * Scaling Path:
 * - Add Kafka between Service and downstream consumers (message fanout to millions)
 * - Add Redis for WebSocket session sharing across multiple instances
 * - Kubernetes HPA scales stateless pods based on CPU/connection load
 */
@SpringBootApplication
@EnableJpaAuditing  // Enables automatic createdAt/updatedAt population
public class ChatBackendApplication {

    private static final Logger logger = LoggerFactory.getLogger(ChatBackendApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ChatBackendApplication.class, args);
        logger.info("Chat Backend Application started successfully");
        logger.info("WebSocket endpoint: /ws (STOMP)");
        logger.info("REST API base: /api/v1");
    }
}
