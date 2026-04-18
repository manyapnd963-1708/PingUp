package com.chatapp.controller;

import com.chatapp.dto.request.CreateGroupRequest;
import com.chatapp.dto.request.SendMessageRequest;
import com.chatapp.dto.response.DirectChatResponse;
import com.chatapp.dto.response.GroupChatResponse;
import com.chatapp.dto.response.MessageResponse;
import com.chatapp.security.UserPrincipal;
import com.chatapp.service.ChatService;
import com.chatapp.service.MessageService;
import com.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Handles both REST and WebSocket (STOMP) chat operations.
 *
 * REST Endpoints (require JWT in Authorization header):
 * GET  /api/v1/chats/direct              — list all direct chats for current user
 * POST /api/v1/chats/direct/{userId}     — start/get direct chat with a user
 * GET  /api/v1/chats/direct/{chatId}/messages — message history
 * POST /api/v1/chats/groups              — create a group chat
 * GET  /api/v1/chats/groups              — list my groups
 * GET  /api/v1/chats/groups/{groupId}    — group details
 * POST /api/v1/chats/groups/{groupId}/members/{userId}    — add member
 * DELETE /api/v1/chats/groups/{groupId}/members/{userId}  — remove/leave
 * GET  /api/v1/chats/groups/{groupId}/messages            — group message history
 *
 * WebSocket STOMP Endpoints (JWT in STOMP CONNECT header):
 * /app/chat/direct  → send a direct message
 * /app/chat/group   → send a group message
 *
 * Client subscriptions:
 * /user/queue/messages        → receive direct messages
 * /topic/group/{groupId}      → receive group messages
 */
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final MessageService messageService;
    private final UserService userService;

    // ==============================
    //  WebSocket STOMP Message Handlers
    // ==============================

    /**
     * Handle a direct message sent via STOMP.
     * Client sends to: /app/chat/direct
     * Payload: { recipientId, content, type? }
     *
     * The sender is extracted from the authenticated WebSocket principal (not the payload)
     * to prevent spoofing.
     */
    @MessageMapping("/chat/direct")
    public void handleDirectMessage(
        @Payload SendMessageRequest request,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            logger.warn("Unauthenticated WebSocket message rejected");
            return;
        }

        String username = principal.getName();
        logger.debug("WebSocket direct message from {}", username);

        var sender = userService.findUserByUsername(username);
        messageService.sendDirectMessage(sender.getId(), request);
    }

    /**
     * Handle a group message sent via STOMP.
     * Client sends to: /app/chat/group
     * Payload: { groupChatId, content, type? }
     */
    @MessageMapping("/chat/group")
    public void handleGroupMessage(
        @Payload SendMessageRequest request,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            logger.warn("Unauthenticated WebSocket group message rejected");
            return;
        }

        String username = principal.getName();
        logger.debug("WebSocket group message from {} to group {}", username, request.getGroupChatId());

        var sender = userService.findUserByUsername(username);
        messageService.sendGroupMessage(sender.getId(), request);
    }

    // ==============================
    //  Direct Chat REST Endpoints
    // ==============================

    /**
     * Get all direct chats for the current user (inbox).
     */
    @GetMapping("/direct")
    public ResponseEntity<List<DirectChatResponse>> getMyDirectChats(
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(chatService.getDirectChatsForUser(currentUser.getId()));
    }

    /**
     * Start or retrieve a direct chat with another user.
     */
    @PostMapping("/direct/{userId}")
    public ResponseEntity<DirectChatResponse> getOrCreateDirectChat(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @PathVariable Long userId
    ) {
        DirectChatResponse chat = chatService.getOrCreateDirectChat(currentUser.getId(), userId);
        return ResponseEntity.ok(chat);
    }

    /**
     * Get message history for a direct chat.
     * Paginated: ?page=0&size=50 (default: page 0, 50 messages)
     */
    @GetMapping("/direct/{chatId}/messages")
    public ResponseEntity<List<MessageResponse>> getDirectChatHistory(
        @PathVariable Long chatId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        // Mark messages as delivered when fetched
        messageService.markDirectChatAsDelivered(chatId, currentUser.getId());
        return ResponseEntity.ok(messageService.getDirectChatHistory(chatId, page, size));
    }

    // ==============================
    //  Group Chat REST Endpoints
    // ==============================

    /**
     * Create a new group chat.
     */
    @PostMapping("/groups")
    public ResponseEntity<GroupChatResponse> createGroupChat(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @Valid @RequestBody CreateGroupRequest request
    ) {
        GroupChatResponse group = chatService.createGroupChat(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    /**
     * Get all group chats the current user belongs to.
     */
    @GetMapping("/groups")
    public ResponseEntity<List<GroupChatResponse>> getMyGroups(
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(chatService.getGroupChatsForUser(currentUser.getId()));
    }

    /**
     * Get details of a specific group chat.
     */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<GroupChatResponse> getGroupChat(
        @PathVariable Long groupId,
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(chatService.getGroupChatById(groupId, currentUser.getId()));
    }

    /**
     * Add a member to a group chat (admin only).
     */
    @PostMapping("/groups/{groupId}/members/{userId}")
    public ResponseEntity<GroupChatResponse> addMember(
        @PathVariable Long groupId,
        @PathVariable Long userId,
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(chatService.addMember(groupId, currentUser.getId(), userId));
    }

    /**
     * Remove a member from a group (admin only, or self-remove = leave).
     */
    @DeleteMapping("/groups/{groupId}/members/{userId}")
    public ResponseEntity<GroupChatResponse> removeMember(
        @PathVariable Long groupId,
        @PathVariable Long userId,
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(chatService.removeMember(groupId, currentUser.getId(), userId));
    }

    /**
     * Get message history for a group chat.
     */
    @GetMapping("/groups/{groupId}/messages")
    public ResponseEntity<List<MessageResponse>> getGroupChatHistory(
        @PathVariable Long groupId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(messageService.getGroupChatHistory(groupId, page, size));
    }
}
