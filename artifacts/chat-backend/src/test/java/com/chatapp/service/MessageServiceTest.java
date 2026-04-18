package com.chatapp.service;

import com.chatapp.dto.request.SendMessageRequest;
import com.chatapp.dto.response.DirectChatResponse;
import com.chatapp.dto.response.MessageResponse;
import com.chatapp.dto.response.UserResponse;
import com.chatapp.exception.UnauthorizedException;
import com.chatapp.model.*;
import com.chatapp.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageService.
 * Tests message sending, persistence, and WebSocket delivery.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private UserService userService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageService messageService;

    private User sender;
    private User recipient;
    private DirectChat directChat;
    private GroupChat groupChat;

    @BeforeEach
    void setUp() {
        sender = User.builder()
            .id(1L)
            .username("alice")
            .email("alice@example.com")
            .displayName("Alice")
            .build();

        recipient = User.builder()
            .id(2L)
            .username("bob")
            .email("bob@example.com")
            .displayName("Bob")
            .build();

        directChat = DirectChat.builder()
            .id(10L)
            .user1(sender)    // sender.id (1) < recipient.id (2)
            .user2(recipient)
            .build();

        groupChat = GroupChat.builder()
            .id(20L)
            .name("Test Group")
            .admin(sender)
            .members(Set.of(sender, recipient))
            .build();
    }

    // ==============================
    //  Direct Message Tests
    // ==============================

    @Test
    @DisplayName("sendDirectMessage - valid request - persists message and delivers via WebSocket")
    void sendDirectMessage_ValidRequest_PersistsAndDeliversViaWebSocket() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Hello Bob!");
        request.setRecipientId(2L);

        UserResponse recipientResponse = UserResponse.builder()
            .id(2L).username("bob").build();

        DirectChatResponse chatResponse = DirectChatResponse.builder()
            .id(10L).otherUser(recipientResponse).unreadCount(0L).build();

        when(userService.findUserById(1L)).thenReturn(sender);
        when(userService.findUserById(2L)).thenReturn(recipient);
        when(chatService.getOrCreateDirectChat(1L, 2L)).thenReturn(chatResponse);
        when(chatService.findDirectChatByUsers(any(), any())).thenReturn(directChat);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            return Message.builder()
                .id(100L)
                .content(m.getContent())
                .sender(m.getSender())
                .directChat(m.getDirectChat())
                .status(MessageStatus.SENT)
                .type(MessageType.TEXT)
                .createdAt(LocalDateTime.now())
                .build();
        });

        // Act
        MessageResponse result = messageService.sendDirectMessage(1L, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello Bob!");
        assertThat(result.getSenderId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(result.getDirectChatId()).isEqualTo(10L);

        // Verify message was persisted
        verify(messageRepository).save(any(Message.class));

        // Verify WebSocket delivery to both sender and recipient
        verify(messagingTemplate, times(2)).convertAndSendToUser(
            anyString(), eq("/queue/messages"), any(MessageResponse.class)
        );
    }

    @Test
    @DisplayName("sendDirectMessage - null recipientId - throws IllegalArgumentException")
    void sendDirectMessage_NullRecipientId_ThrowsIllegalArgumentException() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Hello!");
        request.setRecipientId(null);  // Missing!

        // Act & Assert
        assertThatThrownBy(() -> messageService.sendDirectMessage(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("recipientId");
    }

    // ==============================
    //  Group Message Tests
    // ==============================

    @Test
    @DisplayName("sendGroupMessage - member sends - persists and broadcasts to topic")
    void sendGroupMessage_MemberSends_PersistsAndBroadcastsToTopic() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Hey everyone!");
        request.setGroupChatId(20L);

        when(chatService.isGroupMember(20L, 1L)).thenReturn(true);
        when(userService.findUserById(1L)).thenReturn(sender);
        when(chatService.findGroupChatById(20L)).thenReturn(groupChat);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            return Message.builder()
                .id(200L)
                .content(m.getContent())
                .sender(m.getSender())
                .groupChat(m.getGroupChat())
                .status(MessageStatus.SENT)
                .type(MessageType.TEXT)
                .createdAt(LocalDateTime.now())
                .build();
        });

        // Act
        MessageResponse result = messageService.sendGroupMessage(1L, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hey everyone!");
        assertThat(result.getGroupChatId()).isEqualTo(20L);

        // Verify broadcast to group topic
        verify(messagingTemplate).convertAndSend(
            eq("/topic/group/20"), any(MessageResponse.class)
        );
    }

    @Test
    @DisplayName("sendGroupMessage - non-member sends - throws UnauthorizedException")
    void sendGroupMessage_NonMemberSends_ThrowsUnauthorizedException() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Intruder!");
        request.setGroupChatId(20L);

        // User 99 is not a member of group 20
        when(chatService.isGroupMember(20L, 99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> messageService.sendGroupMessage(99L, request))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("not a member");

        // Verify no message was saved or broadcast
        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("sendGroupMessage - null groupChatId - throws IllegalArgumentException")
    void sendGroupMessage_NullGroupChatId_ThrowsIllegalArgumentException() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Hello!");
        request.setGroupChatId(null);

        // Act & Assert
        assertThatThrownBy(() -> messageService.sendGroupMessage(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("groupChatId");
    }

    // ==============================
    //  Message History Tests
    // ==============================

    @Test
    @DisplayName("getDirectChatHistory - returns paginated messages")
    void getDirectChatHistory_ReturnsPagedMessages() {
        // Arrange
        Message msg = Message.builder()
            .id(1L)
            .content("Hello")
            .sender(sender)
            .directChat(directChat)
            .status(MessageStatus.SENT)
            .type(MessageType.TEXT)
            .createdAt(LocalDateTime.now())
            .build();

        when(messageRepository.findByDirectChatId(eq(10L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(msg)));

        // Act
        List<MessageResponse> result = messageService.getDirectChatHistory(10L, 0, 50);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
        assertThat(result.get(0).getSenderUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("getDirectChatHistory - empty chat - returns empty list")
    void getDirectChatHistory_EmptyChat_ReturnsEmptyList() {
        // Arrange
        when(messageRepository.findByDirectChatId(eq(10L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act
        List<MessageResponse> result = messageService.getDirectChatHistory(10L, 0, 50);

        // Assert
        assertThat(result).isEmpty();
    }
}
