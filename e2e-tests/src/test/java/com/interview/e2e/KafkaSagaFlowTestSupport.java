package com.interview.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
abstract class KafkaSagaFlowTestSupport {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
            .withStartupTimeout(Duration.ofMinutes(5));

    protected String randomTopic(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    protected void publishMessage(String topic, String key, Object message) throws Exception {
        publishMessages(topic, key, List.of(message));
    }

    protected void publishMessages(String topic, String key, List<?> messages) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            for (Object message : messages) {
                producer.send(new ProducerRecord<>(topic, key,
                        UnifiedSagaFixtures.OBJECT_MAPPER.writeValueAsString(message))).get();
            }
            producer.flush();
        }
    }

    protected List<JsonNode> consumeMessages(String topic, int expectedMessages) throws Exception {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            consumer.subscribe(Collections.singletonList(topic));

            List<JsonNode> messages = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (messages.size() < expectedMessages && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    messages.add(UnifiedSagaFixtures.OBJECT_MAPPER.readTree(record.value()));
                }
            }

            assertThat(messages).hasSize(expectedMessages);
            return messages;
        }
    }

    private Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "saga-e2e-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }
}
