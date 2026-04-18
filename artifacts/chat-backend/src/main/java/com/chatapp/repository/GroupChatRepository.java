package com.chatapp.repository;

import com.chatapp.model.GroupChat;
import com.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GroupChat operations.
 *
 * Scaling Notes:
 * - For very large groups, member-count queries should be cached in Redis.
 * - Message fanout at scale: use Kafka topics per group, or a hash-based
 *   routing strategy to specific WebSocket server instances via Redis pub/sub.
 */
@Repository
public interface GroupChatRepository extends JpaRepository<GroupChat, Long> {

    /**
     * Find all group chats a given user is a member of.
     * Used to populate the user's inbox.
     */
    @Query("SELECT gc FROM GroupChat gc JOIN gc.members m WHERE m = :user ORDER BY gc.updatedAt DESC")
    List<GroupChat> findAllByMember(@Param("user") User user);

    /**
     * Check if a user is a member of a specific group.
     * At scale: cache this in Redis (SET data structure for O(1) SISMEMBER checks).
     */
    @Query("SELECT COUNT(gc) > 0 FROM GroupChat gc JOIN gc.members m WHERE gc.id = :groupId AND m.id = :userId")
    boolean isUserMember(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
