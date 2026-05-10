package com.interview.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPoller {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        List<OutboxMessage> messages = outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(
                PageRequest.of(0, BATCH_SIZE));
        if (messages.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (OutboxMessage msg : messages) {
            futures.add(kafkaTemplate.send(msg.getTopic(), msg.getAggregateType(), msg.getPayload())
                    .thenAccept(result -> msg.markPublished())
                    .exceptionally(ex -> {
                        logger.error("Failed to publish outbox message {}: {}", msg.getEventId(), ex.getMessage());
                        return null;
                    }));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Outbox publish timed out or failed after {}s — unpubished messages will be retried",
                    SEND_TIMEOUT_SECONDS);
        }

        outboxRepository.saveAll(messages);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanup() {
        outboxRepository.deleteByPublishedAtIsNotNull();
    }
}
