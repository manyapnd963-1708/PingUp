package com.chatapp.service;

import com.chatapp.dto.request.SendMessageRequest;
import com.chatapp.dto.response.MessageResponse;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.*;
import com.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Core message service: persists messages and delivers them via WebSocket.
 *
 * Message flow:
 * 1. Client sends message to /app/chat/direct or /app/chat/group via STOMP.
 * 2. ChatController receives the message and calls this service.
 * 3. This service:
 *    a. Validates authorization (is the sender a member of the chat?).
 *    b. Persists the message to PostgreSQL (status = SENT).
 *    c. Pushes the message to the recipient(s) via SimpMessagingTemplate (WebSocket).
 *    d. Returns the MessageResponse DTO.
 *
 * Scaling with Kafka:
 * Step 3b and 3c would be decoupled with Kafka:
 * - 3b: MessageService publishes a MessageEvent to Kafka topic "chat-messages".
 * - A "persistence-consumer" service writes to PostgreSQL (async, batched).
 * - A "delivery-consumer" service reads from Kafka and pushes to WebSocket instances
 *   via Redis pub/sub (the WebSocket server subscribing to the relevant channel delivers to clients).
 * This separates ingestion throughput from delivery latency.
 *
 * Scaling with Redis pub/sub (without full Kafka):
 * - Replace SimpMessagingTemplate with Redis PUBLISH to a channel (e.g., "chat:group:42").
 * - All WebSocket server instances subscribe to that Redis channel.
 * - Any instance receives the Redis message and delivers to locally connected clients.
 * - This is the minimal change needed to support horizontal scaling of WebSocket servers.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final ChatService chatService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;  // WebSocket sender

    // ==============================
    //  Send Messages
    // ==============================

    /**
     * Send a direct (one-to-one) message.
     * Creates or retrieves the DirectChat between sender and recipient.
     * Persists the message and pushes it to the recipient's WebSocket queue.
     */
    @Transactional
    public MessageResponse sendDirectMessage(Long senderId, SendMessageRequest request) {
        if (request.getRecipientId() == null) {
            throw new IllegalArgumentException("recipientId is required for direct messages");
        }

        User sender = userService.findUserById(senderId);

        // Get or create the direct chat (idempotent)
        chatService.getOrCreateDirectChat(senderId, request.getRecipientId());
        User recipient = userService.findUserById(request.getRecipientId());

        // Re-fetch the actual chat entity (not DTO) for the message FK
        // Canonical ordering: lower ID = user1
        Long user1Id = senderId < request.getRecipientId() ? senderId : request.getRecipientId();
        Long user2Id = senderId < request.getRecipientId() ? request.getRecipientId() : senderId;
        User user1 = userService.findUserById(user1Id);
        User user2 = userService.findUserById(user2Id);
        DirectChat directChat = chatService.findDirectChatByUsers(user1, user2);

        Message message = Message.builder()
            .content(request.getContent())
            .sender(sender)
            .directChat(directChat)
            .status(MessageStatus.SENT)
            .type(request.getType() != null ? request.getType() : MessageType.TEXT)
            .build();

        Message saved = messageRepository.save(message);
        logger.debug("Direct message {} saved from user {} to user {}", saved.getId(), senderId, request.getRecipientId());

        MessageResponse response = mapToResponse(saved);

        // Push to recipient's personal queue via WebSocket
        // Destination: /user/{recipientUsername}/queue/messages
        // The recipient's WebSocket client must be subscribed to /user/queue/messages
        messagingTemplate.convertAndSendToUser(
            recipient.getUsername(),
            "/queue/messages",
            response
        );

        // Also push back to sender (so multi-device sync works)
        messagingTemplate.convertAndSendToUser(
            sender.getUsername(),
            "/queue/messages",
            response
        );

        logger.debug("Direct message {} delivered via WebSocket", saved.getId());
        return response;
    }

    /**
     * Send a message to a group chat.
     * Validates membership, persists, and broadcasts to the group topic.
     */
    @Transactional
    public MessageResponse sendGroupMessage(Long senderId, SendMessageRequest request) {
        if (request.getGroupChatId() == null) {
            throw new IllegalArgumentException("groupChatId is required for group messages");
        }

        if (!chatService.isGroupMember(request.getGroupChatId(), senderId)) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        User sender = userService.findUserById(senderId);
        GroupChat groupChat = chatService.findGroupChatById(request.getGroupChatId());

        Message message = Message.builder()
            .content(request.getContent())
            .sender(sender)
            .groupChat(groupChat)
            .status(MessageStatus.SENT)
            .type(request.getType() != null ? request.getType() : MessageType.TEXT)
            .build();

        Message saved = messageRepository.save(message);
        logger.debug("Group message {} saved to group {} from user {}", saved.getId(), request.getGroupChatId(), senderId);

        MessageResponse response = mapToResponse(saved);

        // Broadcast to all subscribers of this group topic
        // Destination: /topic/group/{groupId}
        // All group members subscribed to this topic receive the message
        messagingTemplate.convertAndSend(
            "/topic/group/" + groupChat.getId(),
            response
        );

        logger.debug("Group message {} broadcast to /topic/group/{}", saved.getId(), groupChat.getId());
        return response;
    }

    // ==============================
    //  Message History (REST)
    // ==============================

    /**
     * Get paginated message history for a direct chat.
     * Newest messages first, page 0 by default.
     *
     * Scaling Note: Large offset-based pagination degrades at scale.
     * Use cursor-based pagination (WHERE id < :lastSeenId LIMIT :size) for better performance.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getDirectChatHistory(Long chatId, int page, int size) {
        Page<Message> messages = messageRepository.findByDirectChatId(
            chatId,
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return messages.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get paginated message history for a group chat.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getGroupChatHistory(Long groupId, int page, int size) {
        Page<Message> messages = messageRepository.findByGroupChatId(
            groupId,
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return messages.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    // ==============================
    //  Message Status
    // ==============================

    /**
     * Mark all messages in a direct chat as DELIVERED for the given user.
     * Called when the recipient connects or opens a chat window.
     */
    @Transactional
    public void markDirectChatAsDelivered(Long chatId, Long userId) {
        int updated = messageRepository.updateStatusForDirectChat(
            chatId, userId, MessageStatus.SENT, MessageStatus.DELIVERED
        );
        logger.debug("Marked {} messages as DELIVERED in chat {} for user {}", updated, chatId, userId);
    }

    // ==============================
    //  Mapping
    // ==============================

    private MessageResponse mapToResponse(Message message) {
        return MessageResponse.builder()
            .id(message.getId())
            .content(message.getContent())
            .status(message.getStatus())
            .type(message.getType())
            .createdAt(message.getCreatedAt())
            .senderId(message.getSender().getId())
            .senderUsername(message.getSender().getUsername())
            .senderDisplayName(message.getSender().getDisplayName())
            .directChatId(message.getDirectChat() != null ? message.getDirectChat().getId() : null)
            .groupChatId(message.getGroupChat() != null ? message.getGroupChat().getId() : null)
            .build();
    }
}
