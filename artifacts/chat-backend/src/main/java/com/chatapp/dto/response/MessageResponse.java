package com.chatapp.dto.response;

import com.chatapp.model.MessageStatus;
import com.chatapp.model.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for a chat message.
 * Used for both REST history responses and WebSocket push payloads.
 *
 * The same DTO is sent over WebSocket as JSON when a new message arrives,
 * allowing the frontend to render it consistently regardless of transport.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private String content;
    private MessageStatus status;
    private MessageType type;
    private LocalDateTime createdAt;

    // Sender info (denormalized for frontend convenience)
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;

    // Chat context (one of these will be set)
    private Long directChatId;
    private Long groupChatId;
}
