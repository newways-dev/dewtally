package com.kate.dewtally.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    // Locks the oldest unsent messages. SKIP LOCKED: several app instances can relay in parallel
    // without picking the same message.
    @Query(value = """
            SELECT * FROM outbox_message
            WHERE sent_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> lockUnsent(@Param("limit") int limit);

    // For tests and troubleshooting: all messages about one thing, e.g. one document id.
    List<OutboxMessage> findByPayload(String payload);

    // For the "pending" metric; the partial index on unsent messages keeps it cheap.
    long countBySentAtIsNull();
}
