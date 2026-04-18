package com.chatapp.repository;

import com.chatapp.model.DirectChat;
import com.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for DirectChat (one-to-one chat) operations.
 *
 * Note: user1 always has the lower ID — enforced in ChatService.
 * This invariant ensures the unique constraint (user1_id, user2_id) works correctly.
 */
@Repository
public interface DirectChatRepository extends JpaRepository<DirectChat, Long> {

    /**
     * Find the direct chat between two specific users.
     * Uses the canonical ordering (lower ID = user1) enforced by ChatService.
     */
    @Query("SELECT dc FROM DirectChat dc WHERE dc.user1 = :user1 AND dc.user2 = :user2")
    Optional<DirectChat> findByUsers(@Param("user1") User user1, @Param("user2") User user2);

    /**
     * Get all direct chats for a given user (for inbox listing).
     * Returns chats where the user is either user1 or user2.
     */
    @Query("SELECT dc FROM DirectChat dc WHERE dc.user1 = :user OR dc.user2 = :user ORDER BY dc.createdAt DESC")
    List<DirectChat> findAllByUser(@Param("user") User user);
}
