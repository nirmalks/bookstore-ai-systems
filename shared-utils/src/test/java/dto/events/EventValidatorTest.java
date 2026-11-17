package dto.events;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventValidatorTest {

	@Test
	void validate_validEvent_passesWithoutException() {
		OrderCompletedEvent event = new OrderCompletedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				10L,
				BigDecimal.valueOf(50.0),
				Instant.now(),
				List.of(
						new OrderCompletedEvent.OrderItemCompleted(1L, 2, BigDecimal.valueOf(15.0)),
						new OrderCompletedEvent.OrderItemCompleted(2L, 1, BigDecimal.valueOf(20.0))
				)
		);

		assertThatCode(() -> EventValidator.validate(event)).doesNotThrowAnyException();
	}

	@Test
	void validate_nullEvent_throwsInvalidEventException() {
		assertThatThrownBy(() -> EventValidator.validate(null))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("Event payload cannot be null");
	}

	@Test
	void validate_missingEventId_throwsInvalidEventException() {
		OrderCompletedEvent event = new OrderCompletedEvent(
				null,
				UUID.randomUUID(),
				10L,
				BigDecimal.valueOf(20.0),
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(1L, 1, BigDecimal.valueOf(20.0)))
		);

		assertThatThrownBy(() -> EventValidator.validate(event))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("Event ID and Order ID must not be null");
	}

	@Test
	void validate_emptyItems_throwsInvalidEventException() {
		OrderCompletedEvent event = new OrderCompletedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				10L,
				BigDecimal.ZERO,
				Instant.now(),
				List.of()
		);

		assertThatThrownBy(() -> EventValidator.validate(event))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("Order items must not be empty");
	}

	@Test
	void validate_totalCostMismatch_throwsInvalidEventException() {
		OrderCompletedEvent event = new OrderCompletedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				10L,
				BigDecimal.valueOf(100.0), // items sum to 30.0 != 100.0
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(1L, 2, BigDecimal.valueOf(15.0)))
		);

		assertThatThrownBy(() -> EventValidator.validate(event))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("Total cost mismatch");
	}

	@Test
	void validate_negativeQuantity_throwsInvalidEventException() {
		OrderCompletedEvent event = new OrderCompletedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				10L,
				BigDecimal.valueOf(15.0),
				Instant.now(),
				List.of(new OrderCompletedEvent.OrderItemCompleted(1L, -1, BigDecimal.valueOf(15.0)))
		);

		assertThatThrownBy(() -> EventValidator.validate(event))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("Invalid bookId or non-positive quantity");
	}

}
