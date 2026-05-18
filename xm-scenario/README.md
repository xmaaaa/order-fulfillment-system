# xm-scenario：复杂场景学习模块

本模块用**订单履约**作为统一业务场景，串联四个复杂点的学习与实现：

1. **DDD 建模**：聚合根、领域边界、限界上下文
2. **复杂状态机**：订单状态流转与事件驱动
3. **高并发控制**：多业务、三方、不同粒度锁
4. **分布式事务**：本地消息表、TCC、Saga

---

## 文档导航

| 文档 | 内容 |
|------|------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 分层架构、包结构、Seata TCC/Saga 用法、锁策略 |
| [ROADMAP.md](ROADMAP.md) | 已具备能力清单、后续待补齐、与 xm-spring 装配要点 |
| [docs/状态机-设计思想与模式.md](docs/状态机-设计思想与模式.md) | 状态机用到的设计思想与模式 |
| [docs/幂等-目的与思路.md](docs/幂等-目的与思路.md) | 幂等的目的与实现思路 |

---

## 快速运行

在 IDE 中运行 `com.xm.scenario.ScenarioDemo#main`，或：

```bash
mvn compile exec:java -pl xm-scenario -Dexec.mainClass="com.xm.scenario.ScenarioDemo"
```

---

## 建议学习顺序

1. **DDD 边界与聚合根** — 先定边界再写代码：画上下文图，建 Order 聚合根、OrderLine 实体、状态值对象
2. **状态机** — 在聚合内表达状态：`OrderState`/`OrderEvent` 流转，`TransitionGuard` 守卫
3. **多粒度锁** — 按维度（订单/库存/用户）抽象锁策略，`CompositeLockPolicy` 按固定顺序组合
4. **分布式事务** — 本地消息表、TCC、Saga 各一个最小实现，便于对比

---

## 领域模型边界

- **订单聚合根 (Order)**：订单头 + 订单行在同一聚合内，保证一致性；不把库存、支付放进聚合，只通过 ID/值对象引用。
- **支付、库存**：以防腐层形式存在（`PaymentClient`/`InventoryClient` 接口），由 xm-spring 提供实现。
- **状态机归属**：状态、事件、流转规则属于订单聚合的一部分，在 `order.domain.state` 中维护。
