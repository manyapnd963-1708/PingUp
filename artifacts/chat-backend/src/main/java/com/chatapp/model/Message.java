package com.chatapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Represents a chat message — the core entity of the system.
 *
 * Design Decision:
 * - One Message entity for both direct messages and group messages.
 *   Discriminated by the presence of groupChat vs directChat.
 * - MessageStatus tracks delivery lifecycle.
 *
 * Scaling Notes:
 * - At millions of messages/day, partition this table by created_at (range partitioning).
 * - Archive old messages to cold storage (S3 + Athena, Cassandra, or TimescaleDB).
 * - With Kafka: the Service publishes a MessageEvent to a Kafka topic on send.
 *   Consumers (notification service, delivery service) subscribe independently.
 *   This decouples real-time delivery from persistence.
 */
@Entity
@Table(
    name = "messages",
    indexes = {
        @Index(name = "idx_msg_direct_chat", columnList = "direct_chat_id"),
        @Index(name = "idx_msg_group_chat", columnList = "group_chat_id"),
        @Index(name = "idx_msg_sender", columnList = "sender_id"),
        @Index(name = "idx_msg_created_at", columnList = "created_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 4096)
    @Column(nullable = false, length = 4096)
    private String content;

    /**
     * The user who sent this message.
     * LAZY loading prevents N+1 queries when listing messages.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /**
     * Set for direct (one-to-one) messages.
     * Null if this is a group message.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_chat_id")
    private DirectChat directChat;

    /**
     * Set for group messages.
     * Null if this is a direct message.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_chat_id")
    private GroupChat groupChat;

    /**
     * Message delivery lifecycle:
     * SENT     → saved to DB, delivered to WebSocket broker
     * DELIVERED → recipient(s) received it via WebSocket subscription
     *
     * Extension: Add READ status when recipient opens the chat (read receipts).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Soft delete: set to true instead of deleting records */
    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;
}
