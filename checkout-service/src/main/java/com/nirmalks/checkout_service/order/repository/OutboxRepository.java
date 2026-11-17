package com.nirmalks.checkout_service.order.repository;

import com.nirmalks.checkout_service.order.entity.Outbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

	List<Outbox> findByStatusOrderByCreatedAtAsc(Outbox.EventStatus status, Pageable pageable);

	List<Outbox> findByEventTypeAndStatusOrderByCreatedAtAsc(String eventType, Outbox.EventStatus status,
			Pageable pageable);

	@Query(value = """
			SELECT * FROM outbox
			WHERE event_type = :eventType
			  AND ((status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= :now))
			       OR (status = 'IN_PROGRESS' AND locked_until < :now))
			ORDER BY created_at ASC
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<Outbox> findAndLockBatchForClaim(@Param("eventType") String eventType, @Param("now") Instant now,
			@Param("batchSize") int batchSize);

}
