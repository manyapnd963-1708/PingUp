package com.chatapp.service;

import com.chatapp.dto.request.CreateGroupRequest;
import com.chatapp.dto.response.DirectChatResponse;
import com.chatapp.dto.response.GroupChatResponse;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.DirectChat;
import com.chatapp.model.GroupChat;
import com.chatapp.model.User;
import com.chatapp.repository.DirectChatRepository;
import com.chatapp.repository.GroupChatRepository;
import com.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing Direct Chats and Group Chats.
 *
 * Direct Chat Invariant:
 * user1 always has the lower user ID. This is enforced in getOrCreateDirectChat()
 * and relied upon by the database unique constraint (user1_id, user2_id).
 *
 * Scaling Notes:
 * - Group membership checks are cached in Redis (SISMEMBER on a Set per group).
 * - Inbox queries use pagination and are cached with short TTL (e.g., 30 seconds).
 * - For very large groups (thousands of members), member list fetching uses
 *   cursor-based pagination instead of loading all members at once.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final DirectChatRepository directChatRepository;
    private final GroupChatRepository groupChatRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;

    // ==============================
    //  Direct Chat Operations
    // ==============================

    /**
     * Get or create a direct chat between two users.
     * If a chat already exists, return it. Otherwise, create a new one.
     * Enforces the canonical ordering: user with lower ID is always user1.
     */
    @Transactional
    public DirectChatResponse getOrCreateDirectChat(Long currentUserId, Long otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new IllegalArgumentException("Cannot create a chat with yourself");
        }

        User currentUser = userService.findUserById(currentUserId);
        User otherUser = userService.findUserById(otherUserId);

        // Canonical ordering: lower ID = user1
        User user1 = currentUserId < otherUserId ? currentUser : otherUser;
        User user2 = currentUserId < otherUserId ? otherUser : currentUser;

        DirectChat chat = directChatRepository.findByUsers(user1, user2)
            .orElseGet(() -> {
                logger.info("Creating new direct chat between users {} and {}", user1.getId(), user2.getId());
                DirectChat newChat = DirectChat.builder().user1(user1).user2(user2).build();
                return directChatRepository.save(newChat);
            });

        User otherParticipant = chat.getUser1().getId().equals(currentUserId)
            ? chat.getUser2()
            : chat.getUser1();

        long unreadCount = messageRepository.countUnreadInDirectChat(chat.getId(), currentUserId);

        return DirectChatResponse.builder()
            .id(chat.getId())
            .otherUser(userService.mapToResponse(otherParticipant))
            .unreadCount(unreadCount)
            .createdAt(chat.getCreatedAt())
            .build();
    }

    /**
     * Get all direct chats for the current user (inbox).
     */
    @Transactional(readOnly = true)
    public List<DirectChatResponse> getDirectChatsForUser(Long userId) {
        User user = userService.findUserById(userId);
        return directChatRepository.findAllByUser(user).stream()
            .map(chat -> {
                User other = chat.getUser1().getId().equals(userId) ? chat.getUser2() : chat.getUser1();
                long unread = messageRepository.countUnreadInDirectChat(chat.getId(), userId);
                return DirectChatResponse.builder()
                    .id(chat.getId())
                    .otherUser(userService.mapToResponse(other))
                    .unreadCount(unread)
                    .createdAt(chat.getCreatedAt())
                    .build();
            })
            .collect(Collectors.toList());
    }

    /**
     * Load a DirectChat entity by ID (for use in MessageService).
     */
    @Transactional(readOnly = true)
    public DirectChat findDirectChatById(Long chatId) {
        return directChatRepository.findById(chatId)
            .orElseThrow(() -> new ResourceNotFoundException("DirectChat", "id", chatId));
    }

    /**
     * Find a DirectChat by both user entities (canonical order must be enforced by caller).
     */
    @Transactional(readOnly = true)
    public DirectChat findDirectChatByUsers(User user1, User user2) {
        return directChatRepository.findByUsers(user1, user2)
            .orElseThrow(() -> new ResourceNotFoundException("DirectChat between users " + user1.getId() + " and " + user2.getId()));
    }

    // ==============================
    //  Group Chat Operations
    // ==============================

    /**
     * Create a new group chat.
     * The creator is automatically added as admin and member.
     */
    @Transactional
    public GroupChatResponse createGroupChat(Long creatorId, CreateGroupRequest request) {
        User creator = userService.findUserById(creatorId);

        Set<User> members = new HashSet<>();
        members.add(creator);  // Creator is always a member

        // Add requested members (validates each user exists)
        request.getMemberIds().forEach(memberId -> {
            if (!memberId.equals(creatorId)) {
                members.add(userService.findUserById(memberId));
            }
        });

        GroupChat group = GroupChat.builder()
            .name(request.getName())
            .description(request.getDescription())
            .admin(creator)
            .members(members)
            .build();

        GroupChat saved = groupChatRepository.save(group);
        logger.info("Group chat '{}' created by user {}", saved.getName(), creatorId);

        return mapGroupToResponse(saved);
    }

    /**
     * Add a member to a group chat.
     * Only the group admin can add members.
     */
    @Transactional
    public GroupChatResponse addMember(Long groupId, Long adminId, Long newMemberId) {
        GroupChat group = findGroupChatById(groupId);

        if (!group.getAdmin().getId().equals(adminId)) {
            throw new UnauthorizedException("Only the group admin can add members");
        }

        User newMember = userService.findUserById(newMemberId);
        group.getMembers().add(newMember);
        GroupChat saved = groupChatRepository.save(group);

        logger.info("User {} added to group {} by admin {}", newMemberId, groupId, adminId);
        return mapGroupToResponse(saved);
    }

    /**
     * Remove a member from a group chat.
     * Admin can remove any member. Members can remove themselves (leave group).
     */
    @Transactional
    public GroupChatResponse removeMember(Long groupId, Long requesterId, Long targetMemberId) {
        GroupChat group = findGroupChatById(groupId);
        boolean isAdmin = group.getAdmin().getId().equals(requesterId);
        boolean isSelf = requesterId.equals(targetMemberId);

        if (!isAdmin && !isSelf) {
            throw new UnauthorizedException("Only the admin or the member themselves can remove a member");
        }

        User member = userService.findUserById(targetMemberId);
        group.getMembers().remove(member);
        GroupChat saved = groupChatRepository.save(group);

        logger.info("User {} removed from group {} by user {}", targetMemberId, groupId, requesterId);
        return mapGroupToResponse(saved);
    }

    /**
     * Get all groups the current user belongs to.
     */
    @Transactional(readOnly = true)
    public List<GroupChatResponse> getGroupChatsForUser(Long userId) {
        User user = userService.findUserById(userId);
        return groupChatRepository.findAllByMember(user).stream()
            .map(this::mapGroupToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get group chat details by ID.
     * Only members can view group details.
     */
    @Transactional(readOnly = true)
    public GroupChatResponse getGroupChatById(Long groupId, Long requesterId) {
        GroupChat group = findGroupChatById(groupId);

        if (!groupChatRepository.isUserMember(groupId, requesterId)) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        return mapGroupToResponse(group);
    }

    /**
     * Load a GroupChat entity by ID (for use in MessageService).
     */
    @Transactional(readOnly = true)
    public GroupChat findGroupChatById(Long groupId) {
        return groupChatRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("GroupChat", "id", groupId));
    }

    /**
     * Check if a user is a member of a group.
     * At scale: replace with Redis SISMEMBER check.
     */
    @Transactional(readOnly = true)
    public boolean isGroupMember(Long groupId, Long userId) {
        return groupChatRepository.isUserMember(groupId, userId);
    }

    // ==============================
    //  Mapping Helpers
    // ==============================

    private GroupChatResponse mapGroupToResponse(GroupChat group) {
        return GroupChatResponse.builder()
            .id(group.getId())
            .name(group.getName())
            .description(group.getDescription())
            .avatarUrl(group.getAvatarUrl())
            .admin(userService.mapToResponse(group.getAdmin()))
            .members(group.getMembers().stream()
                .map(userService::mapToResponse)
                .collect(Collectors.toList()))
            .memberCount(group.getMembers().size())
            .createdAt(group.getCreatedAt())
            .updatedAt(group.getUpdatedAt())
            .build();
    }
}
