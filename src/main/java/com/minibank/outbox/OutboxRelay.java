package com.minibank.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final Duration retention;

    public OutboxRelay(OutboxEventRepository outbox,
                       @Value("${minibank.housekeeping.retention:30d}") Duration retention) {
        this.outbox = outbox;
        this.retention = retention;
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

    @Scheduled(fixedDelayString = "${minibank.housekeeping.interval:6h}")
    @Transactional
    public void purgePublished() {
        int deleted = outbox.deletePublishedBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Purged {} published outbox events", deleted);
        }
    }
}
