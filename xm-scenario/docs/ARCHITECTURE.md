# xm-scenario 架构说明

## 1. 分层：App -> Domain -> Repository

```
┌─────────────────────────────────────────────────────────────────┐
│  App（应用层）                                                    │
│  OrderCommandServiceImpl：用例入口、入参转换、事件发布、锁编排      │
└───────────────────────────┬─────────────────────────────────────┘
                            │ 委托
┌───────────────────────────▼─────────────────────────────────────┐
│  Domain（领域层）                                                 │
│  OrderDomainService：状态机、守卫、领域规则、持久化编排             │
│  Order、OrderSubmitDomainService                                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │ 使用
┌───────────────────────────▼─────────────────────────────────────┐
│  Repository（基础设施层）                                          │
│  OrderRepository 接口在 domain，实现在 infrastructure              │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 分布式事务

| 模式 | 学习实现 | 框架实现 |
|------|----------|----------|
| TCC | SimpleTccCoordinator + TccParticipant | Seata @TwoPhaseBusinessAction（xm-spring/com.xm.transaction.seata） |
| Saga | SimpleSagaOrchestrator + SagaStep（学习） | SeataSagaOrchestrator + JSON 状态机（saga=seata） |
| 本地消息表 | InMemoryLocalMessageTxSupport（学习） / JdbcLocalMessageTxSupport（生产） |

### Seata TCC 两种用法

- **学习版**（xm.scenario.tcc=learning）：SimpleTccCoordinator 手写 Try/Confirm/Cancel 编排，Seata 仅包一层 begin/commit/rollback
- **框架版**（xm.scenario.tcc=seata）：SeataOrderSubmitWithPaymentService 使用 @GlobalTransactional，各参与者用 @TwoPhaseBusinessAction 声明 prepare/commit/rollback，Seata 自动管理 2PC

### Seata Saga 两种用法

- **学习版**（xm.scenario.saga=learning）：SimpleSagaOrchestrator 手写 SagaStep 编排
- **框架版**（xm.scenario.saga=seata）：SeataSagaOrchestrator + JSON 状态机（statelang/order_submit_with_payment.json），需 seata-saga-spring、saga 状态表

## 3. 锁策略

- OrderLock：订单维度，lease 30s
- InventoryLock：库存维度，lease 10s
- UserLock：用户维度，lease 5s

实现：memory（InMemoryLockStrategy）、redisson（RedissonLockStrategy）

## 4. 包结构

```
com.xm.scenario
├── order
│   ├── domain.model          # Order、OrderId、OrderRepository
│   ├── domain.service        # OrderDomainService、OrderSubmitDomainService
│   ├── domain.state          # 状态机
│   ├── application.command   # 写用例
│   ├── application.query     # 读模型（CQRS）
│   └── infrastructure        # 仓储实现
├── concurrent.lock           # 锁策略
├── transaction
│   ├── tcc                   # TCC 协调器与参与者
│   ├── saga                  # Saga 编排器
│   └── localmessage          # 本地消息表
├── shared                    # event、idempotency、retry、observability
├── payment.client            # 防腐层
└── inventory.client          # 防腐层
```
