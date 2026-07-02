package com.minibank.outbox;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minibank.transfer.TransferCompleted;

/**
 * Writes domain events to the outbox inside the publishing transaction —
 * the state change and the event are committed (or rolled back) together.
 */
@Component
class TransferOutboxWriter {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    TransferOutboxWriter(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @EventListener
    void on(TransferCompleted event) {
        try {
            outbox.save(OutboxEvent.of(
                    TransferCompleted.class.getSimpleName(),
                    event.transferId(),
                    objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + event, e);
        }
    }
}
