package com.chatapp.repository;

import com.chatapp.model.Message;
import com.chatapp.model.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Message operations.
 *
 * CRITICAL: Always use pagination (Pageable) when fetching messages.
 * Never load all messages for a chat — this causes OOM at scale.
 *
 * Scaling Notes:
 * - Partition the messages table by created_at (monthly range partitions in PostgreSQL).
 * - Archive messages older than N days to cold storage (S3 + Parquet/Athena).
 * - Use a read replica for message history queries to offload the primary DB.
 * - With Kafka: message persistence is a consumer of the "messages" Kafka topic,
 *   not a synchronous DB write in the request path.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Paginated message history for a direct chat.
     * Default: newest-first (pass Pageable with Sort.by("createdAt").descending()).
     */
    @Query("SELECT m FROM Message m WHERE m.directChat.id = :chatId AND m.deleted = false ORDER BY m.createdAt DESC")
    Page<Message> findByDirectChatId(@Param("chatId") Long chatId, Pageable pageable);

    /**
     * Paginated message history for a group chat.
     */
    @Query("SELECT m FROM Message m WHERE m.groupChat.id = :groupId AND m.deleted = false ORDER BY m.createdAt DESC")
    Page<Message> findByGroupChatId(@Param("groupId") Long groupId, Pageable pageable);

    /**
     * Bulk update message status (e.g., mark all as DELIVERED when user connects).
     * @Modifying + @Transactional required for UPDATE/DELETE queries.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = :status WHERE m.directChat.id = :chatId AND m.sender.id != :userId AND m.status = :currentStatus")
    int updateStatusForDirectChat(
        @Param("chatId") Long chatId,
        @Param("userId") Long userId,
        @Param("currentStatus") MessageStatus currentStatus,
        @Param("status") MessageStatus status
    );

    /**
     * Count unread messages in a direct chat (for badge counts).
     * At scale: maintain unread counts in Redis (INCR/DECR per user per chat).
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.directChat.id = :chatId AND m.sender.id != :userId AND m.status = 'SENT'")
    long countUnreadInDirectChat(@Param("chatId") Long chatId, @Param("userId") Long userId);
}
