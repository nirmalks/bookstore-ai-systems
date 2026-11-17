package com.nirmalks.catalog_service.messaging;

import com.nirmalks.catalog_service.book.service.SearchPopularityProcessor;
import dto.events.EventValidator;
import dto.events.OrderCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SearchPopularityConsumer {

	private static final Logger logger = LoggerFactory.getLogger(SearchPopularityConsumer.class);

	public static final String TOPIC_ORDER_COMPLETED = "order-completed";

	public static final String GROUP_SEARCH_RANKING = "search-ranking-group";

	private final SearchPopularityProcessor popularityProcessor;

	public SearchPopularityConsumer(SearchPopularityProcessor popularityProcessor) {
		this.popularityProcessor = popularityProcessor;
	}

	@KafkaListener(topics = TOPIC_ORDER_COMPLETED, groupId = GROUP_SEARCH_RANKING,
			containerFactory = "kafkaListenerContainerFactory", autoStartup = "${kafka.consumer.enabled:true}")
	public void consume(ConsumerRecord<String, OrderCompletedEvent> record, Acknowledgment ack) {
		OrderCompletedEvent event = record.value();
		logger.info("Received OrderCompletedEvent from partition {} offset {}: key={}", record.partition(),
				record.offset(), record.key());

		// 1. Structural validation (throws InvalidEventException -> DLT)
		EventValidator.validate(event);

		// 2. Transactional processing (DB commit happens inside this call)
		popularityProcessor.process(event);

		// 3. Acknowledge offset ONLY after the DB transaction has successfully committed
		if (ack != null) {
			ack.acknowledge();
		}
	}

}
