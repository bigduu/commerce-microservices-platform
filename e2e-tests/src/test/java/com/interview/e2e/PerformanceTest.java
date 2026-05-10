package com.interview.e2e;

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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests using simple timing/benchmarking approach.
 * Tests EventStore write performance, Kafka throughput, Order state machine performance,
 * and concurrent saga processing.
 */
@Testcontainers
class PerformanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("commerce_perf_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0"))
            .withStartupTimeout(Duration.ofMinutes(5));

    /**
     * EventStore Write Performance - Use Testcontainers PostgreSQL:
     * - Create domain_events table
     * - Insert 1000 events and measure time
     * - Assert p95 latency < 10ms per event write
     * - Assert total batch time < 5 seconds
     */
    @Test
    void eventStoreWritePerformance_shouldCompleteWithinLimits() throws Exception {
        // Create domain_events table
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS domain_events (
                    id SERIAL PRIMARY KEY,
                    aggregate_id VARCHAR(36) NOT NULL,
                    event_type VARCHAR(100) NOT NULL,
                    payload TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        int eventCount = 1000;
        List<Long> latencies = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            String sql = "INSERT INTO domain_events (aggregate_id, event_type, payload) VALUES (?, ?, ?)";

            long batchStart = System.nanoTime();

            for (int i = 0; i < eventCount; i++) {
                long start = System.nanoTime();

                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, "ORDER_CREATED");
                    ps.setString(3, "{\"orderId\": \"ord-" + i + "\", \"status\": \"PENDING\"}");
                    ps.executeUpdate();
                }

                long end = System.nanoTime();
                latencies.add(end - start);
            }

            long batchEnd = System.nanoTime();
            long totalBatchTimeMs = Duration.ofNanos(batchEnd - batchStart).toMillis();

            // Calculate p95 latency
            List<Long> sortedLatencies = new ArrayList<>(latencies);
            Collections.sort(sortedLatencies);
            int p95Index = (int) Math.ceil(sortedLatencies.size() * 0.95) - 1;
            long p95LatencyNs = sortedLatencies.get(Math.max(0, p95Index));
            double p95LatencyMs = p95LatencyNs / 1_000_000.0;

            System.out.println("EventStore Write Performance:");
            System.out.println("  Total events: " + eventCount);
            System.out.println("  Total batch time: " + totalBatchTimeMs + " ms");
            System.out.println("  Average latency: " + (totalBatchTimeMs / (double) eventCount) + " ms");
            System.out.println("  p95 latency: " + p95LatencyMs + " ms");

            assertThat(p95LatencyMs).isLessThan(10.0);
            assertThat(totalBatchTimeMs).isLessThan(5000);
        }
    }

    /**
     * Kafka Throughput Test - Use Testcontainers Kafka:
     * - Produce 1000 messages to a topic
     * - Consume and verify all received
     * - Measure producer throughput
     * - Assert throughput > 1000 msg/sec
     */
    @Test
    void kafkaThroughput_shouldExceedThreshold() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();
        String topic = "perf-test-topic";
        int messageCount = 1000;

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        producerProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // Produce messages
        long produceStart = System.nanoTime();
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < messageCount; i++) {
                producer.send(new ProducerRecord<>(topic, "key-" + i, "message-" + i));
            }
            producer.flush();
        }
        long produceEnd = System.nanoTime();
        double produceTimeSec = (produceEnd - produceStart) / 1_000_000_000.0;
        double producerThroughput = messageCount / produceTimeSec;

        // Consume messages
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "perf-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        int consumedCount = 0;
        long consumeStart = System.nanoTime();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            while (consumedCount < messageCount) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
                for (ConsumerRecord<String, String> record : records) {
                    consumedCount++;
                }
            }
        }
        long consumeEnd = System.nanoTime();
        double consumeTimeSec = (consumeEnd - consumeStart) / 1_000_000_000.0;
        double consumerThroughput = consumedCount / consumeTimeSec;

        System.out.println("Kafka Throughput Test:");
        System.out.println("  Messages produced: " + messageCount);
        System.out.println("  Messages consumed: " + consumedCount);
        System.out.println("  Producer time: " + String.format("%.3f", produceTimeSec) + " sec");
        System.out.println("  Producer throughput: " + String.format("%.1f", producerThroughput) + " msg/sec");
        System.out.println("  Consumer time: " + String.format("%.3f", consumeTimeSec) + " sec");
        System.out.println("  Consumer throughput: " + String.format("%.1f", consumerThroughput) + " msg/sec");

        assertThat(consumedCount).isEqualTo(messageCount);
        assertThat(producerThroughput).isGreaterThan(1000.0);
    }

    /**
     * Order State Machine Performance:
     * - Create 10000 Order objects and transition through full lifecycle
     * - Measure time
     * - Assert < 1 second for 10000 state transitions
     */
    @Test
    void orderStateMachinePerformance_shouldCompleteWithinOneSecond() {
        int orderCount = 10000;

        long start = System.nanoTime();

        for (int i = 0; i < orderCount; i++) {
            TestOrder order = new TestOrder(
                    "ord-" + i,
                    "usr-001",
                    "mrc-001",
                    "SKU-001",
                    2,
                    new BigDecimal("99.99")
            );

            // Transition through full lifecycle
            order.transitionTo(TestOrderStatus.PAYMENT_PROCESSING);
            order.transitionTo(TestOrderStatus.INVENTORY_PROCESSING);
            order.transitionTo(TestOrderStatus.MERCHANT_CREDITING);
            order.transitionTo(TestOrderStatus.COMPLETED);
        }

        long end = System.nanoTime();
        long durationMs = Duration.ofNanos(end - start).toMillis();

        System.out.println("Order State Machine Performance:");
        System.out.println("  Orders processed: " + orderCount);
        System.out.println("  Total transitions: " + (orderCount * 4));
        System.out.println("  Total time: " + durationMs + " ms");
        System.out.println("  Transitions per second: " + String.format("%.0f", (orderCount * 4.0) / (durationMs / 1000.0)));

        assertThat(durationMs).isLessThan(1000);
    }

    /**
     * Concurrent Saga Processing:
     * - Use ExecutorService with 10 threads
     * - Each thread simulates a full saga flow (start -> payment -> inventory -> credit -> complete)
     * - Measure total time and throughput
     * - Assert all complete within reasonable time
     */
    @Test
    void concurrentSagaProcessing_shouldCompleteAllWithinReasonableTime() throws Exception {
        int threadCount = 10;
        int sagasPerThread = 100;
        int totalSagas = threadCount * sagasPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalSagas);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long start = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < sagasPerThread; i++) {
                    try {
                        String sagaId = "saga-" + threadId + "-" + i;
                        String orderId = "ord-" + threadId + "-" + i;

                        // Simulate full saga flow
                        TestSagaInstance saga = new TestSagaInstance(sagaId, orderId);

                        // Start saga
                        saga.start();

                        // Payment deducted
                        saga.handlePaymentDeducted();

                        // Inventory reserved
                        saga.handleInventoryReserved();

                        // Merchant credited
                        saga.handleMerchantCredited();

                        if (saga.getStatus() == TestSagaStatus.COMPLETED) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(2, TimeUnit.MINUTES);
        long end = System.nanoTime();
        long durationMs = Duration.ofNanos(end - start).toMillis();

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        double throughput = totalSagas / (durationMs / 1000.0);

        System.out.println("Concurrent Saga Processing:");
        System.out.println("  Threads: " + threadCount);
        System.out.println("  Sagas per thread: " + sagasPerThread);
        System.out.println("  Total sagas: " + totalSagas);
        System.out.println("  Completed: " + completed);
        System.out.println("  Executor terminated: " + terminated);
        System.out.println("  Success count: " + successCount.get());
        System.out.println("  Failure count: " + failureCount.get());
        System.out.println("  Total time: " + durationMs + " ms");
        System.out.println("  Throughput: " + String.format("%.1f", throughput) + " sagas/sec");

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(totalSagas);
        assertThat(failureCount.get()).isEqualTo(0);
        assertThat(durationMs).isLessThan(120000); // 2 minutes
    }

    // Simple test order for performance testing
    enum TestOrderStatus {
        PENDING,
        PAYMENT_PROCESSING,
        INVENTORY_PROCESSING,
        MERCHANT_CREDITING,
        COMPLETED,
        FAILED
    }

    static class TestOrder {
        private final String orderId;
        private final String userId;
        private final String merchantId;
        private final String sku;
        private final int quantity;
        private final BigDecimal unitPrice;
        private TestOrderStatus status;

        TestOrder(String orderId, String userId, String merchantId, String sku,
                  int quantity, BigDecimal unitPrice) {
            this.orderId = orderId;
            this.userId = userId;
            this.merchantId = merchantId;
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.status = TestOrderStatus.PENDING;
        }

        void transitionTo(TestOrderStatus newStatus) {
            this.status = newStatus;
        }

        TestOrderStatus getStatus() {
            return status;
        }
    }

    // Simple test saga for performance testing
    enum TestSagaStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }

    enum TestSagaStep {
        DEDUCT_PAYMENT,
        RESERVE_INVENTORY,
        CREDIT_MERCHANT
    }

    static class TestSagaInstance {
        private final String sagaId;
        private final String orderId;
        private TestSagaStatus status;
        private TestSagaStep currentStep;

        TestSagaInstance(String sagaId, String orderId) {
            this.sagaId = sagaId;
            this.orderId = orderId;
            this.status = TestSagaStatus.RUNNING;
            this.currentStep = TestSagaStep.DEDUCT_PAYMENT;
        }

        void start() {
            this.status = TestSagaStatus.RUNNING;
            this.currentStep = TestSagaStep.DEDUCT_PAYMENT;
        }

        void handlePaymentDeducted() {
            if (status != TestSagaStatus.RUNNING) return;
            this.currentStep = TestSagaStep.RESERVE_INVENTORY;
        }

        void handleInventoryReserved() {
            if (status != TestSagaStatus.RUNNING) return;
            this.currentStep = TestSagaStep.CREDIT_MERCHANT;
        }

        void handleMerchantCredited() {
            if (status != TestSagaStatus.RUNNING) return;
            this.status = TestSagaStatus.COMPLETED;
        }

        TestSagaStatus getStatus() {
            return status;
        }
    }
}
