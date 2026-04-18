package com.chatapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a group chat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatResponse {
    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private UserResponse admin;
    private List<UserResponse> members;
    private int memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
