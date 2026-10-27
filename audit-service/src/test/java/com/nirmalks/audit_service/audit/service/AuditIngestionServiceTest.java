package com.nirmalks.audit_service.audit.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.nirmalks.audit_service.audit.entity.AuditLog;
import com.nirmalks.audit_service.audit.repository.AuditLogRepository;

import logging.AuditEvent;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuditIngestionServiceTest {

	@Mock
	private AuditLogRepository repository;

	@InjectMocks
	private AuditIngestionService auditIngestionService;

	private AuditEvent auditEvent;

	@BeforeEach
	void setUp() {
		auditEvent = new AuditEvent("v1", "evt-123", Instant.parse("2026-04-02T10:15:30Z"), "catalog-service", "test",
				"BOOK_CREATED", "BOOK", "book-1", "SUCCESS", "admin", "trace-123", "span-456", "idem-789",
				"Created book", null, null);
	}

	@Test
	void ingest_will_save_audit_log_with_event_details() {
		ArgumentCaptor<AuditLog> auditLogArgumentCaptor = ArgumentCaptor.forClass(AuditLog.class);

		auditIngestionService.ingest(auditEvent);

		verify(repository).save(auditLogArgumentCaptor.capture());
		AuditLog savedAuditLog = auditLogArgumentCaptor.getValue();
		assertEquals("evt-123", savedAuditLog.getEventId());
		assertEquals("v1", savedAuditLog.getSchemaVersion());
		assertEquals(Instant.parse("2026-04-02T10:15:30Z"), savedAuditLog.getOccurredAt());
		assertEquals("catalog-service", savedAuditLog.getServiceName());
		assertEquals("test", savedAuditLog.getEnvironment());
		assertEquals("BOOK_CREATED", savedAuditLog.getAction());
		assertEquals("BOOK", savedAuditLog.getResource());
		assertEquals("book-1", savedAuditLog.getResourceId());
		assertEquals("SUCCESS", savedAuditLog.getStatus());
		assertEquals("admin", savedAuditLog.getPrincipal());
		assertEquals("trace-123", savedAuditLog.getTraceId());
		assertEquals("span-456", savedAuditLog.getSpanId());
		assertEquals("idem-789", savedAuditLog.getIdempotencyKey());
		assertEquals("Created book", savedAuditLog.getDetail());
		assertNull(savedAuditLog.getErrorCode());
		assertNull(savedAuditLog.getErrorMessage());
	}

	@Test
	void ingest_will_ignore_duplicate_event_if_repository_throws_data_integrity_violation_exception() {
		doThrow(new DataIntegrityViolationException("duplicate")).when(repository).save(any(AuditLog.class));

		assertDoesNotThrow(() -> auditIngestionService.ingest(auditEvent));

		verify(repository).save(any(AuditLog.class));
	}

}
