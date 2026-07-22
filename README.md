# Order Fulfillment Reference Architecture

> DDD · Distributed Transactions · Multi-Granularity Locks

A reference implementation of an **order fulfillment system** demonstrating enterprise-grade architecture: Domain-Driven Design, distributed transactions (TCC & Saga), local message table, multi-granularity locking, circuit breaking, and idempotent consumption.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)

**CI:** `.github/workflows/ci.yml` runs `mvn verify` on push/PR. After you publish the repo, add a badge: `https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg`.

---

## Observability

三件套：**Metrics（Prometheus + Grafana）+ Tracing（Jaeger）+ Logs（ELK）**

```
本地 JVM (:8888)
  ├─[OTLP push]──▶ Jaeger      → http://localhost:16686  链路追踪
  ├─[被拉取]◀──── Prometheus   → http://localhost:9090   指标 + 告警
  │               └─▶ Grafana  → http://localhost:3000   可视化 Dashboard
  └─[写文件]──▶ logs/app.log
                 └─▶ Filebeat
                       └─▶ Elasticsearch → Kibana → http://localhost:5601  日志搜索
```

### 快速启动

```bash
# 1. 启动所有基础设施（Kafka / Jaeger / Prometheus / Grafana / ES / Kibana / Filebeat）
docker compose up -d

# 2. 启动应用（MANAGEMENT_TRACING_ENABLED=true 才会发 Span 到 Jaeger）
cd xm-spring && MANAGEMENT_TRACING_ENABLED=true mvn spring-boot:run

# 3. 产生数据
curl -X POST "http://localhost:8888/demo/orders/submit"
# 返回 {"orderId":"A1B2C3D4","status":"SUBMITTED"}
curl -X POST "http://localhost:8888/demo/orders/A1B2C3D4/pay"
curl -X POST "http://localhost:8888/demo/orders/submit?fail=true"   # 模拟失败
```

### 各工具使用说明

| 工具 | 地址 | 看什么 |
|------|------|--------|
| **Prometheus** | `localhost:9090` | Status → Targets 看采集状态；Graph 写 PromQL；Alerts 看 SLO 规则 |
| **Grafana** | `localhost:3000` (admin/admin) | Dashboards → Order Fulfillment，第三行是订单业务指标 |
| **Jaeger** | `localhost:16686` | 服务选 `xm-service`，搜 Traces，点进去看 Span 瀑布图 |
| **Kibana** | `localhost:5601` | 首次需建 Data View（见下方），然后 Discover 搜日志 |

### Kibana 首次配置（只需做一次）

```
Management → Data Views → Create data view
  Name:              xm-service
  Index pattern:     xm-service-*
  Timestamp field:   @timestamp
→ Save
```

之后在 Discover 页面用 KQL 搜索：

```
# 所有订单提交日志
message: "Order submitted"

# 某笔订单（直接用 curl 返回的 orderId）
message: "A1B2C3D4"

# 所有错误
level: "ERROR"

# 某个接口的所有请求
path: "/demo/orders/submit"
```

### 关键日志字段（Kibana 可过滤）

| 字段 | 来源 | 说明 |
|------|------|------|
| `@timestamp` | Logback | 日志时间 |
| `level` | Logback | INFO / WARN / ERROR |
| `message` | Logback | 日志正文 |
| `logger` | Logback | 打日志的类名 |
| `traceId` | Micrometer Tracing 自动注入 MDC | 与 Jaeger 的 Trace ID 相同，可跨工具关联 |
| `spanId` | Micrometer Tracing 自动注入 MDC | 当前 Span |
| `requestId` | `MdcLoggingFilter` | 每个 HTTP 请求的唯一 ID，也出现在响应头 `X-Request-Id` |
| `method` / `path` | `MdcLoggingFilter` | HTTP 方法和路径 |

### 告警规则（prometheus-rules.yml）

| 告警名 | 条件 | 级别 |
|--------|------|------|
| `HighHttpErrorRate` | HTTP 5xx 错误率 > 0.1% 持续 2 分钟 | critical |
| `HighP99Latency` | HTTP P99 延迟 > 200ms 持续 2 分钟 | warning |
| `OrderProcessingSlowP99` | 订单处理 P99 > 1s 持续 2 分钟 | warning |
| `HighOrderSubmitFailureRate` | 订单提交失败率 > 5% 持续 2 分钟 | critical |

触发告警测试：

```bash
# 连续发失败请求，触发 HighOrderSubmitFailureRate
for i in {1..30}; do curl -s -X POST "http://localhost:8888/demo/orders/submit?fail=true" > /dev/null; done
# 在 http://localhost:9090/alerts 观察 PENDING → FIRING 变化
```

### 可观测性接入原理

| 组件 | 接入方式 | 你做了什么 |
|------|---------|-----------|
| Metrics | Spring Boot Actuator 自动暴露 `/actuator/prometheus`，Prometheus 主动拉取 | pom.xml 加 `micrometer-registry-prometheus` |
| Tracing | OTel SDK 自动拦截 HTTP 请求，push Span 到 Jaeger | pom.xml 加 `micrometer-tracing-bridge-otel`；`application.yml` 配置 endpoint |
| Logs | Logback 写 JSON 文件，Filebeat tail 文件后转发 ES | `logback.xml` 配 `LogstashEncoder`；`filebeat.yml` 配采集规则 |
| traceId 注入日志 | Micrometer Tracing 自动把 `traceId`/`spanId` 写入 MDC，Logstash Encoder 把 MDC 所有字段写进 JSON | 无需额外代码 |

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
# 仅基础设施（本地开发推荐）
docker compose up -d

# 完整容器化（应用也跑在 Docker 里）
docker compose --profile app up -d
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
