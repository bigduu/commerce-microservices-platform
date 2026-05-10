package com.interview.common.outbox;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox_messages", indexes = {
        @Index(name = "idx_outbox_published_created", columnList = "published_at, created_at")
})
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected OutboxMessage() {}

    public OutboxMessage(String eventId, String aggregateType, String eventType,
                         String payload, String topic) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.topic = topic;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getTopic() { return topic; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void markPublished() { this.publishedAt = Instant.now(); }
}
