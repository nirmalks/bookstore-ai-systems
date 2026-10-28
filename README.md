# Bookstore AI Systems (V3)

[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.x-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![PostgreSQL & pgvector](https://img.shields.io/badge/PostgreSQL-15%20%2B%20pgvector-4169E1?logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![Kafka](https://img.shields.io/badge/Streaming-Apache%20Kafka-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Cache-Redis%207-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Vault](https://img.shields.io/badge/Secrets-HashiCorp%20Vault-000000?logo=vault&logoColor=white)](https://www.vaultproject.io/)
[![Prometheus & Grafana](https://img.shields.io/badge/Observability-Prometheus%20%2B%20Grafana-F46800?logo=grafana&logoColor=white)](https://grafana.com/)

An enterprise-grade, event-driven, RAG-enabled microservices platform architected for high-throughput data processing, vector embeddings in PostgreSQL (`pgvector`), asynchronous message streaming, and agentic AI tool-calling.

---

## 1. Architecture Overview

```mermaid
flowchart TD
    Client[Web / Mobile Client] -->|HTTP / REST| Gateway[Spring Cloud API Gateway :8080]
    
    subgraph Core Control Plane
        Gateway --> Discovery[Eureka Service Discovery :8761]
        Gateway --> ConfigServer[Spring Cloud Config Server :8888]
        Gateway --> AuthServer[Spring Authorization Server :9001]
        Vault[(HashiCorp Vault :8206<br/>AppRole & Dynamic Secrets)] -.-> CoreServices[All Microservices]
    end

    subgraph Domain Microservices
        Gateway --> UserService[User Service :8101]
        Gateway --> CatalogService[Catalog Service :8201]
        Gateway --> CheckoutService[Checkout Service :8301]
        Gateway --> NotificationService[Notification Service :8401]
        Gateway --> AuditService[Audit Service :8501]
    end

    subgraph Data & Storage Layer
        CatalogService --> CatalogDB[(PostgreSQL Primary :5434<br/>Catalog Data + pgvector)]
        CatalogDB -.-> CatalogReplica[(PostgreSQL Replica :5438<br/>Read Scaling)]
        UserService --> UserDB[(PostgreSQL User DB :5433)]
        CheckoutService --> CheckoutDB[(PostgreSQL Checkout DB :5435)]
        NotificationService --> NotificationDB[(PostgreSQL Notification DB :5436)]
        AuditService --> AuditDB[(PostgreSQL Audit DB :5437)]
        CatalogService --> RedisCache[(Redis 7 :6379<br/>Semantic Search Cache)]
    end

    subgraph Event Streaming & Async AI
        CatalogService -->|BookCreatedEvent| KafkaBus[Event Streaming / Kafka & RabbitMQ]
        KafkaBus --> AIWorker[Async Embedding Consumer]
        AIWorker -->|Generate Embeddings| LLMProvider[Spring AI / Vector Models]
        LLMProvider -->|Vector Write| CatalogDB
    end

    subgraph Observability & Metrics
        CoreServices -.-> OTel[OpenTelemetry Collector :4317]
        OTel --> Prometheus[Prometheus :9092]
        OTel --> Jaeger[Jaeger Distributed Tracing :16686]
        Prometheus --> Grafana[Grafana Dashboard :3000]
    end
```

---

## 2. Key Capabilities & Technical Highlights

- **Event-Driven AI Pipeline:** Decouples heavy AI vector generation from transactional write paths using asynchronous event streaming, dead-letter topics, and consumer idempotency keys.
- **pgvector & Semantic RAG:** Native vector embeddings stored alongside relational catalog entities in PostgreSQL, indexed via HNSW / IVFFlat for sub-50ms cosine similarity searches.
- **Semantic Caching with Redis:** Reduces LLM token costs and accelerates frequent semantic queries by caching query vector embeddings and search results.
- **Structured LLM Outputs:** Enforces strict JSON Schema contracts using Spring AI's structured output converters to guarantee deterministic integration with downstream microservices.
- **Agentic Tool Calling:** Context-aware `BookAssistant` capable of orchestrating multi-step backend actions (e.g., checking stock, managing cart, invoking checkout) with permission guards.
- **Enterprise Observability:** Full distributed tracing (OpenTelemetry/Jaeger) and custom Prometheus metrics tracking LLM token burn, vector query latency, and cache hit ratios.
- **Zero-Trust Secrets Management:** Dynamic database credentials and TLS certificates provisioned automatically via HashiCorp Vault with AppRole authentication.

---

## 3. Microservice & Port Matrix

| Service | Container / Direct Port | Gateway Route | Purpose |
| :--- | :--- | :--- | :--- |
| **API Gateway** | `8080` | `/*` | Edge routing, JWT validation, rate limiting |
| **Discovery Service** | `8761` | `/eureka/**` | Spring Cloud Netflix Eureka service registry |
| **Config Server** | `8888` | — | Centralized Spring Cloud Configuration |
| **Auth Server** | `9001` | `/auth/**` | OAuth2 / OIDC token issuance & user login |
| **User Service** | `8101` | `/api/v1/users/**` | User management and profile lifecycle |
| **Catalog Service** | `8201` | `/api/v1/books/**` | Catalog, inventory, and vector semantic search |
| **Checkout Service** | `8301` | `/api/v1/orders/**` | Cart management, order processing, payments |
| **Notification Service** | `8401` | — | Asynchronous customer email/SMS notifications |
| **Audit Service** | `8501` | — | Immutable audit logging of critical business events |
| **HashiCorp Vault** | `8206` | — | Dynamic secrets & AppRole credential manager |
| **PostgreSQL (Catalog Primary)** | `5434` | — | Relational book data + `pgvector` store |
| **PostgreSQL (Catalog Replica)** | `5438` | — | Read-replica for high-throughput catalog queries |
| **Redis** | `6379` | — | Semantic search cache & distributed rate limiting |
| **RabbitMQ / Kafka** | `5672` (UI: `15672`) | — | Message broker & event streaming |
| **Prometheus** | `9092` | — | Metric aggregation & AI latency tracking |
| **Grafana** | `3000` | — | Production monitoring & AI dashboards (`admin/admin`) |

---

## 4. Quickstart Guide

### Prerequisites
- Docker & Docker Compose v2+
- Java 21 or Java 25 (GraalVM recommended)
- Maven 3.8+

### 1. Bootstrapping the Cluster
The infrastructure automatically generates self-signed certificates and initializes HashiCorp Vault AppRoles via container lifecycle hooks:

```bash
# Start all microservices, databases, and monitoring
docker compose up -d

# Verify all containers are healthy
docker compose ps
```

### 2. Run the Baseline Smoke Test
Validate that all discovery endpoints, gateway routing, and microservice health checks are responsive:

```powershell
# PowerShell (Windows)
powershell -ExecutionPolicy Bypass -File scripts/baseline-smoke-test.ps1
```
```bash
# Bash (Linux/macOS)
./scripts/baseline-smoke-test.sh
```

---

## 5. Interactive Dashboards & APIs

- **Eureka Registry:** [http://localhost:8761](http://localhost:8761)
- **API Gateway Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Catalog Service Swagger:** [http://localhost:8201/swagger-ui/index.html](http://localhost:8201/swagger-ui/index.html)
- **User Service Swagger:** [http://localhost:8101/swagger-ui/index.html](http://localhost:8101/swagger-ui/index.html)
- **Checkout Service Swagger:** [http://localhost:8301/swagger-ui/index.html](http://localhost:8301/swagger-ui/index.html)
- **RabbitMQ Management UI:** [http://localhost:15672](http://localhost:15672) (`guest` / `guest`)
- **Grafana Metrics Dashboard:** [http://localhost:3000](http://localhost:3000) (`admin` / `admin`)
- **Prometheus Metrics:** [http://localhost:9092](http://localhost:9092)

---

## 6. Architecture Rules & Governance

All new code, schema additions, and AI integrations must adhere to the engineering guidelines codified in [CLAUDE.md](file:///c:/projects/showcase/bookstore-ai-systems/CLAUDE.md):
- **Clean DTO Isolation:** JPA entities are never exposed in API signatures.
- **Zero N+1 Queries:** Enforce `JOIN FETCH` and `@EntityGraph`; read paths must use `@Transactional(readOnly = true)`.
- **Flyway Migrations:** All schema changes must be versioned under `src/main/resources/db/migration`.
- **Consumer Resilience:** Idempotency keys and dead-letter topics are required on all event handlers.

