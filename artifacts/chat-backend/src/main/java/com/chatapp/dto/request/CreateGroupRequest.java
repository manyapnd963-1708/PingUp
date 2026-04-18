package com.chatapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * DTO for creating a new group chat.
 */
@Data
public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * Initial member user IDs (excluding the creator, who is added automatically).
     * At least one other member is required.
     */
    @NotEmpty(message = "At least one member must be specified")
    private List<Long> memberIds;
}
