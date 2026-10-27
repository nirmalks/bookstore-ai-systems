package com.nirmalks.audit_service.audit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nirmalks.audit_service.audit.entity.AuditLog;
import com.nirmalks.audit_service.audit.repository.AuditLogRepository;

import exceptions.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Sort;

@WebMvcTest(AuditController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
@TestPropertySource(properties = { "spring.config.import=", "spring.cloud.config.enabled=false",
		"spring.cloud.vault.enabled=false", "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false" })
@AutoConfigureMockMvc(addFilters = false)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuditControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuditLogRepository repository;

	@Test
	void audit_logs_response_should_include_audit_log_fields() throws Exception {
		AuditLog auditLog = new AuditLog();
		auditLog.setId(42L);
		auditLog.setOccurredAt(Instant.parse("2026-04-02T10:15:30Z"));
		auditLog.setEventId("evt-42");
		auditLog.setSchemaVersion("1.0");
		auditLog.setServiceName("checkout-service");
		auditLog.setEnvironment("test");
		auditLog.setAction("ORDER_PLACED");
		auditLog.setResource("ORDER");
		auditLog.setResourceId("order-42");
		auditLog.setStatus("SUCCESS");
		auditLog.setPrincipal("alice");
		auditLog.setTraceId("trace-42");
		auditLog.setSpanId("span-42");
		auditLog.setIdempotencyKey("idem-42");
		auditLog.setDetail("order placed");
		auditLog.setCreatedAt(Instant.parse("2026-04-02T10:16:00Z"));

		when(repository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/v1/audit"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(42))
			.andExpect(jsonPath("$.content[0].eventId").value("evt-42"))
			.andExpect(jsonPath("$.content[0].action").value("ORDER_PLACED"))
			.andExpect(jsonPath("$.content[0].resource").value("ORDER"))
			.andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
			.andExpect(jsonPath("$.content[0].principal").value("alice"))
			.andExpect(jsonPath("$.content[0].traceId").value("trace-42"))
			.andExpect(jsonPath("$.content[0].occurredAt").value("2026-04-02T10:15:30Z"))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void pageable_and_filters_should_be_forwarded_to_repository_search() throws Exception {
		when(repository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(Collections.emptyList()));

		mockMvc
			.perform(get("/api/v1/audit").param("principal", "alice")
				.param("action", "ORDER_PLACED")
				.param("resource", "ORDER")
				.param("status", "SUCCESS")
				.param("traceId", "trace-123")
				.param("from", "2026-04-01T00:00:00Z")
				.param("to", "2026-04-02T00:00:00Z")
				.param("page", "1")
				.param("size", "5")
				.param("sort", "occurredAt,desc"))
			.andExpect(status().isOk());

		ArgumentCaptor<Specification<AuditLog>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

		verify(repository).findAll(specificationCaptor.capture(), pageableCaptor.capture());

		Pageable pageable = pageableCaptor.getValue();
		assertEquals(1, pageable.getPageNumber());
		assertEquals(5, pageable.getPageSize());
		assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("occurredAt").getDirection());
		assertFalse(pageable.getSort().isUnsorted());
		assertNotNull(specificationCaptor.getValue());
	}

	@Test
	void invalid_from_parameter_should_return_bad_request() throws Exception {
		mockMvc.perform(get("/api/v1/audit").param("from", "not-an-instant"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Type mismatch"))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors[0]").value("Parameter 'from' should be of type 'Instant'"));
	}

}
