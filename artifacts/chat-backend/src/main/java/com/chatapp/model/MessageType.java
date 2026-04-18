package com.chatapp.model;

/**
 * Type of message content.
 * Extensible for media messages without schema changes.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    SYSTEM   // System notifications (e.g., "User X joined the group")
}
