package com.kate.dewtally.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A message waiting to be sent to RabbitMQ. Sent messages are kept (sent_at set) for troubleshooting.
 */
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "destination", nullable = false, updatable = false, length = 200)
    private String destination;

    @Column(name = "payload", nullable = false, updatable = false, length = 4000)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected OutboxMessage() {
        // Required by JPA.
    }

    OutboxMessage(String destination, String payload) {
        this.destination = destination;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    void markSent() {
        this.attempts++;
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public String getDestination() {
        return destination;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}