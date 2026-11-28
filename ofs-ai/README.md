# ofs-ai：订单智能助手（Spring AI + DeepSeek）

一个独立的 Spring Boot 应用，用 **Spring AI** 把大模型接入订单系统，演示从"聊天机器人"到"生产级 agent"的完整能力栈：
对话 → 工具调用(Function Calling) → RAG 知识问答 → 多轮记忆 → ReAct 多步 agent → 流式输出 → 人工确认(human-in-the-loop)。

> 定位：面向学习/面试展示。核心四件套（Prompt / Tool / RAG / Memory）已具备，知识库检索已作为工具挂进 `/chat`。生产级增强（评估、护栏、持久化确认、可观测）见文末"未做/待补"。

---

## 0. 技术选型与版本

| 项 | 选择 | 说明 |
|---|---|---|
| 框架 | Spring Boot **3.4.5** + Spring AI **1.0.1** | 独立于订单主系统(3.2.4)，版本隔离，互不影响 |
| 对话模型 | **DeepSeek**（`deepseek-chat`） | OpenAI 兼容，用官方 `spring-ai-starter-model-deepseek` |
| Embedding | 本地 ONNX transformers（all-MiniLM-L6-v2） | **DeepSeek 不提供 embedding**，用本地模型转向量，免费离线，首启下载 ~90MB |
| 向量库 | `SimpleVectorStore`（内存） | 学习够用；生产换 pgvector/Redis/Milvus，接口不变 |

> ⚠️ Spring AI **2.0** 需 Spring Boot **4.0**（Jakarta EE 11 + Jackson 3），太新坑多，故学习用稳定的 1.x。

**模块为何独立**：主系统绑着 spring-cloud-alibaba/Sentinel 的固定版本，直接加 Spring AI 会版本打架。`ofs-ai` 用自己的 `<parent>`（boot 3.4.5），只通过根 pom 的 `<modules>` 聚合、不继承根 pom → 既被 IDE/Maven 收纳，又版本隔离。

---

## 1. 运行

**配置 key**（不进 git）：`src/main/resources/application-local.yml` 里填 `spring.ai.deepseek.api-key`。
该文件已被 `.gitignore` 忽略、且在 classpath 上（IDE/命令行/任意工作目录都能读到）。

```bash
# 启动 AI 应用（:8080）。工具调用/agent 相关功能还需订单系统 ofs-app(:8888) 一起跑。
cd ofs-ai && mvn spring-boot:run
```

---

## 2. 接口一览

| 端点 | 能力 | 依赖订单系统? |
|---|---|---|
| `GET /chat?message=&conversationId=` | 对话 + 订单工具 + 知识库检索 + 多轮记忆 + agent 多步 | 订单类请求需要 |
| `GET /chat/stream?message=&conversationId=` | 同上，**流式**输出(SSE 打字机效果) | 同上 |
| `GET /rag?question=` | 手写 RAG（检索→拼提示词→生成），用来对照 agent 自主检索 | 否 |
| `POST /confirm?actionId=` | 旧版支付/发货人工确认（内存） | 是 |
| `POST /agent/tasks`、`GET /agent/tasks/{id}` | 创建/查看持久化售后任务，需要 X-Agent-Key | 是 |
| `POST /agent/tasks/{id}/approve`、`resume`、`withdraw`、`replan` | 人工审批、继续执行、撤回、重新规划，需要 X-Agent-Key | 是 |
| `GET /agent/manual?message=` | 演示框架层拦截工具调用（自写 ReAct 循环） | 是 |

---

## 3. 核心机制笔记（重点，面试常问）

### 3.1 Function Calling：模型如何"自己"调工具
1. Spring AI 把每个 `@Tool` 方法序列化成"函数说明书"：`name` + `@Tool` 的 description + 参数 JSON schema（来自 `@ToolParam` + Java 类型）。
2. 说明书连同问题一起发给 DeepSeek。
3. 模型判断需要工具时，**不输出文字**，而是吐结构化指令 `{"name":"queryOrder","arguments":{...}}`。
4. Spring AI 按 name 找到 Java 方法 → 反序列化参数 → **真正执行**（模型不执行代码，只"点单"）。
5. 返回值序列化成 JSON 回填给模型 → 模型据此再调下一个工具或生成答复。

> **description 决定一切**：写得含糊模型就调错/不调。每个 `@Tool` 都要写清楚。

### 3.2 ReAct 多步：Spring AI 没有 ReActAgent 类
ReAct 循环**内置在工具调用循环里**（在各 ChatModel 实现内部），不是靠解析 "Thought/Action" 文本：

```
messages=[system,user]
loop:
  resp = 模型(messages, tools)
  if 无 toolCalls: return 文本        # 终止条件
  else: 执行工具 → 结果回填 messages → 继续循环
```
- 真实类：`ToolCallback`(包装@Tool) · `ToolCallingManager`(执行) · `ToolCallingChatOptions`(携带工具+开关)。
- 和经典文本 ReAct 的区别：用**结构化 function-calling**而非文本 scratchpad；Thought 被内化、默认不可见（除非用 `deepseek-reasoner`）。
- "agent" vs "聊天机器人"的本质：能**规划→按序多步执行→基于上一步真实结果决策**。靠 agent system prompt 引导。

### 3.3 多轮记忆：模型无状态，记忆是"重发历史"
- `MessageChatMemoryAdvisor` 在请求前后做手脚：**请求前** `get(conversationId)` 取历史拼进 prompt；**响应后** `add(conversationId, ...)` 存回。
- `conversationId` = `Map<String,List<Message>>` 的 key，用来隔离不同会话。
- `MessageWindowChatMemory(maxMessages=20)` = 每会话滑动窗口保留最近 20 条。
- `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))` = 指定这次用哪个 key。
- **本质**：每次把历史重新发一遍，不是模型真记住了。

### 3.4 RAG：手写三步，和 agent 自己决定何时检索
`RagController`（`/rag`）把三步写死，每次提问都检索：
1. **检索**：`vectorStore.similaritySearch(query, topK=3)` —— 问题也向量化，找语义最近的知识。
2. **增强**：把命中知识拼进 prompt（"【已知资料】…只根据资料回答，没有就说不知道"）。
3. **生成**：交给 DeepSeek。
> 模型并不知道有向量库存在，它只看到"被塞了资料的提示词"。

`KnowledgeTools.searchKnowledge` 把同一次检索包成 `@Tool`，挂到 `/chat` 和 `/agent/manual`。模型自己决定要不要查：政策问题先检索，查订单就只调订单工具。返回值是带编号的原文片段，系统提示词要求引用原句。

### 3.5 Human-in-the-loop：两种拦截层次
- **应用层**（`PendingActionStore` + `/confirm`）：敏感工具(支付/发货/取消)不直接执行，只登记动作数据（`action` / `orderId` / `paymentId`，不含闭包）并返回 `actionId`。人调 `/confirm` 时先 `GET /order/{id}` 核对状态（支付要 `SUBMITTED`，发货要 `PAID`，取消要发货前），符合才 POST。同一 `actionId` 只能确认一次，10 分钟过期。查状态失败时保留待确认项，方便重试。安全操作(查/下单/提交)直接执行。
- **框架层**（`/agent/manual`，`internalToolExecutionEnabled(false)`）：关闭自动执行，自写循环，模型每次"想调工具"都交回代码 → 可插入审批/权限/参数改写/拒绝。比应用层更底层通用。

### 3.6 流式输出
`.stream()` 代替 `.call()`，返回 `Flux<String>`，产出 `text/event-stream`(SSE) → 打字机效果。用 `curl -N` 观察逐块到达。

---

## 4. 名词地图（给模型"加 buff"的手段，可叠加）

| 手段 | 作用 | 本项目 |
|---|---|---|
| System Prompt | 设定角色/规则/工作方式 | ✅ agent 提示词 |
| Tool / Function Calling | 调你的代码/API | ✅ OrderTools + KnowledgeTools |
| RAG | 检索知识注入提示词 | ✅ `/rag` 手写；`/chat` 由 agent 调 `searchKnowledge` |
| Memory | 多轮上下文 | ✅ ChatMemory |
| Structured Output | 强制 JSON schema 输出 | ⬜ |
| **MCP** | 工具/数据的**跨进程标准协议**(USB 接口式)：Tools/Resources/Prompts | ⬜ 接现成工具或对外开放能力时才需要 |
| Guardrails/Rules | 输入输出护栏、业务规则引擎(确定性逻辑别丢给模型) | ⬜ 仅 prompt 软约束 |

---

## 5. 踩过的坑

| 现象 | 原因 | 解法 |
|---|---|---|
| 启动报 `DeepSeek API key must be set` | key 文件靠"工作目录"读取，IDE 工作目录是项目根不是 ofs-ai | 把 `application-local.yml` 放 **classpath**(`src/main/resources`) |
| 编译报 `org.springframework.ai.vectorstore 不存在` | 1.0 GA 拆了模块，VectorStore 不在 starter 里 | 加依赖 `spring-ai-vector-store` |
| 编译报 `ToolCallbacks 找不到` | 类在 `org.springframework.ai.support`，非 `...ai.tool` | 改 import 到 `support` 包 |

---

## 6. 未做 / 待补（生产级差距）

- **确认仍在内存**：动作已是可序列化数据，并有 10 分钟 TTL 和执行前状态复检。重启丢失、多副本失效、无鉴权仍在 → 生产换 Redis/DB + 权限校验，接口（`propose` / `peek` / `take`）不用改。
- **RAG 工程化**：内存库 → pgvector/Redis；加分块策略、混合检索(向量+BM25)+rerank、按权限过滤。
- **记忆分层**：短期窗口 + 长期(持久化用户画像) + 超长对话摘要压缩。
- **评估(Eval)**：工具调用正确率/幻觉率，LLM-as-judge，上线前必备。
- **护栏(Guardrails)**：输入输出安全、PII 脱敏、越狱防护。
- **可观测/成本**：追踪每步 prompt/token/延迟/费用(OpenTelemetry GenAI/Langfuse)，语义缓存、模型路由。
- **部署**：打镜像接入 K8s（复用 `deploy/` 那套），key 改用环境变量/Secret 注入、去掉默认 local profile。

## 7. 可恢复售后 Agent：第一条可执行业务链

本版先实现 **DRAFT/SUBMITTED 未支付订单取消**。PAID 转人工退款，SHIPPED 转人工退货：
当前订单系统缺少可查询结果的退款/退货接口，因此任务绝不把取消状态当作退款成功。
也不宣称已释放库存或已发送通知。这是可恢复执行器的第一条业务切片。

模型通过 `proposeAfterSales(orderId)` 提案、`queryAfterSales(taskId)` 查询。
`cancelOrder` 已从模型工具列表移除。审批、恢复执行、撤回都不暴露为模型工具；
模型和知识库文本不能直接赋予这些权限。

任务状态流转：

```text
PLANNING → WAITING_APPROVAL → APPROVED → EXECUTING → COMPLETED
                 ↓               ↓           ↓
             WITHDRAWN      NEEDS_REVIEW   NEEDS_REVIEW
```

- 提案保存订单状态、版本、金额和固定业务规则 unpaid-cancel-v1，10 分钟后审批失效。
- approve 只持久化人工批准；resume 才会执行，二者均需人工持有的 X-Agent-Key。
- 执行前复查归属、版本、状态和金额；订单端再执行状态校验和 CAS 写入，阻止“检查后刚好支付”的竞态。
- 外部写之前先持久化 WRITE_INTENT。超时或进程重启后只读查询结果，不自动重发。
  查到 CANCELLED 才完成；未取消则转人工。完成记录只说明观察到的业务结果，不虚构执行归因。
- 未发出写请求的旧计划可以 replan，但必须重新审批；已经尝试写入的任务不允许重新规划后重复写。
- 每一步的事实和人工批准都保存在 history，和聊天记忆分开。resume 可在重启后凭同一个任务 ID 继续，
  不需要再请求模型重新生成计划。

### 运行和演示

两个应用均设置同一个 `AFTER_SALES_API_KEY`（自行生成，只供操作者和服务使用，勿放入提示词）。
AI 应用另设置 `AFTER_SALES_OPERATOR_USER_ID=user1`，归属来自服务配置而不是模型参数。
这是单运营用户演示；共享密钥并不是完整的多用户身份系统。

1. 启动 ofs-app 和 ofs-ai，先创建一张 user1 的草稿订单。
2. 对 Agent 说“帮我取消订单 ORD-…”，或通过下面的 API 创建任务。
3. 核对返回的 orderId、approvedPlan 和 history，人工审批，随后 resume。

```bash
# 使用你自己设置的 AFTER_SALES_API_KEY；替换订单号
curl -X POST http://localhost:8080/agent/tasks \
  -H "X-Agent-Key: $AFTER_SALES_API_KEY" -H 'Content-Type: application/json' \
  -d '{"orderId":"ORD-替换这里"}'

# 替换任务 ID；version 必须使用返回计划的真实版本
curl -X POST http://localhost:8080/agent/tasks/task-替换这里/approve \
  -H "X-Agent-Key: $AFTER_SALES_API_KEY" -H 'Content-Type: application/json' \
  -d '{"version":0}'

# 可以在 approve 后重启 ofs-ai，再执行下面命令
curl -X POST http://localhost:8080/agent/tasks/task-替换这里/resume \
  -H "X-Agent-Key: $AFTER_SALES_API_KEY"

curl http://localhost:8080/agent/tasks/task-替换这里 \
  -H "X-Agent-Key: $AFTER_SALES_API_KEY"
```

新订单端接口为 `POST /internal/agent/orders/{id}/cancel-unpaid`，校验相同密钥，
请求体为 `{"userId":"user1","expectedVersion":0}`，成功后写取消事件到 Outbox。
未配置密钥时新写接口和任务管理 API 返回 503，错误密钥返回 403。

### 持久化与边界

检查点存放在 `AFTER_SALES_TASK_DIRECTORY`（默认相对启动目录的 `./data/after-sales`）。
使用临时文件写入、force 和原子替换；操作系统文件锁禁止两个 JVM 同时使用同一目录。
重启必须保持同一个绝对路径；容器需挂载持久卷。本版验证进程重启恢复，不提供多副本调度或断电级持久化保证。
resume 是显式操作，没有后台自动扫描任务。人工撤回与执行在同一个进程内串行化。

订单服务默认仍是内存仓储，因此本次重启演示针对 **ofs-ai**；重启 ofs-app 会丢订单。
旧订单 API 和旧版 /confirm 仍是学习接口，并未因此获得完整的生产鉴权。
本版不支持自然语言跨订单搜索、退款、退货、库存释放和通知执行；这些需后续接入真实业务能力。

### 已有故障验证

`mvn test -pl ofs-ai` 不需要模型 API 或 Docker，使用可控订单网关验证：
等待审批/审批后重启、写成功但响应丢失、写成功后崩溃、失败不盲重试、过期审批、
订单变化、归属越权、并发 resume、用户撤回、读取故障，以及模型无审批执行工具。
订单模块的 REST 测试另验证密钥、版本、归属和已支付订单拦截。
这些是确定性执行器的故障测试，不等同于已完成真实模型的幻觉率或提示词注入评估。
