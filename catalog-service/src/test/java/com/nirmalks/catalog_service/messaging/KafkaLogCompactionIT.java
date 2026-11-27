package com.nirmalks.catalog_service.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "book-inventory-state" })
@TestPropertySource(properties = {
		"kafka.consumer.enabled=false",
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
class KafkaLogCompactionIT {

	private static final String TOPIC_COMPACTED = "book-inventory-state";

	@MockitoBean
	private RedissonClient redissonClient;

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	@Test
	@DisplayName("Should demonstrate log compaction semantics: state deduplication by key and tombstone handling")
	void shouldDemonstrateLogCompactionAndTombstoneSemantics() throws Exception {
		// Producer
		Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
		KafkaTemplate<String, String> template = new KafkaTemplate<>(
				new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), new StringSerializer()));

		// Produce sequence of state transitions for book-101 and book-102
		template.send(TOPIC_COMPACTED, "book-101", "{\"stock\": 100}").get();
		template.send(TOPIC_COMPACTED, "book-102", "{\"stock\": 50}").get();
		template.send(TOPIC_COMPACTED, "book-101", "{\"stock\": 95}").get();
		template.send(TOPIC_COMPACTED, "book-101", "{\"stock\": 90}").get(); // Latest state for book-101
		template.send(new ProducerRecord<>(TOPIC_COMPACTED, "book-102", null)).get(); // Tombstone (null value) for
																						// book-102

		// Consumer
		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("compaction-test-group", "true",
				embeddedKafkaBroker);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

		try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps)
				.createConsumer()) {
			TopicPartition topicPartition = new TopicPartition(TOPIC_COMPACTED, 0);
			consumer.assign(Collections.singletonList(topicPartition));
			consumer.seekToBeginning(Collections.singletonList(topicPartition));

			ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
			System.out.println(records);
			assertThat(records.count()).isGreaterThanOrEqualTo(5);

			// State Reconstruction (Materialized View pattern):
			// When consuming a compacted topic, a consumer reconstructs the current state:
			// - Overwriting older values for the same key
			// - Deleting the key when a tombstone (value == null) is encountered
			Map<String, String> materializedState = new LinkedHashMap<>();
			for (ConsumerRecord<String, String> record : records) {
				if (record.value() == null) {
					// Tombstone received -> remove key from materialized state store
					materializedState.remove(record.key());
				} else {
					materializedState.put(record.key(), record.value());
				}
			}

			// Verify materialized view: book-101 holds latest value {"stock": 90}, book-102
			// is deleted by tombstone
			assertThat(materializedState)
					.containsEntry("book-101", "{\"stock\": 90}")
					.doesNotContainKey("book-102");
		}
	}
}
