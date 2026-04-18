package com.chatapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for a one-to-one (direct) chat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectChatResponse {
    private Long id;
    private UserResponse otherUser;  // The other participant (not the caller)
    private Long unreadCount;
    private LocalDateTime createdAt;
}
