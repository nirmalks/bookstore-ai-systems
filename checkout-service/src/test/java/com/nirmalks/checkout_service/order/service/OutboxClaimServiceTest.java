package com.nirmalks.checkout_service.order.service;

import com.nirmalks.checkout_service.order.entity.Outbox;
import com.nirmalks.checkout_service.order.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {

	@Mock
	private OutboxRepository outboxRepository;

	@InjectMocks
	private OutboxClaimService outboxClaimService;

	private Outbox pendingEvent;

	@BeforeEach
	void setUp() {
		pendingEvent = new Outbox("order-101", "OrderCompleted", "{\"orderId\":\"order-101\"}");
		pendingEvent.setId(1L);
		pendingEvent.setStatus(Outbox.EventStatus.PENDING);
	}

	@Test
	void claimBatch_claimsPendingRowsAndSetsInProgressWithLease() {
		when(outboxRepository.findAndLockBatchForClaim(eq("OrderCompleted"), any(Instant.class), eq(10)))
			.thenReturn(List.of(pendingEvent));
		when(outboxRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Instant beforeClaim = Instant.now();
		List<Outbox> claimed = outboxClaimService.claimBatch("OrderCompleted", 10, Duration.ofSeconds(60));

		assertThat(claimed).hasSize(1);
		Outbox claimedRow = claimed.get(0);
		assertThat(claimedRow.getStatus()).isEqualTo(Outbox.EventStatus.IN_PROGRESS);
		assertThat(claimedRow.getLockedUntil()).isAfterOrEqualTo(beforeClaim.plusSeconds(59));
		verify(outboxRepository).saveAll(List.of(pendingEvent));
	}

	@Test
	void claimBatch_returnsEmptyWhenNoRowsEligible() {
		when(outboxRepository.findAndLockBatchForClaim(eq("OrderCompleted"), any(Instant.class), eq(10)))
			.thenReturn(List.of());

		List<Outbox> claimed = outboxClaimService.claimBatch("OrderCompleted", 10, Duration.ofSeconds(60));

		assertThat(claimed).isEmpty();
		verify(outboxRepository, never()).saveAll(any());
	}

}

