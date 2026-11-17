package com.nirmalks.checkout_service.order.service;

import com.nirmalks.checkout_service.order.entity.Outbox;
import dto.events.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class KafkaOutboxRelayService {

	private static final Logger logger = LoggerFactory.getLogger(KafkaOutboxRelayService.class);

	public static final String TOPIC_ORDER_COMPLETED = "order-completed";

	public static final String EVENT_TYPE_ORDER_COMPLETED = "OrderCompleted";

	private final OutboxClaimService outboxClaimService;

	private final OutboxService outboxService;

	private final KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

	@Value("${outbox.kafka.batch-size:50}")
	private int batchSize;

	@Value("${outbox.kafka.lease-duration-seconds:60}")
	private int leaseDurationSeconds;

	public KafkaOutboxRelayService(OutboxClaimService outboxClaimService, OutboxService outboxService,
			KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate) {
		this.outboxClaimService = outboxClaimService;
		this.outboxService = outboxService;
		this.kafkaTemplate = kafkaTemplate;
	}

	@Scheduled(fixedDelayString = "${outbox.kafka.poll-interval:1000}")
	public void relayOutboxMessages() {
		List<Outbox> batch = outboxClaimService.claimBatch(EVENT_TYPE_ORDER_COMPLETED, batchSize,
				Duration.ofSeconds(leaseDurationSeconds));

		if (batch.isEmpty()) {
			return;
		}

		logger.info("Claimed {} outbox event(s) of type '{}' for Kafka publishing", batch.size(),
				EVENT_TYPE_ORDER_COMPLETED);

		for (Outbox row : batch) {
			try {
				OrderCompletedEvent event = row.getPayloadAs(OrderCompletedEvent.class);
				String partitionKey = event.orderId().toString();

				kafkaTemplate.send(TOPIC_ORDER_COMPLETED, partitionKey, event).whenComplete((result, ex) -> {
					if (ex == null) {
						outboxService.markPublished(row.getId());
						logger.info("Published outbox event {} for order {} to Kafka topic '{}'", row.getId(),
								partitionKey, TOPIC_ORDER_COMPLETED);
					}
					else {
						logger.error("Failed to publish outbox event {} to Kafka", row.getId(), ex);
						outboxService.handlePublishFailure(row.getId(), ex.getMessage());
					}
				});
			}
			catch (Exception e) {
				logger.error("Failed to process outbox row {} for Kafka relay", row.getId(), e);
				outboxService.handlePublishFailure(row.getId(), e.getMessage());
			}
		}
	}

}

