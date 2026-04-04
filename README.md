# Order Fulfillment Reference Architecture

> DDD · Distributed Transactions · Multi-Granularity Locks

A reference implementation of an **order fulfillment system** demonstrating enterprise-grade architecture: Domain-Driven Design, distributed transactions (TCC & Saga), local message table, multi-granularity locking, circuit breaking, and idempotent consumption.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)

**CI:** `.github/workflows/ci.yml` runs `mvn verify` on push/PR. After you publish the repo, add a badge: `https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg`.

---

## Observability

| What | Where |
|------|--------|
| Health / metrics / Prometheus | `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` |
| Request trace id | `TraceIdFilter` — propagates `X-Trace-Id`, sets MDC key `traceId` for logs |
| App metadata | `/actuator/info` |

---

## Outbox pipeline

When `xm.scenario.transaction` is `memory` or `jdbc`, pending outbox rows are relayed on a schedule: scan → idempotent consumer (log) → `markSent`. See [docs/outbox-pipeline.md](docs/outbox-pipeline.md). Toggle with `xm.scenario.outbox-relay.enabled`.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Order Fulfillment System                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐     ┌──────────────────┐     ┌─────────────────────────────┐   │
│  │   REST API  │────▶│  Application     │────▶│  Domain (Order Aggregate)    │   │
│  │  (Controller)│     │  (Use Cases)     │     │  State Machine · Guards     │   │
│  └─────────────┘     └────────┬─────────┘     └──────────────┬──────────────┘   │
│                                │                             │                   │
│                                ▼                             ▼                   │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  Infrastructure: Repository · PaymentClient · InventoryClient · Locks   │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  Cross-Cutting: TCC/Saga Orchestrator · Local Message Table · Circuit Breaker   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Layering**: App → Domain → Repository (clean architecture). Order domain owns state machine and invariants; application layer orchestrates locks, events, and distributed transactions.

See [docs/architecture.md](docs/architecture.md) for the full architecture deep-dive.

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **TCC + Saga both** | TCC for short, strong-consistency flows; Saga for long-running, compensation-based flows. Configurable per use case. |
| **Local message table** | Outbox pattern for reliable event publishing without 2PC. Downstream consumes with idempotency. |
| **Lock at TCC entry** | LockPolicy wraps the orchestrator, not individual Try phases. Avoids redundant locking and keeps Try lightweight. |
| **Anti-corruption layers** | PaymentClient/InventoryClient abstract external services. Swap Stub → Feign for real integration. |
| **State machine in aggregate** | OrderState/OrderEvent + TransitionGuard keep transitions explicit and testable. |

ADRs: [docs/adr/](docs/adr/)

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2, Spring Cloud |
| Distributed Transactions | Seata (TCC, Saga) |
| Distributed Lock | Redisson |
| Circuit Breaker | Alibaba Sentinel |
| Database | MySQL, JDBC |

---

## Quick Start

### Prerequisites

- JDK 21+
- Maven 3.8+

### Run with Maven

```bash
mvn spring-boot:run -pl xm-spring
```

Default port: `8888`. Uses in-memory storage by default (no DB required).

### Run with Docker

```bash
docker compose up -d
```

App: http://localhost:8888

### Order Flow (Basic)

```bash
# 1. Create draft
curl -X POST http://localhost:8888/order/draft \
  -H "Content-Type: application/json" \
  -d '{"userId":"user1","lines":[{"skuId":"SKU-001","quantity":2,"price":99.00}]}'

# 2. Submit
curl -X POST http://localhost:8888/order/{orderId}/submit

# 3. Mark paid
curl -X POST "http://localhost:8888/order/{orderId}/paid?paymentId=PAY-001"

# 4. Ship
curl -X POST http://localhost:8888/order/{orderId}/ship
```

### Order Flow (TCC / Saga)

Configure `xm.scenario.tcc` or `xm.scenario.saga` in `application.yml`:

```bash
curl -X POST "http://localhost:8888/order/{orderId}/submit-with-payment-tcc?amount=198&userId=user1"
curl -X POST "http://localhost:8888/order/{orderId}/submit-with-payment-saga?amount=198&userId=user1"
```

---

## Module Structure

```
xm-java/
├── xm-scenario/      # Domain & infrastructure: DDD, state machine, locks, transactions
├── xm-spring/        # Spring Boot app: REST API, Seata, Redisson, config
├── xm-base/          # Extras: design patterns, algorithms (standalone)
└── docs/             # Architecture, ADRs
```

| Module | Responsibility |
|--------|----------------|
| **xm-scenario** | Order aggregate, OrderRepository, TCC/Saga participants, LockPolicy, LocalMessageTxSupport, IdempotentMessageProcessor |
| **xm-spring** | OrderController, Seata integration, Sentinel client decorators, OrderTimeoutScheduler |
| **xm-base** | Design patterns, algorithms, concurrency (optional) |

---

## Configuration

`xm.scenario` in `application.yml`:

| Property | Values | Description |
|----------|--------|--------------|
| `lock` | `memory` / `redisson` | In-memory or Redisson distributed lock |
| `transaction` | `memory` / `jdbc` | Local message table backend |
| `tcc` | `learning` / `seata` | TCC implementation |
| `saga` | `learning` / `seata` | Saga implementation |
| `circuit-breaker` | `true` / `false` | Sentinel degrade on Payment/Inventory clients |
| `order-timeout.enabled` | `true` / `false` | Auto-cancel SUBMITTED after payment timeout |
| `order-timeout.scan-interval-ms` | number | How often to scan (ms) |
| `order-timeout.payment-timeout` | Duration (e.g. `30m`) | Max wait in SUBMITTED before cancel |

---

## Capabilities

| Capability | Implementation |
|------------|-----------------|
| DDD Aggregate Root | Order, OrderLine, OrderId |
| State Machine + Guards | OrderState, OrderEvent, TransitionGuard |
| Optimistic Locking | Order.version, CAS in Repository |
| Multi-Granularity Locks | LockPolicy (order → inventory → user) |
| Distributed Lock | RedissonLockStrategy |
| TCC | SimpleTccCoordinator / Seata |
| Saga | SimpleSagaOrchestrator / Seata JSON state machine |
| Local Message Table | InMemory / JdbcLocalMessageTxSupport |
| Idempotent Consumption | IdempotentMessageProcessor |
| Circuit Breaker | Sentinel (`paymentClient`, `inventoryClient` resources) |
| Timeout Auto-Transition | OrderTimeoutScheduler |
| CQRS Read Model | OrderQueryService |
| Domain Events | DomainEventPublisher |

---

## Documentation

- [Architecture](docs/architecture.md) — Layering, modules, data flow
- [Outbox pipeline](docs/outbox-pipeline.md) — Local message table relay + idempotent consumer
- [ADRs](docs/adr/) — Architecture decision records
- [xm-scenario](xm-scenario/README.md) — Domain package structure
- [xm-scenario Roadmap](xm-scenario/ROADMAP.md) — Implemented & planned

---

## Database (Optional)

Required for `transaction=jdbc` or `saga=seata`:

- `xm-scenario/src/main/resources/db/outbox_schema.sql`
- `xm-scenario/src/main/resources/db/order_schema.sql`
- `xm-spring/src/main/resources/db/saga_schema.sql`

---

## License

MIT

## Author

Eddie MA
