package com.nirmalks.checkout_service.order.service;

import com.nirmalks.checkout_service.order.entity.Outbox;
import com.nirmalks.checkout_service.order.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxClaimService {

	private static final Logger logger = LoggerFactory.getLogger(OutboxClaimService.class);

	private final OutboxRepository outboxRepository;

	public OutboxClaimService(OutboxRepository outboxRepository) {
		this.outboxRepository = outboxRepository;
	}

	@Transactional
	public List<Outbox> claimBatch(String eventType, int batchSize, Duration leaseDuration) {
		Instant now = Instant.now();
		Instant leaseExpiresAt = now.plus(leaseDuration);

		List<Outbox> claimedRows = outboxRepository.findAndLockBatchForClaim(eventType, now, batchSize);
		if (claimedRows.isEmpty()) {
			return claimedRows;
		}

		logger.debug("Claiming {} outbox events of type '{}' with lease until {}", claimedRows.size(), eventType,
				leaseExpiresAt);

		for (Outbox row : claimedRows) {
			row.setStatus(Outbox.EventStatus.IN_PROGRESS);
			row.setLockedUntil(leaseExpiresAt);
		}

		return outboxRepository.saveAll(claimedRows);
	}

}

