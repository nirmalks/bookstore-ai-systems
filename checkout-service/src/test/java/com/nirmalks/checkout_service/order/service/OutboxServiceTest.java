package com.nirmalks.checkout_service.order.service;

import com.nirmalks.checkout_service.order.entity.Order;
import com.nirmalks.checkout_service.order.entity.OrderItem;
import com.nirmalks.checkout_service.order.entity.Outbox;
import com.nirmalks.checkout_service.order.repository.OutboxRepository;
import dto.OrderMessage;
import dto.events.OrderCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

	@InjectMocks
	private OutboxService outboxService;

	@Mock
	private OutboxRepository outboxRepository;

	@Test
	void saveOrderCreatedEvent_should_save_order_created_event_successfully() {
		OrderMessage orderMessage = new OrderMessage("1", "1", "1", 1L, "user@example.com", 0.0, LocalDateTime.now(),
				new ArrayList<>());
		outboxService.saveOrderCreatedEvent("1", orderMessage);
		verify(outboxRepository, times(1)).save(any(Outbox.class));
	}

	@Test
	void saveOrderCompletedEvent_should_construct_event_with_calculated_subtotal_and_save() {
		Order order = new Order();
		order.setId(100L);
		order.setUserId(5L);

		OrderItem item1 = new OrderItem();
		item1.setBookId(1L);
		item1.setQuantity(2);
		item1.setPrice(15.0);

		OrderItem item2 = new OrderItem();
		item2.setBookId(2L);
		item2.setQuantity(1);
		item2.setPrice(20.0);

		order.setItems(List.of(item1, item2));

		outboxService.saveOrderCompletedEvent(order);

		ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
		verify(outboxRepository).save(captor.capture());

		Outbox saved = captor.getValue();
		assertThat(saved.getAggregateId()).isEqualTo("100");
		assertThat(saved.getEventType()).isEqualTo("OrderCompleted");
		assertThat(saved.getStatus()).isEqualTo(Outbox.EventStatus.PENDING);

		OrderCompletedEvent event = saved.getPayloadAs(OrderCompletedEvent.class);
		assertThat(event.userId()).isEqualTo(5L);
		// totalCost: (2 * 15.0) + (1 * 20.0) = 50.0
		assertThat(event.totalCost()).isEqualByComparingTo(BigDecimal.valueOf(50.0));
		assertThat(event.items()).hasSize(2);
	}

	@Test
	void markPublished_should_transition_to_sent_and_clear_lease() {
		Outbox row = new Outbox("100", "OrderCompleted", "{}");
		row.setId(1L);
		row.setStatus(Outbox.EventStatus.IN_PROGRESS);
		row.setLockedUntil(Instant.now().plusSeconds(60));

		when(outboxRepository.findById(1L)).thenReturn(Optional.of(row));

		outboxService.markPublished(1L);

		assertThat(row.getStatus()).isEqualTo(Outbox.EventStatus.SENT);
		assertThat(row.getLockedUntil()).isNull();
		verify(outboxRepository).save(row);
	}

	@Test
	void handlePublishFailure_should_schedule_backoff_when_under_max_retries() {
		Outbox row = new Outbox("100", "OrderCompleted", "{}");
		row.setId(1L);
		row.setStatus(Outbox.EventStatus.IN_PROGRESS);
		row.setRetryCount(1);
		row.setLockedUntil(Instant.now().plusSeconds(60));

		when(outboxRepository.findById(1L)).thenReturn(Optional.of(row));

		Instant beforeFailure = Instant.now();
		outboxService.handlePublishFailure(1L, "Connection reset");

		assertThat(row.getStatus()).isEqualTo(Outbox.EventStatus.PENDING);
		assertThat(row.getRetryCount()).isEqualTo(2);
		assertThat(row.getLockedUntil()).isNull();
		// 2^2 = 4 seconds backoff
		assertThat(row.getNextRetryAt()).isAfterOrEqualTo(beforeFailure.plusSeconds(3));
		verify(outboxRepository).save(row);
	}

	@Test
	void handlePublishFailure_should_mark_failed_when_max_retries_reached() {
		Outbox row = new Outbox("100", "OrderCompleted", "{}");
		row.setId(1L);
		row.setStatus(Outbox.EventStatus.IN_PROGRESS);
		row.setRetryCount(4); // next retry = 5 >= maxRetries (5)

		when(outboxRepository.findById(1L)).thenReturn(Optional.of(row));

		outboxService.handlePublishFailure(1L, "Permanent failure");

		assertThat(row.getStatus()).isEqualTo(Outbox.EventStatus.FAILED);
		assertThat(row.getRetryCount()).isEqualTo(5);
		assertThat(row.getLockedUntil()).isNull();
		verify(outboxRepository).save(row);
	}

}
