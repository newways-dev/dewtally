package com.kate.dewtally.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point of the outbox module: record a message to be sent after the current transaction commits.
 */
@Service
public class Outbox {

    private final OutboxRepository repository;

    public Outbox(OutboxRepository repository) {
        this.repository = repository;
    }

    /**
     * MANDATORY: a message is only ever written together with the change it belongs to.
     * Calling this outside a transaction fails, so the pattern cannot be used the wrong way by accident.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String destination, String payload) {
        repository.save(new OutboxMessage(destination, payload));
    }
}