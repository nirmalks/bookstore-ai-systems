package com.nirmalks.catalog_service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dto.events.InvalidEventException;
import dto.events.OrderCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

	@Value("${spring.kafka.bootstrap-servers:localhost:9094}")
	private String bootstrapServers;

	@Value("${spring.kafka.consumer.max-poll-records:50}")
	private int maxPollRecords;

	@Value("${spring.kafka.consumer.properties.max.poll.interval.ms:300000}")
	private int maxPollIntervalMs;

	@Value("${spring.kafka.listener.concurrency:3}")
	private int concurrency;

	@Bean
	public ConsumerFactory<String, OrderCompletedEvent> orderCompletedConsumerFactory() {
		ObjectMapper consumerMapper = new ObjectMapper();
		consumerMapper.registerModule(new JavaTimeModule());
		consumerMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		JsonDeserializer<OrderCompletedEvent> jsonDeserializer = new JsonDeserializer<>(OrderCompletedEvent.class,
				consumerMapper);
		jsonDeserializer.addTrustedPackages("dto.events");
		jsonDeserializer.setUseTypeHeaders(false);

		ErrorHandlingDeserializer<OrderCompletedEvent> errorHandlingValueDeserializer = new ErrorHandlingDeserializer<>(
				jsonDeserializer);
		ErrorHandlingDeserializer<String> errorHandlingKeyDeserializer = new ErrorHandlingDeserializer<>(
				new StringDeserializer());

		Map<String, Object> props = new HashMap<>();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "search-ranking-group");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
		props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);

		return new DefaultKafkaConsumerFactory<>(props, errorHandlingKeyDeserializer, errorHandlingValueDeserializer);
	}

	@Bean
	public ProducerFactory<Object, Object> dltProducerFactory(ObjectMapper objectMapper) {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

		// Delegating Key Serializer: handles String and raw byte[]
		Map<Class<?>, Serializer<?>> keyDelegates = new HashMap<>();
		keyDelegates.put(String.class, new StringSerializer());
		keyDelegates.put(byte[].class, new ByteArraySerializer());
		DelegatingByTypeSerializer keySerializer = new DelegatingByTypeSerializer(keyDelegates);

		// Delegating Value Serializer: handles raw byte[] poison messages and OrderCompletedEvent
		Map<Class<?>, Serializer<?>> valueDelegates = new HashMap<>();
		valueDelegates.put(byte[].class, new ByteArraySerializer());
		valueDelegates.put(OrderCompletedEvent.class, new JsonSerializer<>(objectMapper));
		DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(valueDelegates);

		return new DefaultKafkaProducerFactory<>(props, keySerializer, valueSerializer);
	}

	@Bean
	public KafkaTemplate<Object, Object> dltKafkaTemplate(ProducerFactory<Object, Object> dltProducerFactory) {
		return new KafkaTemplate<>(dltProducerFactory);
	}

	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> dltKafkaTemplate) {
		// Topic and DLT both configured with 3 partitions; preserves partition mapping
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
				(record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

		// 3 retries (1 initial + 3 retries = 4 total attempts) with 1000ms delay
		FixedBackOff backOff = new FixedBackOff(1000L, 3);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

		// Advance source offset ONLY upon successful DLT recovery publication
		errorHandler.setCommitRecovered(true);

		// Non-retryable exceptions route directly to DLT without retrying
		errorHandler.addNotRetryableExceptions(DeserializationException.class, MessageConversionException.class,
				InvalidEventException.class);

		return errorHandler;
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> kafkaListenerContainerFactory(
			ConsumerFactory<String, OrderCompletedEvent> orderCompletedConsumerFactory,
			DefaultErrorHandler kafkaErrorHandler) {
		ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(orderCompletedConsumerFactory);
		factory.setCommonErrorHandler(kafkaErrorHandler);
		factory.setConcurrency(concurrency);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		return factory;
	}

}
