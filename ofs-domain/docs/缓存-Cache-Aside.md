# 订单查询缓存：Cache-Aside 的设计与取舍

## 一、目的

**一句话**：把热点订单的读请求挡在数据库前面，同时让「缓存和库不一致」的窗口小到可接受。

缓存不是「加个 Redis 就完事」。真正要回答的是四个问题：

| 问题 | 现象 | 本项目的答案 |
|------|------|--------------|
| 怎么读、怎么写 | —— | Cache-Aside：读时回填，写时**删**缓存 |
| 缓存**穿透** | 查一堆不存在的 ID，缓存永不命中，请求全落库 | 布隆过滤器短路 + 空值缓存 |
| 缓存**击穿** | 单个热点 key 过期瞬间，大量并发一起打库 | 分布式锁只放一个重建者 + 双重检查 |
| 缓存**雪崩** | 大批 key 同时过期，库被打穿 | TTL 加随机抖动 |

三个词容易混：**穿透**是查不存在的数据（缓存里永远没有）；**击穿**是热点 key 刚好过期（缓存里本该有）；**雪崩**是大量 key 同时失效。

---

## 二、代码结构

读写两侧都是装饰器，和项目里锁、幂等、本地消息表那几层是同一个套路——包一层，不改被包者。

```
读路径（CQRS 读侧）
  OrderController
    └─ CachedOrderQueryService        ← 缓存装饰器：查缓存/防穿透/防击穿/回填
         └─ OrderQueryServiceImpl     ← 真正查仓储并投影成 OrderView

写路径（失效只有一处，挂在所有写路径的收口点）
  IdempotentOrderCommandService
    └─ LockedOrderCommandService                   ← 订单锁
         └─ LocalMessageOrderCommandService        ← 事务边界（目前只包 createDraft）
              └─ OrderCommandServiceImpl
                   └─ OrderDomainService
                        └─ CacheEvictingOrderRepository  ← 失效：注册 after-commit 回调
                             └─ InMemoryOrderRepository

  TCC / Saga / OrderTimeoutScheduler ──┘ 直接调 OrderDomainService，同样收口在仓储
```

端口与实现分离，沿用项目既有约定（domain 定接口、app 提供 Redis 实现）：

| 端口（ofs-domain） | 实现（ofs-app） | 作用 |
|---|---|---|
| `OrderViewCache` | `RedissonOrderViewCache` / `InMemoryOrderViewCache` | 存读模型 |
| `OrderIdFilter` | `RedissonOrderIdFilter`（布隆） | 防穿透 |
| `LockStrategy`（已有） | `RedissonLockStrategy` / `InMemoryLockStrategy` | 防击穿的重建锁 |
| `AfterCommitExecutor` | `SpringAfterCommitExecutor` / `ImmediateAfterCommitExecutor` | 把失效推迟到事务提交后 |

---

## 三、为什么是「删缓存」而不是「更新缓存」

两个理由：

1. **删比更新省事且不易错。** 更新要在写侧重新拼一份读模型，于是投影逻辑读写两处各有一份，迟早漂移。删掉让下一次读自己去投影，只有一份真相。
2. **并发写下「更新缓存」会留脏值。** W1、W2 各自「写库 + 写缓存」，两组操作的相对顺序不保证一致，完全可能「库里是 W2 的值、缓存里是 W1 的值」，而且除了 TTL 没有自愈机制。删缓存不会：谁删都是删掉，下次读一定拿到库里的最新值。

## 四、为什么顺序是「先改库、后删缓存」

反过来（先删缓存再改库）会留下一个必然发生的窗口：删完到改完之间进来的读，查不到缓存 → 回源查到**旧值** → 回填。之后库改成了新值，缓存里却是旧值，一直脏到 TTL 到期。

所以规矩是：**库先落定，再删缓存。**

---

## 五、失效挂在哪一层：两个约束同时成立才行

这是本项目最值得记的一段推理，因为第一版做错了。

### 约束一：覆盖完整 → 所以挂在仓储层

**写入口不止一个。** 除了 `OrderCommandService`，还有 `OrderSubmitWithPaymentTccService`、`OrderSubmitWithPaymentSagaService`、`OrderTimeoutScheduler` 以及 Seata 的 TCC/Saga Action——它们都直接调 `OrderDomainService`。只在应用服务层挂失效装饰器的话，**TCC 支付完再查订单会读到旧状态**。

而所有写路径最终都收敛到 `OrderRepository.save()` / `updateVersion()`。挂在仓储层才是「一个都不漏」。

### 约束二：不早于提交 → 所以走 `AfterCommitExecutor`

但仓储的 save/updateVersion 是在**事务内**被调用的。在那里当场删缓存，等于**基于一个还没提交的事实发布副作用**：

- 删完到提交之间进来的读会查到**旧值**（事务还没提交）并回填，之后一直脏到 TTL；
- 事务万一回滚，你已经白删了——库根本没变，缓存里的值本来是对的。

所以 `CacheEvictingOrderRepository` **不直接删**，而是把删的动作交给 `AfterCommitExecutor`：有活跃事务就挂到提交后（`TransactionSynchronization.afterCommit()`），没有事务就立即执行（数据本来就已可见）。

### 走过的弯路

第一版是「仓储层当场删 + 应用服务层再删一次」——用两个各有残缺的层互相打补丁：仓储层管覆盖面但时序错，应用服务层管时序但覆盖不全。换成 after-commit 之后，一处就同时满足两个约束，应用服务层那一层随即删除。

**这也是生产系统的通行做法**，不是本项目的独创：Spring 生态用 `@TransactionalEventListener(AFTER_COMMIT)` 或事务同步回调；再彻底一点就订阅 binlog（见第六节）。共同点都是——**失效发生在提交之后，且由一个所有写都必经的点触发**。

### 两个细节

- `updateVersion` 只在 CAS **真的成功**时才失效。乐观锁冲突意味着库里没变，缓存里的值仍然是对的。
- 布隆过滤器的登记也挂在提交后。事务回滚的话订单压根不存在，不该登记进去——布隆过滤器删不掉元素，错误登记只能靠整体重建来清。

### 已知边界

事务回滚时不失效。唯一的例外是：如果某个写用例在**同一个事务内**又通过带缓存的读路径查了这笔订单，它会看到自己未提交的修改并回填进缓存，回滚后就留下一个「从未存在过」的值。本项目不存在这种用例——写路径读订单走的是 `OrderDomainService`/仓储，不经过 `CachedOrderQueryService`。若将来出现，把实现换成 `afterCompletion(status)` 并在回滚时也失效即可。

---

## 六、残留的不一致窗口（诚实说明）

即使失效的位置和时机都对了，Cache-Aside 仍然挡不住这一种交错：

```
读 A：查缓存 miss ──→ 查库拿到旧值 ───────────────→ 回填旧值 ✗
写 B：            改库(新值) ──→ 提交 ──→ 删缓存 ──┘
```

要求 A 的**回填**晚于 B 的**删除**。窗口很窄（A 已经查完库、只差最后一步写缓存），本项目靠 TTL 兜底。这是 Cache-Aside 的固有缺陷，不是实现问题。真要收敛，两条路：

- **延迟双删**：写操作删完缓存后，延迟 N 毫秒（大于「一次读的查库+回填」耗时）再删一次。实现简单，但 N 靠猜，且多一次延迟任务。
- **订阅 binlog**（Canal / Debezium）：以数据库变更日志为唯一触发源去失效缓存。彻底解决顺序问题，而且是唯一能覆盖「绕过应用的写」（DBA 脚本、其他服务直连库）的方案——被问「你怎么保证没有写路径漏掉」时，这才是终极答案。代价是多一套组件和运维成本。

本项目没做这两个——订单读模型不是强一致场景，TTL 5 分钟可接受。这是**明确的取舍，不是遗漏**。

### 演进路线（按代价排序）

| 方案 | 覆盖面 | 时序 | 代价 | 本项目 |
|---|---|---|---|---|
| 应用服务层失效 | ❌ 漏 TCC/Saga/调度器 | ✅ | 无 | 试过，已放弃 |
| 仓储层当场失效 | ✅ | ❌ 在提交前 | 无 | 试过，已放弃 |
| **仓储层 + after-commit** | ✅ | ✅ | 很小 | **当前实现** |
| 领域事件（聚合记录 + 提交时派发） | ✅ | ✅ | 中（要动聚合） | 待做，和「outbox 铺全部流转」一起 |
| 订阅 binlog | ✅✅ 连绕过应用的写都覆盖 | ✅ | 大（多套组件） | 演进方向 |

领域事件那一档是 DDD 正统，还能把项目里**至今没装配的 `DomainEventPublisher`** 用起来。但要注意：事件的产生点必须在**领域层**（聚合或 `OrderDomainService`），若放在应用服务层，TCC/Saga 在事件层面会原样重现覆盖不全的问题。

---

## 七、防穿透的两道防线

### 第一道：布隆过滤器（默认关闭）

`mightExist()` 返回 false 就直接判定不存在、**连库都不查**。所以它的语义必须是「宁可放过，不可错杀」：

- 允许**假阳性**（不存在的说成可能存在）→ 代价只是多查一次库。
- **绝不允许假阴性**（存在的说成不存在）→ 真实订单被当成 404。

由此推出一条运维前提：**所有已存在的订单 ID 都必须先进过滤器。** 新单靠 `CacheEvictingOrderRepository.save()` 实时登记；**历史单必须在启用前全量预热**。这就是 `ofs.scenario.order-cache.bloom-filter.enabled` 默认 `false` 的原因——没预热就开，等于给老订单判死刑。

两个已知取舍：

- **不支持删除元素。** 订单取消后 ID 仍留在过滤器里，只产生假阳性，不影响正确性；要收缩规模只能定期整体重建。
- **`tryInit` 只在 key 不存在时生效。** 改了 `expected-insertions` / `false-probability` 后必须先删掉旧 key 才会按新参数重建，否则一直沿用旧位图规格。

### 第二道：空值缓存

查不到的 ID 也在缓存里占个短命位置（`absent-ttl`，默认 30s，实现上是一个 `__ABSENT__` 占位符）。TTL 必须**远短于**正常值，否则「刚建的单查不到」。

两道是互补的：布隆过滤器挡住海量随机 ID（连缓存都不用写），空值缓存挡住反复查同一个不存在的 ID。

---

## 八、防击穿：锁 + 双重检查 + 降级

热点 key 过期瞬间会有一大波请求同时 miss。流程：

1. 抢重建锁（key 前缀 `cache:order:`）。
2. 抢到 → **双重检查**缓存（等锁期间别人可能已回填）→ 仍没有才查库、回填。
3. 抢不到 → 重读一次缓存；还没有就**直接查库**，不排队等锁。

两个设计点：

- **锁 key 必须和业务订单锁隔离。** 复用业务锁的话，一个查询在重建缓存时会把同订单的写操作挡住，读拖累写。
- **可用性优先于命中率。** `lock-wait` 默认只等 200ms，等不到就降级直查库。宁可多打一次库，也不让请求堆在锁上。同理，缓存或锁组件抛异常一律当「未命中」降级读库——**Redis 挂了要能继续卖货，不能连查询一起挂。**

实测（20 并发打同一冷 key）：`loads` 只 +1，其余 19 个走双重检查或直接命中，没有一个降级。

---

## 九、防雪崩：TTL 抖动

批量回填的 key（冷启动预热、大促前刷缓存）如果 TTL 完全相同，会在同一秒集体过期把库打穿。实际 TTL = `ttl + random[0, jitter)`，默认 `5m + [0,30s)`。

Redis 侧也配了 `--maxmemory 256mb --maxmemory-policy allkeys-lru`：内存打满时按 LRU 淘汰而不是报 OOM 拒写。这是把 Redis 当**缓存**用的正确姿势；当**存储**用才该配 `noeviction`。

---

## 十、配置

```yaml
ofs:
  scenario:
    cache: none          # none=不缓存 | memory=JVM 内(单机/单测) | redis=Redisson(多机)
    order-cache:
      ttl: 5m
      absent-ttl: 30s    # 空值占位；0=关闭
      jitter: 30s        # TTL 抖动上界；0=关闭
      rebuild-lock: true # 防击穿
      lock-wait: 200ms
      lock-lease: 3s     # 须大于「查库+回填」最坏耗时，否则锁提前释放、防击穿失效
      bloom-filter:
        enabled: false   # 开之前必须先全量预热历史订单 ID
```

`cache=redis` 还需要 `redis.enabled=true`。配了 `redis` 却忘开 `redis.enabled` 是个静默陷阱（没有 `RedissonClient` → 没有 `OrderViewCache` → 装饰器不生效 → 缓存完全没开），所以 `OrderCacheConfig` 有个启动自检会打 WARN。

---

## 十一、可观测

`OrderCacheStats` 的计数器通过 `MeterBinder` 暴露成 `ofs_order_cache_*` 指标。看板上真正有用的是三个：

- `hit_rate` — 够不够本。`hits`/`absent_hits`/`misses` 每个请求只记一次，所以这个比值可以直接当命中率读。
- `degraded_loads` — 缓存是不是在裸奔（抢不到锁又没回填）。
- `errors` — Redis 有没有在抖。

`rebuild_skipped` 是防击穿真正省下的查库次数：热点 key 上并发越高，它越接近「并发数 - 1」。

---

## 十二、本地验证

```bash
docker compose up -d redis

REDIS_ENABLED=true OFS_SCENARIO_CACHE=redis \
OFS_SCENARIO_ORDER_CACHE_BLOOM_FILTER_ENABLED=true \
java -jar ofs-app/target/ofs-app-1.0-SNAPSHOT.jar

# 建单（此时缓存为空——Cache-Aside 不做写时预热）
OID=$(curl -s -X POST localhost:8888/order/draft -H 'Content-Type: application/json' \
  -d '{"userId":"user1","lines":[{"skuId":"SKU-001","quantity":2,"price":99.00}]}' \
  | sed -E 's/.*"orderId":"([^"]+)".*/\1/')

curl -s localhost:8888/order/$OID                      # 首读 miss → 回填
docker exec redis redis-cli GET  "ofs:order:view:$OID" # 值存成可读 JSON，方便观察
docker exec redis redis-cli PTTL "ofs:order:view:$OID" # 5m + 抖动
curl -s -X POST localhost:8888/order/$OID/submit       # 写 → 删缓存
docker exec redis redis-cli EXISTS "ofs:order:view:$OID"  # → 0

curl -s localhost:8888/actuator/prometheus | grep ofs_order_cache_
```

值故意存成 JSON 字符串（`StringCodec` + Jackson）而不是 Redisson 默认的二进制编码，就是为了能直接 `redis-cli GET` 看懂，方便观察「冷启动全 miss → 回填 → 命中」。代价是比二进制略大，订单读模型这个体量无所谓。

---

## 十三、还没做的

- **列表查询不缓存**（`listByUserId` 直接透传）。列表缓存失效面太大（任一订单变更都要失效所有含它的列表页），收益却低（带分页/筛选，命中率差）。生产要做该走读库或 ES 投影，别硬塞 Redis。
- **历史订单 ID 预热布隆过滤器**的脚本/启动任务。没有它，过滤器就只能保持关闭。
- **延迟双删 / binlog 订阅**（见第六节）。
- **多级缓存**（本地 Caffeine + Redis 两级）。能再挡一层 Redis 网络开销，但要处理本地缓存的跨节点失效（一般靠 Redis pub/sub 广播）。
