package com.chatapp.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a one-to-one (direct) chat between exactly two users.
 *
 * Design: A DirectChat is created the first time two users exchange a message,
 * and persists thereafter (even if both delete messages). This is the Telegram model.
 *
 * Scaling Notes:
 * - Indexed on (user1_id, user2_id) for fast lookup when checking if a chat exists.
 * - Messages are fetched with pagination (never load all messages at once).
 * - With Kafka: each incoming message triggers a Kafka event that fan-outs to the
 *   recipient's WebSocket session, push notification service, and unread-counter service.
 */
@Entity
@Table(
    name = "direct_chats",
    uniqueConstraints = {
        // Ensure only ONE chat exists per pair of users
        @UniqueConstraint(name = "uq_direct_chat_users", columnNames = {"user1_id", "user2_id"})
    },
    indexes = {
        @Index(name = "idx_direct_chat_user1", columnList = "user1_id"),
        @Index(name = "idx_direct_chat_user2", columnList = "user2_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * user1 is always the user with the lower ID (enforced in service layer).
     * This prevents duplicate chats like (user1=A, user2=B) and (user1=B, user2=A).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    /**
     * Messages in this direct chat.
     * LAZY + pagination prevents loading millions of messages into memory.
     */
    @OneToMany(mappedBy = "directChat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
