package com.nirmalks.checkout_service.order.service;

import com.nirmalks.checkout_service.order.entity.Outbox;
import dto.events.OrderCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxRelayServiceTest {

	@Mock
	private OutboxClaimService outboxClaimService;

	@Mock
	private OutboxService outboxService;

	@Mock
	private KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

	@InjectMocks
	private KafkaOutboxRelayService kafkaOutboxRelayService;

	private Outbox outboxRow;

	private OrderCompletedEvent event;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(kafkaOutboxRelayService, "batchSize", 10);
		ReflectionTestUtils.setField(kafkaOutboxRelayService, "leaseDurationSeconds", 60);

		UUID eventId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		event = new OrderCompletedEvent(eventId, orderId, 1L, BigDecimal.valueOf(50.0), Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(101L, 2, BigDecimal.valueOf(25.0))));

		outboxRow = new Outbox(orderId.toString(), "OrderCompleted", event);
		outboxRow.setId(42L);
		outboxRow.setStatus(Outbox.EventStatus.IN_PROGRESS);
	}

	@Test
	void relayOutboxMessages_publishesToKafkaAndMarksSentOnAck() {
		when(outboxClaimService.claimBatch(eq("OrderCompleted"), eq(10), eq(Duration.ofSeconds(60))))
			.thenReturn(List.of(outboxRow));

		CompletableFuture<SendResult<String, OrderCompletedEvent>> future = CompletableFuture
			.completedFuture(new SendResult<>(null, null));
		when(kafkaTemplate.send(eq("order-completed"), eq(event.orderId().toString()), any(OrderCompletedEvent.class)))
			.thenReturn(future);

		kafkaOutboxRelayService.relayOutboxMessages();

		verify(kafkaTemplate).send(eq("order-completed"), eq(event.orderId().toString()), any(OrderCompletedEvent.class));
		verify(outboxService).markPublished(42L);
		verify(outboxService, never()).handlePublishFailure(any(), any());
	}

	@Test
	void relayOutboxMessages_handlesPublishFailureWhenBrokerFails() {
		when(outboxClaimService.claimBatch(eq("OrderCompleted"), eq(10), eq(Duration.ofSeconds(60))))
			.thenReturn(List.of(outboxRow));

		CompletableFuture<SendResult<String, OrderCompletedEvent>> future = new CompletableFuture<>();
		future.completeExceptionally(new RuntimeException("Kafka broker unreachable"));
		when(kafkaTemplate.send(eq("order-completed"), eq(event.orderId().toString()), any(OrderCompletedEvent.class)))
			.thenReturn(future);

		kafkaOutboxRelayService.relayOutboxMessages();

		verify(kafkaTemplate).send(eq("order-completed"), eq(event.orderId().toString()), any(OrderCompletedEvent.class));
		verify(outboxService).handlePublishFailure(eq(42L), eq("Kafka broker unreachable"));
		verify(outboxService, never()).markPublished(any());
	}

	@Test
	void relayOutboxMessages_doesNothingWhenBatchIsEmpty() {
		when(outboxClaimService.claimBatch(eq("OrderCompleted"), eq(10), eq(Duration.ofSeconds(60))))
			.thenReturn(List.of());

		kafkaOutboxRelayService.relayOutboxMessages();

		verify(kafkaTemplate, never()).send(any(), any(), any());
		verify(outboxService, never()).markPublished(any());
		verify(outboxService, never()).handlePublishFailure(any(), any());
	}

}

