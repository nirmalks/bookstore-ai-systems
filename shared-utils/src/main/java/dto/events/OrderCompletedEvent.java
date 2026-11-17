package dto.events;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCompletedEvent(
		UUID eventId,
		UUID orderId,
		Long userId,
		BigDecimal totalCost,
		Instant completedAt,
		List<OrderItemCompleted> items
) implements Serializable {

	public record OrderItemCompleted(
			Long bookId,
			int quantity,
			BigDecimal unitPrice
	) implements Serializable {}

}

