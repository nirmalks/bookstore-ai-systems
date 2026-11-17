package com.nirmalks.catalog_service.book.service;

import com.nirmalks.catalog_service.book.repository.BookRepository;
import com.nirmalks.catalog_service.idempotency.repository.ProcessedEventRepository;
import dto.events.OrderCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchPopularityProcessorTest {

	@Mock
	private ProcessedEventRepository processedEventRepository;

	@Mock
	private BookRepository bookRepository;

	@InjectMocks
	private SearchPopularityProcessor popularityProcessor;

	private OrderCompletedEvent event;

	private UUID eventId;

	@BeforeEach
	void setUp() {
		eventId = UUID.randomUUID();
		event = new OrderCompletedEvent(
				eventId,
				UUID.randomUUID(),
				10L,
				BigDecimal.valueOf(50.0),
				Instant.now(),
				List.of(
						new OrderCompletedEvent.OrderItemCompleted(1L, 2, BigDecimal.valueOf(15.0)),
						new OrderCompletedEvent.OrderItemCompleted(2L, 1, BigDecimal.valueOf(20.0))
				)
		);
	}

	@Test
	void process_firstExecution_incrementsPurchaseCountAndReturnsTrue() {
		when(processedEventRepository.insertIfAbsent(eq(eventId.toString()), eq("order-completed"), any(Instant.class)))
				.thenReturn(true);

		boolean processed = popularityProcessor.process(event);

		assertThat(processed).isTrue();
		verify(bookRepository).incrementPurchaseCount(1L, 2);
		verify(bookRepository).incrementPurchaseCount(2L, 1);
	}

	@Test
	void process_duplicateEvent_ignoresDeduplicationAndReturnsFalse() {
		when(processedEventRepository.insertIfAbsent(eq(eventId.toString()), eq("order-completed"), any(Instant.class)))
				.thenReturn(false);

		boolean processed = popularityProcessor.process(event);

		assertThat(processed).isFalse();
		verify(bookRepository, never()).incrementPurchaseCount(any(), any(int.class));
	}

}
