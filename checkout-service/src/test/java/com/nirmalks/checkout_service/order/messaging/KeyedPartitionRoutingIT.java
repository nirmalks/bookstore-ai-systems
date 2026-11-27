package com.nirmalks.checkout_service.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dto.events.OrderCompletedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = { "order-completed" })
@TestPropertySource(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
class KeyedPartitionRoutingIT {

	private static final Logger logger = LoggerFactory.getLogger(KeyedPartitionRoutingIT.class);

	private static final String TOPIC = "order-completed";

	@MockitoBean
	private RedissonClient redissonClient;

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	@Autowired
	private KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	@DisplayName("Should route identical keys to the same partition and distribute distinct keys across partitions")
	void shouldVerifyKeyedPartitionRoutingAndOrdering() throws Exception {
		UUID targetOrderId = UUID.randomUUID();
		String targetKey = targetOrderId.toString();

		// 1. Send multiple events with identical key (targetOrderId)
		List<CompletableFuture<SendResult<String, OrderCompletedEvent>>> sameKeyFutures = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			OrderCompletedEvent event = new OrderCompletedEvent(
					UUID.randomUUID(),
					targetOrderId,
					10L,
					BigDecimal.valueOf(100.0),
					Instant.now(),
					List.of(new OrderCompletedEvent.OrderItemCompleted(1L, 1, BigDecimal.valueOf(100.0))));
			sameKeyFutures.add(kafkaTemplate.send(TOPIC, targetKey, event));
		}

		// Wait for sends to complete and verify partition assignment
		int targetPartition = -1;
		for (CompletableFuture<SendResult<String, OrderCompletedEvent>> future : sameKeyFutures) {
			SendResult<String, OrderCompletedEvent> result = future.get();
			int partition = result.getRecordMetadata().partition();
			if (targetPartition == -1) {
				targetPartition = partition;
			} else {
				// All messages with the same orderId MUST map to the same partition!
				assertThat(partition).isEqualTo(targetPartition);
			}
		}

		// 2. Send events with varying keys and collect partitions
		Set<Integer> observedPartitions = new HashSet<>();
		observedPartitions.add(targetPartition);

		for (int i = 0; i < 30; i++) {
			UUID otherOrderId = UUID.randomUUID();
			OrderCompletedEvent event = new OrderCompletedEvent(
					UUID.randomUUID(),
					otherOrderId,
					20L,
					BigDecimal.valueOf(50.0),
					Instant.now(),
					List.of(new OrderCompletedEvent.OrderItemCompleted(2L, 1, BigDecimal.valueOf(50.0))));
			SendResult<String, OrderCompletedEvent> result = kafkaTemplate.send(TOPIC, otherOrderId.toString(), event)
					.get();
			observedPartitions.add(result.getRecordMetadata().partition());
		}

		// 30 random UUIDs across 3 partitions must hit multiple partitions (murmur2
		// hash distribution)
		assertThat(observedPartitions.size()).isGreaterThan(1);

		// 3. Verify consumer receives identical-key messages in strict offset sequence
		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("keyed-routing-test-group", "true",
				embeddedKafkaBroker);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		JsonDeserializer<OrderCompletedEvent> valueDeserializer = new JsonDeserializer<>(OrderCompletedEvent.class,
				objectMapper);
		valueDeserializer.addTrustedPackages("*");

		try (Consumer<String, OrderCompletedEvent> consumer = new DefaultKafkaConsumerFactory<>(consumerProps,
				new StringDeserializer(), valueDeserializer).createConsumer()) {

			TopicPartition assignedPartition = new TopicPartition(TOPIC, targetPartition);
			consumer.assign(Collections.singletonList(assignedPartition));
			consumer.seekToBeginning(Collections.singletonList(assignedPartition));

			ConsumerRecords<String, OrderCompletedEvent> records = KafkaTestUtils.getRecords(consumer,
					Duration.ofSeconds(10));
			List<ConsumerRecord<String, OrderCompletedEvent>> targetRecords = new ArrayList<>();

			for (ConsumerRecord<String, OrderCompletedEvent> record : records) {
				logger.info("Received record: partition={}, offset={}, key={}",
						record.partition(), record.offset(), record.key());
				if (targetKey.equals(record.key())) {
					targetRecords.add(record);
				}
			}

			assertThat(targetRecords).hasSize(5);
			// Verify monotonic offset ordering within the partition
			for (int i = 1; i < targetRecords.size(); i++) {
				assertThat(targetRecords.get(i).offset()).isGreaterThan(targetRecords.get(i - 1).offset());
			}

			// 4. Verify Micrometer has registered Kafka producer metrics
			List<String> kafkaMetrics = meterRegistry.getMeters().stream()
					.map(m -> m.getId().getName())
					.filter(name -> name.startsWith("kafka.producer"))
					.distinct()
					.sorted()
					.toList();

			assertThat(kafkaMetrics).isNotEmpty();
			logger.info("Registered Kafka Micrometer metric count: {}", kafkaMetrics.size());
			kafkaMetrics.stream().limit(5)
					.forEach(metric -> logger.info("  Sample Kafka metric: {}", metric));
		}
	}
}
