package com.chatapp.model;

/**
 * Lifecycle states of a chat message.
 *
 * SENT      → Message persisted to DB and published to the WebSocket broker.
 * DELIVERED → At least one recipient received it via WebSocket.
 * READ      → Recipient opened the chat and saw the message (future extension).
 * FAILED    → Delivery failed (for retry queue — useful with Kafka dead-letter topics).
 *
 * Scaling Note:
 * When using Kafka, this status is updated by a separate "delivery-tracker" microservice
 * that listens on a "message.delivered" Kafka topic. The main service just emits SENT.
 */
public enum MessageStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED
}
