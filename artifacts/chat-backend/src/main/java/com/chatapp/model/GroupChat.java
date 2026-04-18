package com.chatapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a group chat with multiple members.
 *
 * Many-to-Many: GroupChat <-> User (via the group_chat_members join table).
 * One-to-Many:  GroupChat -> Message.
 *
 * Scaling Notes:
 * - For very large groups (100k+ members, like Telegram channels), the fanout
 *   problem is significant. Use Kafka: publish one message to a topic, and let
 *   consumer workers deliver to sharded WebSocket server instances.
 * - Member list can be cached in Redis (Set data structure) for O(1) membership checks.
 * - For analytics: emit events to a separate analytics Kafka topic (don't query the DB).
 */
@Entity
@Table(
    name = "group_chats",
    indexes = {
        @Index(name = "idx_group_chat_admin", columnList = "admin_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String avatarUrl;

    /**
     * The user who created and administers this group.
     * Extension: Add a role-based system (ADMIN, MODERATOR, MEMBER).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /**
     * Group members — Many-to-Many with User.
     * The join table 'group_chat_members' stores (group_chat_id, user_id).
     */
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "group_chat_members",
        joinColumns = @JoinColumn(name = "group_chat_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"),
        indexes = {
            @Index(name = "idx_gcm_group", columnList = "group_chat_id"),
            @Index(name = "idx_gcm_user", columnList = "user_id")
        }
    )
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @OneToMany(mappedBy = "groupChat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
