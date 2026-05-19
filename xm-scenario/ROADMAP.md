## 一、已具备

| 能力 | 说明 | 位置 |
|------|------|------|
| **DDD 聚合根 + 限界上下文** | Order 聚合、OrderLine 实体、OrderId 值对象；payment/inventory 防腐层 | order.domain.model, payment.client, inventory.client |
| **状态机 + 守卫** | OrderState/OrderEvent 流转、TransitionGuard（如 PAY 需金额>0） | order.domain.state, OrderStateMachineGuards |
| **乐观锁 CAS** | 聚合 version，submit/markPaid/ship/cancel 用 updateVersion 做 CAS | Order.version, OrderRepository.updateVersion, OrderCommandServiceImpl |
| **领域事件** | DomainEvent、OrderDomainEvent、DomainEventPublisher（内存/可换 Outbox） | shared.event |
| **幂等键** | IdempotencyKeyStore、InMemoryIdempotencyKeyStore、IdempotentOrderCommandService | shared.idempotency |
| **幂等消费** | IdempotentMessageProcessor + Outbox 投递链路 | shared.consumer, OutboxRelayRunner |
| **多粒度锁** | LockStrategy、LockPolicy、CompositeLockPolicy（ORDER→INVENTORY→USER 顺序） | concurrent.lock |
| **分布式锁** | xm-spring 中 RedissonLockStrategy 对接 Redisson | xm-spring RedissonLockStrategy |
| **TCC** | TccCoordinator、SimpleTccCoordinator、MutableTccContext、Order/Payment/Inventory TCC 参与者 | transaction.tcc, transaction.tcc.participant |
| **Saga** | SagaOrchestrator、SimpleSagaOrchestrator、DefaultSagaContext、逆序补偿 | transaction.saga |
| **本地消息表** | LocalMessageTxSupport、InMemory + JdbcLocalMessageTxSupport、outbox_schema.sql | transaction.localmessage, db/outbox_schema.sql |
| **Outbox 闭环** | OutboxRelayRunner 扫表 → 幂等消费 → markSent；`xm.scenario.outbox-relay` | transaction.localmessage, xm-spring OutboxRelayConfig |
| **CQRS 读模型** | OrderView、OrderQueryService、OrderQueryServiceImpl | order.application.query |
| **重试策略** | RetryPolicy、ExponentialBackoffRetry | shared.retry |
| **可观测 TraceId** | TraceIdHolder（ThreadLocal）；HTTP：`TraceIdFilter` + MDC `traceId` | shared.observability, xm-spring TraceIdFilter |
| **可观测 Metrics** | Spring Boot Actuator + Micrometer Prometheus | `/actuator/metrics`, `/actuator/prometheus` |
| **熔断** | Sentinel 装饰 Payment/Inventory，`paymentClient` / `inventoryClient` 资源 | xm-spring SentinelClientConfig |
| **超时自动流转** | OrderTimeoutScheduler + `Order.submittedAtEpochMs` | order.application.scheduler（xm-spring: OrderTimeoutProperties, OrderTimeoutScheduledJob, OrderTimeoutConfig） |
| **契约测试** | `PaymentInventoryClientContractTest` 校验 Stub 行为 | xm-scenario/contract |
| **CI** | GitHub Actions `mvn verify` | `.github/workflows/ci.yml` |
| **Seata TCC** | SeataTccCoordinator、SeataTccOrderAction/PaymentAction/InventoryAction + Impl；SeataOrderSubmitWithPaymentService 用 `@GlobalTransactional` | xm-spring com.xm.transaction.seata.tcc |
| **Seata Saga** | SeataSagaOrchestrator + OrderSubmitSagaAction + JSON 状态机；SagaContextHolder | xm-spring com.xm.transaction.seata.saga |
| **装饰器链** | LockedOrderCommandService（加锁）、LocalMessageOrderCommandService（outbox）、IdempotentOrderCommandService（幂等） | order.application.command |
| **领域服务** | OrderDomainService、OrderSubmitDomainService、OrderPricingDomainService | order.domain.service |
| **TCC/Saga 编排入口** | OrderSubmitWithPaymentTccService、OrderSubmitWithPaymentSagaService | transaction |

---

## 二、后续可继续补齐

| 能力 | 说明 | 建议实现 |
|------|------|----------|
| **RedLock** | 关键路径多 Redis 实例 RedLock | RedissonUtils.executeWithRedLock 或 CriticalLockPolicy |
| **事件溯源** | Order 以事件流持久化 | EventStore + 聚合从事件重建 |
| **多租户** | tenantId 进聚合与锁 key | Order 加 tenantId；LockStrategy key 含 tenantId |
| **分库分表键** | 订单库按 orderId 分区 | 仓储层路由 DataSource/表名 |
| **Pact 契约** | 与真实支付/库存服务联调 | Pact JVM + Feign 实现 |
| **Seata AT** | 无侵入两阶段（TCC/Saga 已在 xm-spring 实现，AT 模式尚未接入） | seata 数据源代理 |
| **OpenTelemetry** | 全链路 Trace 替代仅 TraceId | otel-java + exporter |

---

## 三、与 xm-spring 的装配要点

1. **锁**：`@Bean` 三个 `LockStrategy` 用 `RedissonLockStrategy`，再 `@Bean CompositeLockPolicy`。
2. **本地消息表**：建表 `outbox_schema.sql`，`JdbcLocalMessageTxSupport`；**OutboxRelayRunner** 定时扫表 + 幂等消费。
3. **领域事件**：Outbox 写库后由 relay 模拟下游；真实环境换 MQ 发送。
4. **熔断**：Sentinel 装饰 `PaymentClient`/`InventoryClient`；Dashboard 调规则。
5. **TraceId**：`TraceIdFilter` 入口生成/透传 `X-Trace-Id`，MDC `traceId`。
