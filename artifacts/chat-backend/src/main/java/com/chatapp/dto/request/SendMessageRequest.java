package com.chatapp.dto.request;

import com.chatapp.model.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for sending a chat message via WebSocket (STOMP) or REST.
 *
 * This DTO is used as the STOMP message payload (JSON-serialized).
 * The sender is extracted from the authenticated WebSocket principal,
 * NOT from this DTO, to prevent spoofing.
 */
@Data
public class SendMessageRequest {

    @NotBlank(message = "Content is required")
    @Size(max = 4096, message = "Message content must not exceed 4096 characters")
    private String content;

    /**
     * For direct messages: the ID of the recipient user.
     * Null for group messages.
     */
    private Long recipientId;

    /**
     * For group messages: the ID of the target group.
     * Null for direct messages.
     */
    private Long groupChatId;

    private MessageType type = MessageType.TEXT;
}
