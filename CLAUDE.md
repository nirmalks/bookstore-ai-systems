# CLAUDE.md — Architecture & Engineering Guardrails

## 1. Project Identity & Purpose
- **Repository:** `bookstore-ai-systems` (V3)
- **Role:** Event-driven, RAG-enabled, agentic bookstore microservices architecture.
- **Career Anchor:** Demonstrating Senior / Lead AI Systems Architect competency (high-throughput data ingestion, resilient event streams, vector search with `pgvector`, agentic tool-calling, and enterprise observability).

---

## 2. Core Technology Stack
- **Language / Runtime:** Java 25 (Java 21 LTS compatible features), GraalVM JDK
- **Framework:** Spring Boot 3.5.x, Spring Cloud 2025.x
- **Build Tool:** Apache Maven (multi-module)
- **Database & Vectors:** PostgreSQL 15+ (Primary/Replica setup), `pgvector` extension, Flyway migrations
- **Messaging & Ingestion:** Apache Kafka (target async streaming), RabbitMQ (legacy event fallback)
- **Caching & State:** Redis 7 (Alpine)
- **Security & Secrets:** HashiCorp Vault (AppRole dynamic secrets), Spring Authorization Server, JWT
- **Observability:** OpenTelemetry Collector, Prometheus, Grafana, Jaeger, Loki

---

## 3. The 4 AI Pairing Protocols
1. **You Own the Architecture:** The human architect defines schemas, service contracts, and failure modes. The AI implements boilerplate, tests, and configurations within those bounds.
2. **One Feature Per Session:** Execute atomic, scoped tasks (e.g., "Implement Kafka consumer for book embeddings with unit tests", not "Build the whole event flow").
3. **Plan Before Code:** For any architectural modification, propose the design, schema changes, and edge cases first. Await explicit review before writing code.
4. **Review Like a Principal Engineer:** Verify:
   - Are there N+1 query risks?
   - Are transaction boundaries explicit (`@Transactional(readOnly = true)`)?
   - Is there idempotency on consumer handlers?
   - Are DTOs strictly isolated from JPA entities?
   - Is error handling resilient (circuit breakers, dead-letter queues)?

---

## 4. Architectural Guardrails & Coding Standards

### 4.1 Clean Boundaries & DTO Isolation
- **Controllers:** Thin HTTP adapters. Zero business logic. Must validate incoming request payloads (`@Valid`).
- **DTOs / Records:** All request/response contracts must be Java `record` types.
- **JPA Entities:** NEVER expose `@Entity` classes in controller signatures or message payloads. Always map to/from DTOs.
- **Domain Services:** Encapsulate business logic. Enforce invariants.

### 4.2 Database & Data Access Standards
- **Flyway Migrations:** All schema updates must be committed as versioned migrations in `src/main/resources/db/migration/V<timestamp>__<description>.sql`.
- **Zero Hibernate DDL:** `spring.jpa.hibernate.ddl-auto` must remain `validate` or `none` in production configurations.
- **Query Optimization:**
  - Index all foreign keys and frequently filtered columns.
  - Eliminate N+1 queries using `JOIN FETCH`, `@EntityGraph`, or projection interfaces.
  - Mark read-only queries with `@Transactional(readOnly = true)` to avoid unnecessary Hibernate dirty-checking overhead.
  - Profile queries with `EXPLAIN (ANALYZE, BUFFERS)` before committing repository methods.

### 4.3 Event-Driven Architecture & Messaging
- **Decoupled Writes:** Ingestion and heavy processing (such as vector embedding generation) must happen asynchronously via event queues.
- **Idempotent Consumers:** Every message handler must accept an `idempotencyKey` / message ID and check a deduplication store (Redis or DB unique constraint) before executing side effects.
- **Dead-Letter Topics (DLT):** Failed messages must route to a dead-letter topic after bounded retries with exponential backoff. Never block the consumer partition.

### 4.4 AI, Vector Search & RAG Rules
- **Asynchronous Embeddings:** Never generate vector embeddings within synchronous ACID web transactions. Book creation must publish an event; the embedding worker consumes it asynchronously.
- **Vector Storage:** Store embeddings in PostgreSQL using `pgvector` with HNSW / IVFFlat indexes tuned for cosine distance (`vector_cosine_ops`).
- **Structured Outputs:** Force LLM responses into strict JSON Schemas (using Spring AI `BeanOutputConverter` or structured output APIs). Never parse unstructured markdown/strings in downstream microservices.
- **Semantic Caching:** Cache vector search queries and LLM summaries in Redis with TTLs to minimize latency and token costs.
- **Agentic Tool Calling:** Treat tool calls as untrusted inputs. Validate permissions (User vs Admin), payload constraints, and state transitions before executing backend mutations.

### 4.5 Observability & Metrics
- Expose Actuator health and Prometheus metrics on all services (`/actuator/health`, `/actuator/prometheus`).
- Track AI metrics: LLM request latency, prompt/completion token usage, vector query latency, and Redis cache hit/miss ratios.
- Propagate W3C Trace Context headers across HTTP requests and message headers for distributed tracing in Jaeger.

---

## 5. Standard Build & Development Commands

### Maven Multi-Module
```bash
# Clean build without running slow tests
mvn clean compile -DskipTests

# Build and run all unit tests
mvn clean test

# Build a specific service module
mvn clean package -pl catalog-service -am -DskipTests
```

### Docker Compose Stack
```bash
# Start complete infrastructure and microservices in background
docker compose up -d

# Check health status of all containers
docker compose ps

# View logs for a specific service
docker compose logs -f catalog-service

# Rebuild and restart a single service after code changes
docker compose up -d --no-deps --build catalog-service
```

### Local Endpoints Reference
- **API Gateway:** `http://localhost:8080`
- **Eureka Discovery Dashboard:** `http://localhost:8761`
- **Config Server:** `http://localhost:8888`
- **Auth Server:** `http://localhost:9001`
- **Catalog Service Swagger:** `http://localhost:8082/swagger-ui/index.html` (direct: `:8201`)
- **User Service Swagger:** `http://localhost:8081/swagger-ui/index.html` (direct: `:8101`)
- **Checkout Service Swagger:** `http://localhost:8083/swagger-ui/index.html` (direct: `:8301`)
- **RabbitMQ Management:** `http://localhost:15672` (guest/guest)
- **Grafana Dashboard:** `http://localhost:3000` (admin/admin)
- **Prometheus UI:** `http://localhost:9092`
