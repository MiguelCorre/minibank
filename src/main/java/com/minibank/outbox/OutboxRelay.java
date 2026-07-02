package com.minibank.outbox;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and publishes pending events. Here "publishing" is a log
 * line; in production this is where a Kafka producer or an SQS client would
 * sit — the at-least-once semantics stay the same.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outbox;

    public OutboxRelay(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${minibank.outbox.relay-interval:5s}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(BATCH_SIZE));
        for (OutboxEvent event : pending) {
            log.info("Publishing outbox event {} {} payload={}",
                    event.getEventType(), event.getId(), event.getPayload());
            event.markPublished();
        }
    }
}
