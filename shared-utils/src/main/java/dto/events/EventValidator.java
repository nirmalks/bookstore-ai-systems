package dto.events;

import java.math.BigDecimal;

public class EventValidator {

	public static void validate(OrderCompletedEvent event) {
		if (event == null) {
			throw new InvalidEventException("Event payload cannot be null");
		}
		if (event.eventId() == null || event.orderId() == null) {
			throw new InvalidEventException("Event ID and Order ID must not be null");
		}
		if (event.userId() == null) {
			throw new InvalidEventException("User ID must not be null");
		}
		if (event.completedAt() == null) {
			throw new InvalidEventException("CompletedAt timestamp must not be null");
		}
		if (event.items() == null || event.items().isEmpty()) {
			throw new InvalidEventException("Order items must not be empty");
		}
		if (event.totalCost() == null || event.totalCost().compareTo(BigDecimal.ZERO) < 0) {
			throw new InvalidEventException("Total cost must be non-negative");
		}

		BigDecimal calculatedTotal = BigDecimal.ZERO;
		for (OrderCompletedEvent.OrderItemCompleted item : event.items()) {
			if (item == null) {
				throw new InvalidEventException("Order item cannot be null");
			}
			if (item.bookId() == null || item.quantity() <= 0) {
				throw new InvalidEventException("Invalid bookId or non-positive quantity");
			}
			if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
				throw new InvalidEventException("Unit price must be non-negative");
			}
			calculatedTotal = calculatedTotal.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
		}

		// Explicit semantics: totalCost represents line items subtotal in this contract
		if (event.totalCost().compareTo(calculatedTotal) != 0) {
			throw new InvalidEventException(
					"Total cost mismatch: declared " + event.totalCost() + " but items sum to " + calculatedTotal);
		}
	}

}
