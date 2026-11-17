package com.nirmalks.checkout_service.order.service;

import com.nirmalks.checkout_service.order.entity.Order;
import com.nirmalks.checkout_service.order.entity.OrderItem;
import com.nirmalks.checkout_service.order.entity.Outbox;
import com.nirmalks.checkout_service.order.repository.OutboxRepository;
import dto.OrderMessage;
import dto.events.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

	private static final Logger logger = LoggerFactory.getLogger(OutboxService.class);

	private static final int DEFAULT_MAX_RETRIES = 5;

	private final OutboxRepository outboxRepository;

	@Value("${outbox.kafka.max-retries:5}")
	private int maxRetries = DEFAULT_MAX_RETRIES;

	public OutboxService(OutboxRepository outboxRepository) {
		this.outboxRepository = outboxRepository;
	}

	@Transactional
	public void saveOrderCreatedEvent(String orderIdString, OrderMessage message) {
		logger.info("Outbox event save is triggered from order creation");
		Outbox outboxEvent = new Outbox(orderIdString, "OrderCreated", message);
		outboxRepository.save(outboxEvent);
	}

	@Transactional
	public void saveOrderCompletedEvent(Order order) {
		logger.info("Saving OrderCompletedEvent to outbox for order ID: {}", order.getId());

		UUID eventId = UUID.randomUUID();
		UUID orderUuid = UUID.nameUUIDFromBytes(("order-" + order.getId()).getBytes(StandardCharsets.UTF_8));

		List<OrderCompletedEvent.OrderItemCompleted> items = new ArrayList<>();
		BigDecimal totalCost = BigDecimal.ZERO;

		if (order.getItems() != null) {
			for (OrderItem item : order.getItems()) {
				BigDecimal unitPrice = BigDecimal.valueOf(item.getPrice());
				items.add(new OrderCompletedEvent.OrderItemCompleted(item.getBookId(), item.getQuantity(), unitPrice));
				totalCost = totalCost.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
			}
		}

		OrderCompletedEvent event = new OrderCompletedEvent(eventId, orderUuid, order.getUserId(), totalCost,
				Instant.now(), items);

		Outbox outboxEvent = new Outbox(order.getId().toString(), "OrderCompleted", event);
		outboxRepository.save(outboxEvent);
	}

	@Transactional
	public void markPublished(Long outboxId) {
		outboxRepository.findById(outboxId).ifPresent(row -> {
			row.setStatus(Outbox.EventStatus.SENT);
			row.setLockedUntil(null);
			outboxRepository.save(row);
			logger.debug("Marked outbox event {} as SENT", outboxId);
		});
	}

	@Transactional
	public void handlePublishFailure(Long outboxId, String errorMessage) {
		outboxRepository.findById(outboxId).ifPresent(row -> {
			int currentRetry = row.getRetryCount() + 1;
			row.setRetryCount(currentRetry);

			if (currentRetry >= maxRetries) {
				row.setStatus(Outbox.EventStatus.FAILED);
				row.setLockedUntil(null);
				logger.error("Outbox event {} exceeded max retries ({}), marked FAILED. Error: {}", outboxId,
						maxRetries, errorMessage);
			} else {
				// Exponential backoff: 2^retry seconds (capped at 60s)
				long backoffSeconds = Math.min(60, (long) Math.pow(2, currentRetry));
				Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
				row.setStatus(Outbox.EventStatus.PENDING);
				row.setNextRetryAt(nextRetry);
				row.setLockedUntil(null);
				logger.warn(
						"Outbox event {} publish failed (retry {}/{}). Rescheduled at {}. Error: {}",
						outboxId, currentRetry, maxRetries, nextRetry, errorMessage);
			}
			outboxRepository.save(row);
		});
	}

}
