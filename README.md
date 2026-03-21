# xm-java

A Java learning project covering design patterns, algorithms, Spring Web, and production-grade practices for **order fulfillment**—including DDD, distributed transactions, and high-concurrency control.

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2, Spring Cloud |
| Distributed Transactions | Seata (TCC, Saga) |
| Distributed Lock | Redisson |
| Circuit Breaker | Resilience4j |
| Database | MySQL, JDBC |

---

## Module Structure

```
xm-java/
├── xm-base/          # Fundamentals: design patterns, algorithms, concurrency, I/O
├── xm-scenario/      # Complex scenarios: DDD, state machine, locks, distributed transactions
├── xm-spring/        # Spring Boot application: REST API, Seata, Redisson integration
└── pom.xml
```

### xm-base

- **Design Patterns** — 23 GoF patterns (creational, structural, behavioral)
- **Algorithms** — Data structures, sorting, graphs, dynamic programming
- **Concurrency** — Thread pools, AQS, synchronization primitives
- **I/O** — NIO, Netty examples

### xm-scenario

Order fulfillment as the unified domain, integrating DDD, state machines, locks, and distributed transactions:

- **DDD** — Order aggregate root, OrderLine entity, OrderId value object; payment/inventory anti-corruption layers
- **State Machine** — OrderState/OrderEvent transitions, TransitionGuard
- **Locks** — LockStrategy, LockPolicy, multi-granularity locks (order/inventory/user)
- **Distributed Transactions** — Local message table, TCC, Saga
- **Idempotent Consumption** — IdempotentMessageProcessor for Exactly-Once semantics
- **Circuit Breaker** — Resilience4j decorators for PaymentClient/InventoryClient
- **Timeout Handling** — Auto-cancel SUBMITTED orders after payment timeout

See [xm-scenario/README.md](xm-scenario/README.md) and [xm-scenario/docs/ARCHITECTURE.md](xm-scenario/docs/ARCHITECTURE.md) for details.

### xm-spring

Spring Boot application that wires all modules:

- **Order API** — CRUD, submit, pay, ship, cancel via `/order`
- **TCC/Saga** — `/order/{id}/submit-with-payment-tcc`, `/order/{id}/submit-with-payment-saga`
- **Third-Party Integrations** — Stripe, Twilio, SendGrid
- **Configuration** — Switchable lock, transaction, TCC, Saga, circuit breaker, timeout policies

---

## Quick Start

### Prerequisites

- JDK 21+
- Maven 3.8+

### Run the Application

```bash
mvn spring-boot:run -pl xm-spring
```

Or with explicit main class:

```bash
mvn exec:java -pl xm-spring -Dexec.mainClass="com.xm.web.XmBootStarter"
```

Default port: `8888`.

### Standalone Demo (No Spring)

```bash
mvn exec:java -pl xm-scenario -Dexec.mainClass="com.xm.scenario.ScenarioDemo"
```

### Order Flow (Without TCC/Saga)

```bash
# 1. Create draft order
curl -X POST http://localhost:8888/order/draft \
  -H "Content-Type: application/json" \
  -d '{"userId":"user1","lines":[{"skuId":"SKU-001","quantity":2,"price":99.00}]}'

# 2. Submit order
curl -X POST http://localhost:8888/order/{orderId}/submit

# 3. Mark as paid
curl -X POST "http://localhost:8888/order/{orderId}/paid?paymentId=PAY-001"

# 4. Ship
curl -X POST http://localhost:8888/order/{orderId}/ship
```

### Order Flow (TCC / Saga)

Configure `xm.scenario.tcc` or `xm.scenario.saga` in `application.yml` first:

```bash
# TCC: submit with payment
curl -X POST "http://localhost:8888/order/{orderId}/submit-with-payment-tcc?amount=198&userId=user1"

# Saga: submit with payment
curl -X POST "http://localhost:8888/order/{orderId}/submit-with-payment-saga?amount=198&userId=user1"
```

---

## Configuration

`xm.scenario` settings in `application.yml`:

| Property | Values | Description |
|----------|--------|--------------|
| `lock` | `memory` / `redisson` / unset | Lock strategy: in-memory, Redisson, or none |
| `transaction` | `memory` / `jdbc` / unset | Local message table: in-memory or JDBC |
| `tcc` | `learning` / `seata` | TCC: learning implementation or Seata |
| `saga` | `learning` / `seata` | Saga: learning implementation or Seata state machine |
| `circuit-breaker` | `true` / `false` | Wrap Payment/Inventory clients with Resilience4j |
| `order-timeout.enabled` | `true` / `false` | Auto-cancel SUBMITTED orders after timeout |
| `order-timeout.interval-ms` | number | Timeout scan interval (ms) |

Example:

```yaml
xm:
  scenario:
    lock: memory
    transaction: memory
    tcc: learning
    saga: learning
    circuit-breaker: false
    order-timeout:
      enabled: false
      interval-ms: 60000
```

---

## Database (Optional)

Required when using `xm.scenario.transaction=jdbc` or `xm.scenario.saga=seata`:

| Schema | Path | Purpose |
|--------|------|---------|
| Outbox | `xm-scenario/src/main/resources/db/outbox_schema.sql` | Local message table |
| Order | `xm-scenario/src/main/resources/db/order_schema.sql` | Order table |
| Saga | `xm-spring/src/main/resources/db/saga_schema.sql` | Seata Saga state machine |

---

## Project Layout

```
xm-java/
├── xm-base/                    # Fundamentals
│   └── src/main/java/com/xm/
│       ├── designpattern/      # Design patterns
│       ├── dp/                 # Dynamic programming
│       ├── graph/              # Graph algorithms
│       ├── multithread/        # Concurrency
│       ├── io/                 # NIO, Netty
│       └── ...
├── xm-scenario/                # Complex scenarios
│   └── src/main/java/com/xm/scenario/
│       ├── order/              # Order domain (DDD)
│       ├── payment/            # Payment anti-corruption layer
│       ├── inventory/          # Inventory anti-corruption layer
│       ├── concurrent/         # Locks
│       ├── transaction/        # Distributed transactions
│       └── shared/             # Idempotency, events, retry, etc.
├── xm-spring/                  # Application
│   └── src/main/java/com/xm/
│       ├── web/                # OrderController, etc.
│       ├── config/             # Configuration
│       ├── transaction/        # Seata TCC/Saga
│       └── service/            # Third-party integrations
└── README.md
```

---

## Capabilities

| Capability | Status | Location |
|------------|--------|----------|
| DDD Aggregate Root | ✅ | Order domain |
| State Machine + Guards | ✅ | OrderState |
| Optimistic Locking (CAS) | ✅ | Order.version |
| Multi-Granularity Locks | ✅ | LockPolicy |
| Distributed Lock | ✅ | Redisson |
| TCC | ✅ | Simple / Seata |
| Saga | ✅ | Simple / Seata JSON state machine |
| Local Message Table | ✅ | InMemory / Jdbc |
| Idempotent Consumption | ✅ | IdempotentMessageProcessor |
| Circuit Breaker | ✅ | Resilience4j |
| Timeout Auto-Transition | ✅ | OrderTimeoutScheduler |
| CQRS Read Model | ✅ | OrderQueryService |
| Domain Events | ✅ | DomainEventPublisher |

---

## Documentation

- [xm-scenario README](xm-scenario/README.md) — Learning path, package structure, xm-spring integration
- [xm-scenario Architecture](xm-scenario/docs/ARCHITECTURE.md) — Layering, distributed transactions, lock strategy
- [xm-scenario Roadmap](xm-scenario/ROADMAP.md) — Implemented and planned capabilities

---

## Author

Eddie MA
