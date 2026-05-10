package com.interview.e2e;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Infrastructure E2E Test that verifies PostgreSQL and Kafka containers
 * can start and accept connections, produce and consume messages.
 */
@Testcontainers
class InfrastructureE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("commerce_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0"))
            .withStartupTimeout(Duration.ofMinutes(5));

    @Test
    void postgreSQLContainerShouldStartAndAcceptConnections() throws Exception {
        assertThat(postgres.isRunning()).isTrue();

        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE IF NOT EXISTS test_table (id SERIAL PRIMARY KEY, name VARCHAR(255))");
            statement.executeUpdate("INSERT INTO test_table (name) VALUES ('Alice')");

            ResultSet resultSet = statement.executeQuery("SELECT * FROM test_table WHERE name = 'Alice'");
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("name")).isEqualTo("Alice");
        }
    }

    @Test
    void kafkaContainerShouldStartAndAllowProduceConsume() {
        assertThat(kafka.isRunning()).isTrue();

        String bootstrapServers = kafka.getBootstrapServers();
        String topic = "test-topic";

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "key1", "Hello Kafka"));
            producer.flush();
        }

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records).isNotEmpty();

            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("key1");
            assertThat(record.value()).isEqualTo("Hello Kafka");
        }
    }

    @Test
    void databaseTablesCanBeCreatedAndQueried() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id VARCHAR(36) NOT NULL,
                    merchant_id VARCHAR(36) NOT NULL,
                    sku VARCHAR(50) NOT NULL,
                    quantity INT NOT NULL,
                    total_amount DECIMAL(10,2) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            statement.executeUpdate("""
                INSERT INTO orders (user_id, merchant_id, sku, quantity, total_amount, status)
                VALUES ('user-123', 'merchant-456', 'SKU-001', 2, 199.98, 'PENDING')
                """);

            ResultSet rs = statement.executeQuery("SELECT * FROM orders WHERE sku = 'SKU-001'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("quantity")).isEqualTo(2);
            assertThat(rs.getBigDecimal("total_amount")).isEqualByComparingTo("199.98");
            assertThat(rs.getString("status")).isEqualTo("PENDING");
        }
    }
}
