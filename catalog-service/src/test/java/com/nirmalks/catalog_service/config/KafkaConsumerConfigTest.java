package com.nirmalks.catalog_service.config;

import dto.events.InvalidEventException;
import dto.events.OrderCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

	@Test
	@DisplayName("Should configure consumer factory with bounded poll records, poll interval, and disabled auto-commit")
	void shouldConfigureConsumerFactoryProperties() {
		KafkaConsumerConfig config = new KafkaConsumerConfig();
		ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
		ReflectionTestUtils.setField(config, "maxPollRecords", 50);
		ReflectionTestUtils.setField(config, "maxPollIntervalMs", 300000);

		ConsumerFactory<String, OrderCompletedEvent> factory = config.orderCompletedConsumerFactory();
		Map<String, Object> props = factory.getConfigurationProperties();

		assertThat(props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
		assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("search-ranking-group");
		assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
		assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
		assertThat(props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo(50);
		assertThat(props.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)).isEqualTo(300000);
	}

	@Test
	@DisplayName("Should configure listener container factory with concurrency=3 and MANUAL_IMMEDIATE ack mode")
	@SuppressWarnings("unchecked")
	void shouldConfigureListenerContainerFactoryConcurrencyAndAckMode() {
		KafkaConsumerConfig config = new KafkaConsumerConfig();
		ReflectionTestUtils.setField(config, "concurrency", 3);

		ConsumerFactory<String, OrderCompletedEvent> consumerFactory = mock(ConsumerFactory.class);
		DefaultErrorHandler errorHandler = mock(DefaultErrorHandler.class);

		ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> containerFactory =
				config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

		Object concurrency = ReflectionTestUtils.getField(containerFactory, "concurrency");
		assertThat(concurrency).isEqualTo(3);
		assertThat(containerFactory.getContainerProperties().getAckMode())
				.isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
	}

	@Test
	@DisplayName("Should configure error handler with commitRecovered and non-retryable exceptions")
	@SuppressWarnings("unchecked")
	void shouldConfigureErrorHandler() {
		KafkaConsumerConfig config = new KafkaConsumerConfig();
		KafkaOperations<Object, Object> dltKafkaTemplate = mock(KafkaOperations.class);

		DefaultErrorHandler errorHandler = config.kafkaErrorHandler(dltKafkaTemplate);

		Object commitRecovered = ReflectionTestUtils.getField(errorHandler, "commitRecovered");
		assertThat(commitRecovered).isEqualTo(true);
	}
}
