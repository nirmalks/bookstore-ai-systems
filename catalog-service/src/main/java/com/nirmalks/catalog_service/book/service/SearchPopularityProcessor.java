package com.nirmalks.catalog_service.book.service;

import com.nirmalks.catalog_service.book.repository.BookRepository;
import com.nirmalks.catalog_service.idempotency.repository.ProcessedEventRepository;
import dto.events.OrderCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SearchPopularityProcessor {

	private static final Logger logger = LoggerFactory.getLogger(SearchPopularityProcessor.class);

	private final ProcessedEventRepository processedEventRepository;

	private final BookRepository bookRepository;

	public SearchPopularityProcessor(ProcessedEventRepository processedEventRepository,
			BookRepository bookRepository) {
		this.processedEventRepository = processedEventRepository;
		this.bookRepository = bookRepository;
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public boolean process(OrderCompletedEvent event) {
		String eventId = event.eventId().toString();

		// Atomic insertion: returns true if new event, false if duplicate
		boolean isFirstExecution = processedEventRepository.insertIfAbsent(eventId, "order-completed", Instant.now());

		if (!isFirstExecution) {
			logger.warn("Duplicate OrderCompletedEvent ignored in search-ranking-group: {}", eventId);
			return false;
		}

		// Increment purchase_count for each book in the order
		for (OrderCompletedEvent.OrderItemCompleted item : event.items()) {
			bookRepository.incrementPurchaseCount(item.bookId(), item.quantity());
			logger.info("Incremented purchase count for book ID {} by {}", item.bookId(), item.quantity());
		}

		return true;
	}

}
