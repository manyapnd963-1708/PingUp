package com.chatapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an application user.
 *
 * Scaling Notes:
 * - For millions of users, consider sharding by user_id ranges.
 * - Add a Redis cache layer (e.g., @Cacheable) to avoid DB hits on every JWT validation.
 * - The 'online' status can be stored in Redis (TTL-based) instead of PostgreSQL
 *   to reduce write pressure. Each heartbeat from the client updates Redis TTL.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_username", columnList = "username"),
        @Index(name = "idx_user_email", columnList = "email")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Password is stored as a BCrypt hash — never stored in plain text.
     * BCrypt cost factor is configured in SecurityConfig.
     */
    @NotBlank
    @Column(nullable = false)
    private String password;

    @Size(max = 255)
    @Column(length = 255)
    private String displayName;

    @Column(length = 500)
    private String avatarUrl;

    /**
     * Online status. For production: store in Redis with TTL
     * instead of writing to PostgreSQL on every heartbeat.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean online = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Many-to-Many relationship: Users <-> Groups.
     * A user can be in multiple group chats.
     * Mapped on the GroupChat side (inverse side here).
     */
    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<GroupChat> groupChats = new HashSet<>();
}
