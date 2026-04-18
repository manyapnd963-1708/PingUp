package com.chatapp.config;

import com.chatapp.security.JwtTokenProvider;
import com.chatapp.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration.
 *
 * STOMP Protocol:
 * - Client connects: ws://host/ws (with optional SockJS fallback)
 * - Client subscribes: /topic/group/{groupId} or /user/queue/messages (direct)
 * - Client sends: /app/chat/direct or /app/chat/group
 * - Server broadcasts: SimpMessagingTemplate.convertAndSend(destination, payload)
 *
 * Scaling Notes:
 * - The simple in-memory broker works for a single server instance.
 * - For multiple instances: replace with a full-featured broker (RabbitMQ or ActiveMQ)
 *   using enableStompBrokerRelay(). This is the key to horizontal scaling of WebSockets.
 *   Multiple server instances connect to the shared broker — any instance can receive
 *   a message and any instance can deliver it to connected clients.
 * - Kafka integration: Service publishes messages to Kafka, a WebSocket delivery service
 *   consumes from Kafka and pushes to connected clients. This decouples ingestion from delivery.
 *
 * JWT in WebSocket:
 * - STOMP CONNECT frame carries JWT in the "Authorization" header.
 * - The ChannelInterceptor below validates it and sets the user principal.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        /**
         * In-memory simple broker for topic-based and user-specific messaging.
         *
         * To scale to multiple instances, replace with:
         * config.enableStompBrokerRelay("/topic", "/queue")
         *       .setRelayHost("rabbitmq-host")
         *       .setRelayPort(61613)
         *       .setClientLogin("guest")
         *       .setClientPasscode("guest");
         */
        config.enableSimpleBroker("/topic", "/queue");

        // All messages from clients go to @MessageMapping methods with /app prefix
        config.setApplicationDestinationPrefixes("/app");

        // For user-specific messages (DMs): /user/{userId}/queue/messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            // Allow all origins in development. Restrict in production.
            .setAllowedOriginPatterns("*")
            // SockJS fallback for browsers that don't support native WebSocket
            .withSockJS();
    }

    /**
     * JWT Authentication interceptor for WebSocket CONNECT frames.
     * Validates JWT and sets the authenticated user principal on the STOMP session.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);

                        if (tokenProvider.validateToken(token)) {
                            String username = tokenProvider.getUsernameFromToken(token);
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                                );
                            accessor.setUser(auth);
                            logger.debug("WebSocket authenticated user: {}", username);
                        } else {
                            logger.warn("Invalid JWT in WebSocket CONNECT");
                        }
                    }
                }
                return message;
            }
        });
    }
}
