package com.nirmalks.catalog_service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirmalks.catalog_service.book.entity.Book;
import com.nirmalks.catalog_service.book.repository.BookRepository;
import com.nirmalks.catalog_service.idempotency.repository.ProcessedEventRepository;
import dto.events.OrderCompletedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.redisson.api.RedissonClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = { "order-completed", "order-completed.DLT" })
@TestPropertySource(properties = {
		"kafka.consumer.enabled=true",
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@Import(OrderCompletedKafkaIT.TestKafkaConfig.class)
@DirtiesContext
class OrderCompletedKafkaIT {

	private static final String TOPIC_ORDER_COMPLETED = "order-completed";
	private static final String TOPIC_ORDER_COMPLETED_DLT = "order-completed.DLT";

	@MockitoBean
	private RedissonClient redissonClient;

	@Autowired
	private KafkaTemplate<String, Object> testKafkaTemplate;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private ProcessedEventRepository processedEventRepository;

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	private Long testBookId;

	@TestConfiguration
	static class TestKafkaConfig {

		@Bean
		public ProducerFactory<String, Object> testProducerFactory(
				@Value("${spring.embedded.kafka.brokers}") String bootstrapServers,
				ObjectMapper objectMapper) {
			Map<String, Object> props = new HashMap<>();
			props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
			props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

			JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper);
			serializer.setAddTypeInfo(false);

			return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), serializer);
		}

		@Bean
		public KafkaTemplate<String, Object> testKafkaTemplate(ProducerFactory<String, Object> testProducerFactory) {
			return new KafkaTemplate<>(testProducerFactory);
		}

	}

	@BeforeEach
	void setUp() {
		processedEventRepository.deleteAll();

		Book book = new Book();
		book.setTitle("Designing Data-Intensive Applications");
		book.setIsbn("9781449373320");
		book.setPrice(45.0);
		book.setStock(100);
		book.setPurchaseCount(0);
		book.setPublishedDate(LocalDate.of(2017, 3, 16));

		Book savedBook = bookRepository.save(book);
		testBookId = savedBook.getId();
	}

	@Test
	@DisplayName("Should process OrderCompletedEvent and increment book purchase_count")
	void shouldProcessOrderCompletedAndIncrementPopularity() {
		UUID eventId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		int quantityPurchased = 3;
		BigDecimal unitPrice = BigDecimal.valueOf(45.0);
		BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(quantityPurchased));

		OrderCompletedEvent event = new OrderCompletedEvent(
				eventId,
				orderId,
				100L,
				totalCost,
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(testBookId, quantityPurchased, unitPrice))
		);

		testKafkaTemplate.send(TOPIC_ORDER_COMPLETED, orderId.toString(), event);

		await().atMost(10, SECONDS).untilAsserted(() -> {
			assertThat(processedEventRepository.existsByEventId(eventId.toString())).isTrue();
			Book updatedBook = bookRepository.findById(testBookId).orElseThrow();
			assertThat(updatedBook.getPurchaseCount()).isEqualTo(quantityPurchased);
		});
	}

	@Test
	@DisplayName("Should prove idempotency: duplicate is ignored and sentinel guarantees ordering")
	void shouldHandleDuplicateEventIdempotentlyWithSentinelProof() {
		UUID eventId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		BigDecimal unitPrice = BigDecimal.valueOf(45.0);

		// 1. Send initial event: purchase 2 copies
		OrderCompletedEvent initialEvent = new OrderCompletedEvent(
				eventId,
				orderId,
				100L,
				unitPrice.multiply(BigDecimal.valueOf(2)),
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(testBookId, 2, unitPrice))
		);
		testKafkaTemplate.send(TOPIC_ORDER_COMPLETED, orderId.toString(), initialEvent);

		await().atMost(10, SECONDS).untilAsserted(() -> {
			assertThat(processedEventRepository.existsByEventId(eventId.toString())).isTrue();
			Book book = bookRepository.findById(testBookId).orElseThrow();
			assertThat(book.getPurchaseCount()).isEqualTo(2);
		});

		// 2. Publish duplicate event (exact same eventId and orderId key)
		testKafkaTemplate.send(TOPIC_ORDER_COMPLETED, orderId.toString(), initialEvent);

		// 3. Immediately publish sentinel event on SAME partition key (orderId) with purchase of 1 copy
		UUID sentinelEventId = UUID.randomUUID();
		OrderCompletedEvent sentinelEvent = new OrderCompletedEvent(
				sentinelEventId,
				orderId,
				100L,
				unitPrice.multiply(BigDecimal.valueOf(1)),
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(testBookId, 1, unitPrice))
		);
		testKafkaTemplate.send(TOPIC_ORDER_COMPLETED, orderId.toString(), sentinelEvent);

		// 4. Wait for sentinel to be processed
		await().atMost(10, SECONDS).untilAsserted(() -> {
			assertThat(processedEventRepository.existsByEventId(sentinelEventId.toString())).isTrue();
			Book book = bookRepository.findById(testBookId).orElseThrow();
			// Initial (2) + Duplicate (0) + Sentinel (1) = 3
			assertThat(book.getPurchaseCount()).isEqualTo(3);
			// Exactly 2 records in processed_events: initial + sentinel
			assertThat(processedEventRepository.count()).isEqualTo(2);
		});
	}

	@Test
	@DisplayName("Should reject poisoned event and route directly to order-completed.DLT")
	void shouldRoutePoisonMessageDirectlyToDltWithoutProcessing() {
		UUID poisonEventId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();

		// Poison payload: totalCost mismatch (declared 999.0 != calculated 45.0)
		OrderCompletedEvent poisonEvent = new OrderCompletedEvent(
				poisonEventId,
				orderId,
				100L,
				BigDecimal.valueOf(999.0),
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(testBookId, 1, BigDecimal.valueOf(45.0)))
		);

		// Set up DLT consumer
		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
				embeddedKafkaBroker.getBrokersAsString(), "test-dlt-verifier-group", "false");
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

		DefaultKafkaConsumerFactory<String, byte[]> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
		try (Consumer<String, byte[]> dltConsumer = consumerFactory.createConsumer()) {
			embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, TOPIC_ORDER_COMPLETED_DLT);

			// Send poisoned message
			testKafkaTemplate.send(TOPIC_ORDER_COMPLETED, orderId.toString(), poisonEvent);

			// Verify arrival in DLT topic
			await().atMost(10, SECONDS).untilAsserted(() -> {
				ConsumerRecords<String, byte[]> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofMillis(500));
				assertThat(records.count()).isGreaterThanOrEqualTo(1);
			});

			// Verify database was NOT modified
			Book book = bookRepository.findById(testBookId).orElseThrow();
			assertThat(book.getPurchaseCount()).isEqualTo(0);
			assertThat(processedEventRepository.existsByEventId(poisonEventId.toString())).isFalse();
		}
	}

}
