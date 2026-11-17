package com.nirmalks.catalog_service.messaging;

import com.nirmalks.catalog_service.book.service.SearchPopularityProcessor;
import dto.events.InvalidEventException;
import dto.events.OrderCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchPopularityConsumerTest {

	@Mock
	private SearchPopularityProcessor popularityProcessor;

	@Mock
	private Acknowledgment acknowledgment;

	@InjectMocks
	private SearchPopularityConsumer popularityConsumer;

	private OrderCompletedEvent validEvent;

	@BeforeEach
	void setUp() {
		validEvent = new OrderCompletedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				10L,
				BigDecimal.valueOf(30.0),
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(1L, 2, BigDecimal.valueOf(15.0)))
		);
	}

	@Test
	void consume_validEvent_processesAndAcknowledges() {
		ConsumerRecord<String, OrderCompletedEvent> record =
				new ConsumerRecord<>("order-completed", 0, 100L, "key-1", validEvent);

		popularityConsumer.consume(record, acknowledgment);

		verify(popularityProcessor).process(validEvent);
		verify(acknowledgment).acknowledge();
	}

	@Test
	void consume_invalidEvent_throwsExceptionWithoutProcessingOrAcknowledging() {
		OrderCompletedEvent invalidEvent = new OrderCompletedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				10L,
				BigDecimal.valueOf(999.0), // Mismatch with items total 15.0
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(1L, 1, BigDecimal.valueOf(15.0)))
		);

		ConsumerRecord<String, OrderCompletedEvent> record =
				new ConsumerRecord<>("order-completed", 0, 100L, "key-1", invalidEvent);

		assertThatThrownBy(() -> popularityConsumer.consume(record, acknowledgment))
				.isInstanceOf(InvalidEventException.class);

		verify(popularityProcessor, never()).process(any());
		verify(acknowledgment, never()).acknowledge();
	}

}
