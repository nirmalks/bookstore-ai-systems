package com.nirmalks.checkout_service.order.entity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dto.OrderMessage;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox")
public class Outbox {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "aggregate_type", nullable = false)
	private String aggregateType = "Order";

	@Column(name = "aggregate_id", nullable = false)
	private String aggregateId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", columnDefinition = "JSONB", nullable = false)
	private JsonNode payload;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false)
	private EventStatus status = EventStatus.PENDING;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "retry_count", nullable = false)
	private int retryCount = 0;

	@Column(name = "next_retry_at")
	private Instant nextRetryAt;

	public enum EventStatus {

		PENDING, IN_PROGRESS, SENT, FAILED

	}


	public Outbox(String aggregateId, String eventType, Object payload) {
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		setPayload(payload);
	}

	public Outbox() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public void setAggregateType(String aggregateType) {
		this.aggregateType = aggregateType;
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public OrderMessage getPayload() {
		return getPayloadAs(OrderMessage.class);
	}

	public JsonNode getRawPayload() {
		return payload;
	}


	public <T> T getPayloadAs(Class<T> clazz) {
		if (this.payload == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.treeToValue(this.payload, clazz);
		} catch (Exception e) {
			throw new RuntimeException("Failed to deserialize outbox payload to " + clazz.getSimpleName(), e);
		}
	}

	public void setPayload(Object payload) {
		if (payload == null) {
			this.payload = null;
		} else if (payload instanceof JsonNode jsonNode) {
			this.payload = jsonNode;
		} else {
			this.payload = OBJECT_MAPPER.valueToTree(payload);
		}
	}

	public EventStatus getStatus() {
		return status;
	}

	public void setStatus(EventStatus status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}


	public Instant getLockedUntil() {
		return lockedUntil;
	}


	public void setLockedUntil(Instant lockedUntil) {
		this.lockedUntil = lockedUntil;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}


	public Instant getNextRetryAt() {
		return nextRetryAt;
	}


	public void setNextRetryAt(Instant nextRetryAt) {
		this.nextRetryAt = nextRetryAt;
	}

}