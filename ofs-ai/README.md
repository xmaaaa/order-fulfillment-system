# ofs-ai：订单智能助手（Spring AI + DeepSeek）

一个独立的 Spring Boot 应用，用 **Spring AI** 把大模型接入订单系统，演示从"聊天机器人"到"生产级 agent"的完整能力栈：
对话 → 工具调用(Function Calling) → RAG 知识问答 → 多轮记忆 → ReAct 多步 agent → 流式输出 → 人工确认(human-in-the-loop)。

> 定位：面向学习/面试展示。核心四件套（Prompt / Tool / RAG / Memory）已具备；生产级增强（评估、护栏、持久化确认、可观测）见文末"未做/待补"。

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
| `GET /chat?message=&conversationId=` | 对话 + 工具调用 + 多轮记忆 + agent 多步 | 工具类请求需要 |
| `GET /chat/stream?message=&conversationId=` | 同上，**流式**输出(SSE 打字机效果) | 同上 |
| `GET /rag?question=` | RAG 知识问答（独立，未接入 chat，见"待补"） | 否 |
| `POST /confirm?actionId=` | 人工确认敏感操作 | 是 |
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

### 3.4 RAG：本质是"往提示词注入检索到的上下文"
`RagController` 手写三步，看得见全过程：
1. **检索**：`vectorStore.similaritySearch(query, topK=3)` —— 问题也向量化，找语义最近的知识。
2. **增强**：把命中知识拼进 prompt（"【已知资料】…只根据资料回答，没有就说不知道"）。
3. **生成**：交给 DeepSeek。
> 模型并不知道有向量库存在，它只看到"被塞了资料的提示词"。

### 3.5 Human-in-the-loop：两种拦截层次
- **应用层**（`PendingActionStore` + `/confirm`）：敏感工具(支付/发货/取消)不直接执行，登记待确认项返回 `actionId`，人调 `/confirm` 才执行。安全操作(查/下单/提交)直接执行。
- **框架层**（`/agent/manual`，`internalToolExecutionEnabled(false)`）：关闭自动执行，自写循环，模型每次"想调工具"都交回代码 → 可插入审批/权限/参数改写/拒绝。比应用层更底层通用。

### 3.6 流式输出
`.stream()` 代替 `.call()`，返回 `Flux<String>`，产出 `text/event-stream`(SSE) → 打字机效果。用 `curl -N` 观察逐块到达。

---

## 4. 名词地图（给模型"加 buff"的手段，可叠加）

| 手段 | 作用 | 本项目 |
|---|---|---|
| System Prompt | 设定角色/规则/工作方式 | ✅ agent 提示词 |
| Tool / Function Calling | 调你的代码/API | ✅ OrderTools |
| RAG | 检索知识注入提示词 | ✅ /rag |
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

- **RAG 未接入 agent**：`/rag` 和 `/chat` 割裂。应做 **agentic RAG**——把检索包成一个 `@Tool searchKnowledge(query)`，让 agent 自主决定何时查知识库，统一到 `/chat` 入口。
- **确认存内存**：重启丢失、多副本失效、无过期、无鉴权、confirm 时不复检业务状态 → 生产应存 Redis/DB + TTL + 权限 + 执行前复检（可复用主系统 TCC/状态机）。
- **RAG 工程化**：内存库 → pgvector/Redis；加分块策略、混合检索(向量+BM25)+rerank、按权限过滤。
- **记忆分层**：短期窗口 + 长期(持久化用户画像) + 超长对话摘要压缩。
- **评估(Eval)**：工具调用正确率/幻觉率，LLM-as-judge，上线前必备。
- **护栏(Guardrails)**：输入输出安全、PII 脱敏、越狱防护。
- **可观测/成本**：追踪每步 prompt/token/延迟/费用(OpenTelemetry GenAI/Langfuse)，语义缓存、模型路由。
- **部署**：打镜像接入 K8s（复用 `deploy/` 那套），key 改用环境变量/Secret 注入、去掉默认 local profile。
