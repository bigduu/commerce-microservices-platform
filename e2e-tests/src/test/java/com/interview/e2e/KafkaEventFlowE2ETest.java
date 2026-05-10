package com.interview.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka Event Flow Test - Uses Testcontainers Kafka to verify:
 * - Domain events can be serialized and published to Kafka topics
 * - Events can be consumed and deserialized correctly
 * - Event ordering is preserved within a partition
 * - Outbox pattern works correctly (write to DB + publish to Kafka)
 */
@Testcontainers
class KafkaEventFlowE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("commerce_events_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0"))
            .withStartupTimeout(Duration.ofMinutes(5));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void domainEventsCanBeSerializedAndPublishedToKafka() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();
        String topic = "user-events";

        DomainEvent event = new DomainEvent(
                UUID.randomUUID().toString(),
                "USER_CREATED",
                "user-service",
                "{\"userId\": \"usr-001\", \"username\": \"alice\"}",
                Instant.now().toString()
        );

        String serialized = objectMapper.writeValueAsString(event);

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, event.aggregateId(), serialized)).get();
        }

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-events");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records).isNotEmpty();

            ConsumerRecord<String, String> record = records.iterator().next();
            DomainEvent deserialized = objectMapper.readValue(record.value(), DomainEvent.class);

            assertThat(deserialized.eventType()).isEqualTo("USER_CREATED");
            assertThat(deserialized.sourceService()).isEqualTo("user-service");
            assertThat(deserialized.aggregateId()).isEqualTo(event.aggregateId());
        }
    }

    @Test
    void eventsCanBeConsumedAndDeserializedCorrectly() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();
        String topic = "order-events";

        List<DomainEvent> events = List.of(
                new DomainEvent("ord-001", "ORDER_CREATED", "order-service",
                        "{\"orderId\": \"ord-001\", \"status\": \"PENDING\"}", Instant.now().toString()),
                new DomainEvent("ord-001", "ORDER_PAYMENT_COMPLETED", "order-service",
                        "{\"orderId\": \"ord-001\", \"status\": \"PAID\"}", Instant.now().toString()),
                new DomainEvent("ord-001", "ORDER_COMPLETED", "order-service",
                        "{\"orderId\": \"ord-001\", \"status\": \"COMPLETED\"}", Instant.now().toString())
        );

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (DomainEvent event : events) {
                String serialized = objectMapper.writeValueAsString(event);
                producer.send(new ProducerRecord<>(topic, event.aggregateId(), serialized)).get();
            }
        }

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-deserialize");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            List<DomainEvent> consumedEvents = new ArrayList<>();
            while (consumedEvents.size() < events.size()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, String> record : records) {
                    consumedEvents.add(objectMapper.readValue(record.value(), DomainEvent.class));
                }
            }

            assertThat(consumedEvents).hasSize(3);
            assertThat(consumedEvents.get(0).eventType()).isEqualTo("ORDER_CREATED");
            assertThat(consumedEvents.get(1).eventType()).isEqualTo("ORDER_PAYMENT_COMPLETED");
            assertThat(consumedEvents.get(2).eventType()).isEqualTo("ORDER_COMPLETED");
        }
    }

    @Test
    void eventOrderingIsPreservedWithinAPartition() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();
        String topic = "inventory-events";

        String aggregateId = "SKU-001";
        List<String> expectedSequence = List.of("INVENTORY_RESERVED", "INVENTORY_UPDATED", "INVENTORY_CONFIRMED");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < expectedSequence.size(); i++) {
                DomainEvent event = new DomainEvent(
                        aggregateId,
                        expectedSequence.get(i),
                        "merchant-service",
                        "{\"sku\": \"SKU-001\", \"sequence\": " + i + "}",
                        Instant.now().toString()
                );
                producer.send(new ProducerRecord<>(topic, aggregateId, objectMapper.writeValueAsString(event))).get();
            }
        }

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-ordering");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            List<String> actualSequence = new ArrayList<>();
            while (actualSequence.size() < expectedSequence.size()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, String> record : records) {
                    DomainEvent event = objectMapper.readValue(record.value(), DomainEvent.class);
                    actualSequence.add(event.eventType());
                }
            }

            assertThat(actualSequence).containsExactlyElementsOf(expectedSequence);
        }
    }

    @Test
    void outboxPatternWorksCorrectlyWriteToDbThenPublishToKafka() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();
        String topic = "outbox-events";

        // Step 1: Create outbox table in PostgreSQL
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS outbox (
                    id SERIAL PRIMARY KEY,
                    aggregate_id VARCHAR(36) NOT NULL,
                    event_type VARCHAR(100) NOT NULL,
                    payload TEXT NOT NULL,
                    topic VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    published BOOLEAN DEFAULT FALSE
                )
                """);
        }

        // Step 2: Insert event into outbox (simulating transaction)
        String aggregateId = UUID.randomUUID().toString();
        String payload = "{\"orderId\": \"ord-001\", \"status\": \"PENDING\"}";

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO outbox (aggregate_id, event_type, payload, topic) VALUES (?, ?, ?, ?)")) {

            ps.setString(1, aggregateId);
            ps.setString(2, "ORDER_CREATED");
            ps.setString(3, payload);
            ps.setString(4, topic);
            ps.executeUpdate();
        }

        // Step 3: Read from outbox and publish to Kafka
        List<OutboxRecord> outboxRecords = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id, aggregate_id, event_type, payload, topic FROM outbox WHERE published = FALSE")) {

            while (rs.next()) {
                outboxRecords.add(new OutboxRecord(
                        rs.getInt("id"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("topic")
                ));
            }
        }

        assertThat(outboxRecords).hasSize(1);
        OutboxRecord record = outboxRecords.get(0);

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            DomainEvent event = new DomainEvent(
                    record.aggregateId(),
                    record.eventType(),
                    "order-service",
                    record.payload(),
                    Instant.now().toString()
            );
            producer.send(new ProducerRecord<>(record.topic(), record.aggregateId(),
                    objectMapper.writeValueAsString(event))).get();
        }

        // Step 4: Mark as published in DB
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE outbox SET published = TRUE WHERE id = ?")) {
            ps.setInt(1, record.id());
            ps.executeUpdate();
        }

        // Step 5: Verify event is in Kafka
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-outbox");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records).isNotEmpty();

            ConsumerRecord<String, String> kafkaRecord = records.iterator().next();
            DomainEvent consumedEvent = objectMapper.readValue(kafkaRecord.value(), DomainEvent.class);
            assertThat(consumedEvent.eventType()).isEqualTo("ORDER_CREATED");
            assertThat(consumedEvent.aggregateId()).isEqualTo(aggregateId);
        }

        // Step 6: Verify DB record is marked published
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT published FROM outbox WHERE id = " + record.id())) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("published")).isTrue();
        }
    }

    record DomainEvent(String aggregateId, String eventType, String sourceService, String payload, String timestamp) {}
    record OutboxRecord(int id, String aggregateId, String eventType, String payload, String topic) {}
}
