package com.nirmalks.checkout_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dto.events.OrderCompletedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    @Test
    @DisplayName("Verify Producer Durability, Idempotence, and Throughput Configurations")
    void shouldConfigureProducerDurabilityAndThroughputEssentials() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9094");
        ReflectionTestUtils.setField(config, "compressionType", "snappy");
        ReflectionTestUtils.setField(config, "lingerMs", 20);
        ReflectionTestUtils.setField(config, "batchSize", 32768);
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", 120000);

        ProducerFactory<String, OrderCompletedEvent> factory = config.orderCompletedProducerFactory(new ObjectMapper());
        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);

        DefaultKafkaProducerFactory<String, OrderCompletedEvent> defaultFactory = (DefaultKafkaProducerFactory<String, OrderCompletedEvent>) factory;

        Map<String, Object> props = defaultFactory.getConfigurationProperties();

        // 1. Durability: acks=all
        assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");

        // 2. Single-Partition Deduplication: enable.idempotence=true
        assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);

        // 3. Retry configuration: Integer.MAX_VALUE with delivery timeout
        assertThat(props.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(Integer.MAX_VALUE);
        assertThat(props.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(120000);

        // 4. In-flight requests <= 5 preserves ordering with idempotence
        int maxInFlight = (Integer) props.get(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION);
        assertThat(maxInFlight).isLessThanOrEqualTo(5);

        // 5. Compression: snappy reduces bandwidth and storage
        assertThat(props.get(ProducerConfig.COMPRESSION_TYPE_CONFIG)).isEqualTo("snappy");

        // 6. Throughput batching: linger.ms = 20ms and batch.size = 32KB
        assertThat(props.get(ProducerConfig.LINGER_MS_CONFIG)).isEqualTo(20);
        assertThat(props.get(ProducerConfig.BATCH_SIZE_CONFIG)).isEqualTo(32768);
    }

    @Test
    @DisplayName("Verify Micrometer producer listener registration when MeterRegistry is present")
    void shouldRegisterMicrometerProducerListener() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9094");
        ReflectionTestUtils.setField(config, "compressionType", "snappy");
        ReflectionTestUtils.setField(config, "lingerMs", 20);
        ReflectionTestUtils.setField(config, "batchSize", 32768);
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", 120000);

        io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        ProducerFactory<String, OrderCompletedEvent> factory = config.orderCompletedProducerFactory(new ObjectMapper(),
                java.util.Optional.of(meterRegistry));

        DefaultKafkaProducerFactory<String, OrderCompletedEvent> defaultFactory = (DefaultKafkaProducerFactory<String, OrderCompletedEvent>) factory;

        assertThat(defaultFactory.getListeners()).isNotEmpty();
    }
}
