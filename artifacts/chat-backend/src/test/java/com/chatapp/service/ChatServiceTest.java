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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatService (group and direct chat management).
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private DirectChatRepository directChatRepository;

    @Mock
    private GroupChatRepository groupChatRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatService chatService;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        alice = User.builder().id(1L).username("alice").email("alice@test.com").displayName("Alice").build();
        bob = User.builder().id(2L).username("bob").email("bob@test.com").displayName("Bob").build();
        charlie = User.builder().id(3L).username("charlie").email("charlie@test.com").displayName("Charlie").build();
    }

    // ==============================
    //  Direct Chat Tests
    // ==============================

    @Test
    @DisplayName("getOrCreateDirectChat - existing chat - returns existing chat")
    void getOrCreateDirectChat_ExistingChat_ReturnsExistingChat() {
        // Arrange
        DirectChat existingChat = DirectChat.builder().id(10L).user1(alice).user2(bob).build();

        when(userService.findUserById(1L)).thenReturn(alice);
        when(userService.findUserById(2L)).thenReturn(bob);
        when(directChatRepository.findByUsers(alice, bob)).thenReturn(Optional.of(existingChat));
        when(messageRepository.countUnreadInDirectChat(10L, 1L)).thenReturn(3L);
        when(userService.mapToResponse(bob)).thenReturn(
            com.chatapp.dto.response.UserResponse.builder().id(2L).username("bob").build()
        );

        // Act
        DirectChatResponse result = chatService.getOrCreateDirectChat(1L, 2L);

        // Assert
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUnreadCount()).isEqualTo(3L);

        // Verify no new chat was created
        verify(directChatRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreateDirectChat - no existing chat - creates new chat")
    void getOrCreateDirectChat_NoExistingChat_CreatesNewChat() {
        // Arrange
        when(userService.findUserById(1L)).thenReturn(alice);
        when(userService.findUserById(2L)).thenReturn(bob);
        when(directChatRepository.findByUsers(alice, bob)).thenReturn(Optional.empty());

        DirectChat newChat = DirectChat.builder().id(11L).user1(alice).user2(bob).build();
        when(directChatRepository.save(any(DirectChat.class))).thenReturn(newChat);
        when(messageRepository.countUnreadInDirectChat(11L, 1L)).thenReturn(0L);
        when(userService.mapToResponse(bob)).thenReturn(
            com.chatapp.dto.response.UserResponse.builder().id(2L).username("bob").build()
        );

        // Act
        DirectChatResponse result = chatService.getOrCreateDirectChat(1L, 2L);

        // Assert
        assertThat(result.getId()).isEqualTo(11L);
        verify(directChatRepository).save(any(DirectChat.class));
    }

    @Test
    @DisplayName("getOrCreateDirectChat - same user - throws IllegalArgumentException")
    void getOrCreateDirectChat_SameUser_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> chatService.getOrCreateDirectChat(1L, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("yourself");
    }

    // ==============================
    //  Group Chat Tests
    // ==============================

    @Test
    @DisplayName("createGroupChat - valid request - creates group with creator as admin and member")
    void createGroupChat_ValidRequest_CreatesGroupWithCreatorAsMember() {
        // Arrange
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Dev Team");
        request.setDescription("Dev team group");
        request.setMemberIds(List.of(2L, 3L));

        when(userService.findUserById(1L)).thenReturn(alice);
        when(userService.findUserById(2L)).thenReturn(bob);
        when(userService.findUserById(3L)).thenReturn(charlie);

        Set<User> expectedMembers = new HashSet<>(Set.of(alice, bob, charlie));
        GroupChat savedGroup = GroupChat.builder()
            .id(50L)
            .name("Dev Team")
            .description("Dev team group")
            .admin(alice)
            .members(expectedMembers)
            .build();

        when(groupChatRepository.save(any(GroupChat.class))).thenReturn(savedGroup);
        when(userService.mapToResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return com.chatapp.dto.response.UserResponse.builder()
                .id(u.getId()).username(u.getUsername()).build();
        });

        // Act
        GroupChatResponse result = chatService.createGroupChat(1L, request);

        // Assert
        assertThat(result.getId()).isEqualTo(50L);
        assertThat(result.getName()).isEqualTo("Dev Team");
        assertThat(result.getMemberCount()).isEqualTo(3);
        assertThat(result.getAdmin().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("addMember - admin adds member - succeeds")
    void addMember_AdminAddsMember_Succeeds() {
        // Arrange
        GroupChat group = GroupChat.builder()
            .id(50L).name("Group").admin(alice)
            .members(new HashSet<>(Set.of(alice, bob))).build();

        when(groupChatRepository.findById(50L)).thenReturn(Optional.of(group));
        when(userService.findUserById(3L)).thenReturn(charlie);
        when(groupChatRepository.save(any(GroupChat.class))).thenReturn(group);
        when(userService.mapToResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return com.chatapp.dto.response.UserResponse.builder()
                .id(u.getId()).username(u.getUsername()).build();
        });

        // Act
        GroupChatResponse result = chatService.addMember(50L, 1L, 3L);

        // Assert — group now has 3 members
        assertThat(result.getMemberCount()).isEqualTo(3);
        verify(groupChatRepository).save(any(GroupChat.class));
    }

    @Test
    @DisplayName("addMember - non-admin tries to add member - throws UnauthorizedException")
    void addMember_NonAdmin_ThrowsUnauthorizedException() {
        // Arrange — bob (id=2) is NOT the admin (alice id=1 is)
        GroupChat group = GroupChat.builder()
            .id(50L).name("Group").admin(alice)
            .members(new HashSet<>(Set.of(alice, bob))).build();

        when(groupChatRepository.findById(50L)).thenReturn(Optional.of(group));

        // Act & Assert
        assertThatThrownBy(() -> chatService.addMember(50L, 2L, 3L))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("admin");
    }

    @Test
    @DisplayName("removeMember - member leaves group - succeeds")
    void removeMember_MemberLeaves_Succeeds() {
        // Arrange — bob removes himself
        GroupChat group = GroupChat.builder()
            .id(50L).name("Group").admin(alice)
            .members(new HashSet<>(Set.of(alice, bob, charlie))).build();

        when(groupChatRepository.findById(50L)).thenReturn(Optional.of(group));
        when(userService.findUserById(2L)).thenReturn(bob);
        when(groupChatRepository.save(any(GroupChat.class))).thenReturn(group);
        when(userService.mapToResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return com.chatapp.dto.response.UserResponse.builder()
                .id(u.getId()).username(u.getUsername()).build();
        });

        // Act
        assertThatNoException().isThrownBy(() -> chatService.removeMember(50L, 2L, 2L));
        verify(groupChatRepository).save(any(GroupChat.class));
    }

    @Test
    @DisplayName("findGroupChatById - non-existent ID - throws ResourceNotFoundException")
    void findGroupChatById_NonExistent_ThrowsResourceNotFoundException() {
        when(groupChatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.findGroupChatById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("GroupChat");
    }
}
