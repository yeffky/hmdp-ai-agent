# O2O 生活服务 AI Agent 项目面试题档案

> 分析对象：`D:\project\hmdp-ai-agent`
>
> 分析基线：2026-08-19，按当前工作区代码、README、测试目录和 `.claude/progress/PROJECT_PROGRESS.md` 综合整理。
>
> 使用方式：先背“项目概述”和“核心链路”，再按面试岗位选择专题深入。文中“已实现”只表示代码中存在相应实现；涉及“我负责/我设计”的表述，请替换成你的真实分工。

## 0. 先记住项目的一句话定位

这是一个基于 Spring Boot 的 O2O 生活服务平台，除了店铺、团购、评论、排队和秒杀等基础业务，还增加了一个面向生活服务场景的 ReAct AI Agent。Agent 使用 LangGraph4j 编排状态图，结合工具调用、RAG 混合检索、Text2SQL、上下文记忆、人工确认、SSE 流式输出和可观测 trace，把自然语言请求转换成可执行的查询或业务操作。

最适合面试的主线不是“用了很多中间件”，而是：

> 如何把一个不确定的 LLM 决策过程，约束成可恢复、可观测、可确认、可落库、可测试的业务系统。

## 1. 项目事实卡片

| 维度 | 当前项目事实 |
|---|---|
| 后端 | Spring Boot 2.7.18、Java 17、MyBatis-Plus 3.4.3 |
| 主业务库 | MySQL 8，店铺、用户、团购、订单、评论、排队等 |
| Agent 状态/历史 | PostgreSQL，checkpoint、聊天历史、用户画像、trace |
| 缓存/分布式能力 | Redis、Spring Data Redis、Redisson |
| 消息队列 | RabbitMQ，发布确认、手动 ACK、有限重试、DLQ |
| Agent 框架 | LangGraph4j 1.8.19、LangChain4j 1.16.2 |
| 大模型 | DeepSeek OpenAI-compatible API |
| RAG | 自适应切片、Embedding、Qdrant、BM25、RRF、LLM Rerank |
| 前端 | Vue 3、Vite、Pinia、Vue Router、Element Plus、Vitest |
| 流式协议 | Spring `SseEmitter` + 自定义 SSE JSON 事件 |
| 部署/访问 | Nginx 静态托管和反向代理，Docker Compose 管理部分依赖 |
| 定时任务 | 本地 `@Scheduled` 兜底，服务器可切换 xxl-job |
| 代码规模 | 后端主源码约 214 个 Java 文件；后端测试文件 48 个；前端测试文件 11 个 |

## 2. 面试前必须校准的代码事实

### 2.1 当前图结构（五个 StateGraph 节点）

README 已同步为当前五节点描述。`GraphConfig` 实际编译的节点是：

```text
START
  ↓
Context → Planner ──简单问题/无法满足──→ Answer → END
              ↓
            Agent ──普通工具──→ Tools ──→ Agent
              ↑                    │
              └──重规划/恢复────────┘
```

当前实际节点为 5 个：`Context`、`Planner`、`Agent`、`Tools`、`Answer`。

- 观察逻辑主要在 `ToolNode` 中完成：判断空结果、注入“换一种思路”的提示、把工具结果追加到消息通道。
- 充分性和继续执行逻辑主要在 `AgentNode` 及 skill SOP 中完成。
- 早期拆分的执行、观察和信息充分性判断职责已经收敛，不要面试时说当前代码仍然存在这些独立节点。

### 2.2 卡片协议的主路径已经从文本标记切到 `showCards`

当前主路径是：Agent 决策阶段调用 `showCards(shopIds)` 声明要展示的店铺，后端根据声明的店铺和回答文本中的店名发送独立 `cards` SSE 事件，前端按 anchor 把卡片插入文本附近。

旧的 `[[id]]` 文本占位符逻辑仍保留兼容代码，所以代码里还能看到占位符剥离和卡片兜底逻辑。面试时应表述为：

> 主协议使用结构化工具调用驱动 UI，旧标记协议作为兼容和兜底，避免让 LLM 在自由文本中稳定地产生 UI 指令。

### 2.3 当前工作区不是干净提交状态

分析时仓库存在未提交修改和未跟踪文件。因此这份档案描述的是“当前工作区”，不等同于最近一个 Git commit 的精确快照。面试前建议：

- 只展示你确认已经提交、可以运行的功能。
- 把本地实验文件、爬虫产物、临时配置排除出展示范围。
- 不要把进度文件里的历史实验结果直接说成当前线上指标。

### 2.4 当前代码审阅出的一个高风险点：Text2SQL 行级过滤顺序

当前 `Text2SqlTool.query` 的代码顺序需要重点复核：先调用 `jdbcTemplate.queryForList(currentSql)`，之后才调用 `enforceRowLevel(currentSql, userId)`。如果这就是准备部署的版本，那么 `tb_voucher_order` 的用户隔离是在 SQL 执行之后才追加，不能真正保护本次查询。

面试中不要把它说成“已经彻底解决”。正确的工程回答是：

> 行级约束必须在第一次执行前注入，并且注入后再次做 AST/白名单校验；同时使用只读数据库账号、SQL AST 解析和专门的订单查询工具作为纵深防御。当前实现需要把 `enforceRowLevel` 前移到 `queryForList` 之前，并补充跨用户回归测试。

## 3. 30 秒项目介绍

可以这样回答：

> 我做的是一个 O2O 生活服务平台，用户可以按地区找店、看评论、查团购、排队取号和参与秒杀。我在传统 Spring Boot 业务上增加了一个生活服务 AI Agent：用户用自然语言提问后，系统通过 LangGraph4j 状态图完成上下文组装、意图规划、工具调用和最终回答；需要知识问答时走 RAG，需要复杂数据查询时走安全的 Text2SQL，需要取号或取消排队时会进入人工确认。秒杀链路使用 Redis Lua 做原子预扣，RabbitMQ 异步落单，并配合发布确认、重试、死信和数据库唯一索引保证最终一致性。前端通过 SSE 接收思考过程、工具过程、卡片和回答流。

## 4. 两分钟项目介绍

> 项目分成传统 O2O 业务和 AI Agent 两部分。传统业务包括城市/地区、店铺列表、地图、团购、订单、评论、关注、排队和秒杀。后端基于 Spring Boot，MySQL 保存业务数据，Redis 负责缓存、Geo 查询、Token 和秒杀预扣，RabbitMQ 负责秒杀订单异步落库，PostgreSQL 保存 Agent checkpoint、聊天历史和可观测数据。
>
> AI 请求从 `/chat/react/stream` 进入，先做输入清洗和用户解析，然后通过 LangGraph4j 运行状态图。Context 负责上下文和记忆，Planner 用结构化 JSON 做意图和技能规划，Agent 只暴露当前技能对应的工具，Tools 执行工具并把结果写回标准消息通道，最后 Answer 组装回答规则，由 Controller 通过 DeepSeek 流式模型输出 SSE。工具结果为空时会提示 Agent 换查询策略，写操作如取号、取消排队由代码级 WriteGuard 强制人工确认。
>
> RAG 侧是文档切片、Embedding、Qdrant 向量检索和内存 BM25 的混合检索，使用 RRF 融合后再进行 LLM 重排。Text2SQL 侧先做表域白名单和 schema 渐进式披露，再生成 SELECT，经过危险关键字、表名、敏感字段和 LIMIT 等校验。项目中比较有挑战的部分是处理 LLM 的不确定性：通过状态 checkpoint、错误分类、重试上限、人工确认、流式断点续传和 trace 把它约束在可恢复的业务流程内。

## 5. 系统架构总图

```text
Vue 3 + Pinia + chatMachine
        │  REST / SSE
        ▼
Nginx：静态资源、/api 代理、SSE 关闭 buffering
        │
        ▼
Spring Boot
  ├─ 传统 Controller / Service / Mapper
  │    ├─ MySQL：店铺、团购、订单、评论、排队
  │    ├─ Redis：Cache-Aside、Geo、Token、秒杀预扣
  │    └─ RabbitMQ：秒杀异步订单、重试、DLQ
  │
  ├─ ReactStreamController
  │    └─ LangGraph4j StateGraph
  │         Context → Planner → Agent → Tools → Agent → Answer
  │                         │              │
  │                         │              ├─ 店铺/团购/评价/排队工具
  │                         │              ├─ RAG 工具
  │                         │              ├─ Text2SQL 工具
  │                         │              └─ showCards / askUserToChoose
  │                         │
  │                         └─ PostgreSQL Delta Checkpoint
  │
  ├─ RAG
  │    ├─ 文档监控/转换/去重/切片
  │    ├─ Embedding → Qdrant
  │    └─ BM25 + 向量 → RRF → LLM Rerank
  │
  └─ AgentTrace / LlmTraceListener → MySQL trace 表 + 管理端回放

DeepSeek：规划/工具决策/查询改写/重排/最终回答
```

## 6. 核心业务链路，必须能手画出来

### 6.1 普通 AI 查询链路

```text
POST /chat/react/stream
  → PromptSanitizer 清洗输入
  → UserResolver 解析用户
  → stateOf(threadId) 检查是否有待确认 checkpoint
  → GraphInputFactory 统一初始化状态
  → Context：窗口、摘要、画像、历史、skill 规则
  → Planner：意图 + skills + plan，JSON 输出
  → Agent：按 skill 动态暴露工具
  → Tools：执行工具，消息/错误/空结果写回 state
  → Agent：继续决策，可能换工具、扩大条件或结束
  → Answer：生成规则 prompt
  → AnswerInjection：折叠工具消息并注入上下文
  → DeepSeek Streaming API
  → SSE：answer_chunk / cards / actions / done
  → 前端 chatMachine 归约并渲染
```

### 6.2 排队取号链路

```text
用户：帮我在某店取号
  → Planner 选择 shop + queue skill
  → 先搜索店铺得到 shopId
  → Agent 决定调用 takeQueueNumber
  → WriteGuard 识别为写操作
  → pendingWrite 写入 checkpoint，原地暂停
  → SSE 返回确认卡片和“确认/取消”按钮
  → 用户再次发送确认
  → Controller updateState 注入 userChoice，恢复到 Agent
  → AgentNode 用 LLM/规则解析确认、取消或修改参数
  → 确认后才真正调用 QueueTicketTool
  → 写入排队记录并返回结果
```

### 6.3 秒杀链路

```text
POST /voucher-order/seckill/{id}
  → 生成雪花 orderId
  → Redis Lua 原子检查时间窗口、库存、一人一单
  → Redis 预扣库存 + SADD 用户集合
  → RabbitMQ 发布消息 + CorrelationData
  → ConfirmCallback / ReturnsCallback 失败时 Lua 回补预留
  → Consumer 手动消费
  → userId 维度 Redisson 分布式锁
  → @Transactional createVoucherOrder
       ├─ 订单已存在则幂等返回
       ├─ 校验券状态和时间
       ├─ MySQL 条件扣库存 stock > 0
       ├─ 写订单
       └─ 清理团购列表缓存
  → 成功 ACK
  → 失败按 2s/5s/10s 重试
  → 超过次数 basicNack(requeue=false) 进入 DLQ
  → DLQ 落 tb_dead_order
  → 管理员选择重放或丢弃
```

## 7. 高频面试题：项目总览与架构

### Q1：为什么选择这个项目作为面试项目？

答题要点：

- 业务面完整：查询、推荐、写操作、异步订单、支付/取消、排队。
- 技术面有深度：Redis、RabbitMQ、MySQL、PostgreSQL、Qdrant、SSE。
- AI 不是简单聊天，而是有状态图、工具执行、RAG、Text2SQL、HITL、可观测性和恢复机制。
- 能够讲清楚失败路径和一致性，而不是只讲 happy path。

### Q2：为什么没有直接用一个大 Prompt，而是拆成 Planner、Agent、Tools、Answer？

答：职责隔离和风险控制。Planner 只负责意图和计划，Agent 负责每一步工具决策，Tools 负责确定性执行，Answer 负责把事实组织成用户可读文本。这样可以：

- 限制每个阶段的上下文和工具集合，降低 token 和误调用。
- 让写操作确认、错误分类、重试上限进入程序控制。
- 工具结果可以落入标准消息和 checkpoint，支持恢复和回放。
- 每一层可以单独测试，定位问题时知道是规划、工具还是回答阶段出错。

### Q3：当前系统是单体还是微服务？为什么？

答：当前是模块化单体。业务域仍在一个 Spring Boot 进程中，但按 Controller、Service、Agent、RAG、Task 等边界组织。对于当前项目规模，单体能降低部署和分布式事务成本；Redis、RabbitMQ、PostgreSQL、Qdrant 已经承担了异步、状态和数据隔离。未来如果 Agent 推理耗时明显影响主业务，可以先把 Agent 推理服务独立出来，但要先定义 checkpoint、工具调用和用户身份的服务契约。

### Q4：MySQL 和 PostgreSQL 为什么同时存在？

答：职责不同。MySQL 是成熟的 O2O 业务库，适合事务、订单和关系查询；PostgreSQL 在本项目中主要保存 Agent checkpoint、聊天历史、用户画像和部分 trace。这样不会把 Agent 高频变更的状态和业务订单混在同一个库里，也能让 checkpoint 使用 PostgreSQL saver 的能力。代价是跨库运维、备份、监控和一致性复杂度上升。

### Q5：为什么选择 LangGraph4j，而不是手写 while 循环？

答：手写循环可以完成基本 ReAct，但状态、节点跳转、恢复、中断、checkpoint、并发和可视化会逐渐变成隐式逻辑。LangGraph4j 把节点、条件边和状态通道显式化，便于把“规划→执行→继续决策→回答”建模成可检查的状态机。项目仍然需要自己处理工具安全、状态序列化、SSE 和业务确认，框架不是自动解决所有问题。

### Q6：项目里最重要的三个技术难点是什么？

推荐回答：

1. LLM 不确定性：通过技能白名单、结构化 Planner、错误分类、循环上限、写操作确认和 checkpoint 约束。
2. 秒杀一致性：Redis 预扣、RabbitMQ 发布/消费失败、MySQL 落单和死信处理之间的补偿与幂等。
3. 长连接与长上下文：通过滑动窗口、摘要、Delta checkpoint、SSE 断点续传和发送端去重控制资源和体验。

### Q7：如果让你重新设计，最先改什么？

答：先修复和验证安全边界，再做性能优化。当前代码审阅中 Text2SQL 的行级过滤顺序需要前移；之后会补跨用户访问回归测试、AST 级 SQL 校验和只读数据库账号。再做 RAG/Agent 质量评测自动化，把 GoldenEval 接入可控的离线 mock 或专用测试环境，最后才考虑微服务拆分和更复杂的缓存。

### Q8：项目当前有多少个 Agent 节点？

答：按当前 `GraphConfig` 是 5 个：Context、Planner、Agent、Tools、Answer。观察和判断职责已经合并进 ToolNode、AgentNode 和 skill SOP。这个问题经常用来检查是否真的读过代码。

### Q9：为什么 Planner 不直接选择具体工具？

答：Planner 先选择技能和目的，Agent 再根据执行上下文从技能对应的工具集合中作具体调用。这样可以把规划层和工具实现解耦，也能限制当轮可见工具数量。例如“在某店取号”规划出 `shop + queue`，Agent 先搜索店铺，再调用排队工具；Planner 不需要知道每个工具的参数细节。

### Q10：如何避免用户一次请求导致工具无限循环？

答：多层限制：

- `agent.graph.max-iterations` 限制图迭代。
- `max-retries` 限制错误重试。
- Planner 对 replan 有次数上限，当前代码是最多一次。
- 空结果会注入换思路提示，避免原参数重复调用。
- Agent 通过 pending tool 和计数器控制每一步。
- 最终仍会路由 Answer，给出已尝试范围和下一步建议。

## 8. 高频面试题：Agent 状态图与工具调用

### Q11：ReAct 在这个项目里具体是什么？

答：不是让模型自由输出一长段“思考”，而是把每一轮拆成“观察已有上下文和工具结果 → 决策是否调用工具以及调用哪个 → 执行工具 → 把结果写回消息 → 再次决策”。当前代码把决策放在 AgentNode，把执行放在 ToolNode，工具结果通过 `messages` 通道和 scratchpad 保存。

### Q12：Planner 的输出为什么使用 JSON？

答：后端需要稳定读取 `intent`、`complex`、`skills`、`plan`、`ask_user`、`cannot_fulfill` 等字段。当前使用 LangChain4j 的 JSON response format，并通过 `JsonParser` 做解析和兜底。需要注意 DeepSeek 对 JSON Schema 支持存在兼容性限制，所以项目使用 JSON object 约束而不是完全依赖 JSON Schema。

### Q13：`messages` 和 `scratchpad` 分别做什么？

答：`messages` 是标准对话/工具轨迹，包含 user、assistant tool_calls、tool result，适合回放、上下文注入和 checkpoint；`scratchpad` 是执行控制面，保存最后一次工具、参数、结果摘要、待执行工具、卡片声明、领域规则和内部标记。把事实消息和控制状态分开，能避免把所有内部控制字段都暴露给模型。

### Q14：Delta Channel 是什么？

答：状态通道不都采用同一种合并策略：

- `messages` 使用 appender，节点只返回新增消息，框架追加到历史。
- 普通字段使用 last-write-wins，节点返回新值覆盖旧值。
- `counters` 统一收敛为一个 Map，在 Context 入口重置，减少散落计数器造成的状态不一致。

这样节点返回的是状态增量，降低 checkpoint 写入量，也让节点职责更清晰。

### Q15：工具执行失败后怎么处理？

答：`ToolExecutor` 捕获异常后交给 `ErrorClassifier`，按 cause chain 和消息关键词分类为：

- `RETRYABLE`：超时、连接失败、限流等瞬态故障。
- `USER_FIXABLE`：未登录、缺参数、参数不合法等用户可修正问题。
- `FATAL`：SQL、数据完整性和未知错误等不可自动恢复问题。

分类结果以 `[retryable]` 等标签写入工具结果，Agent 可以决定重试、询问用户或结束。对外回答阶段还会做一次脱敏，不能把异常类名、SQL 和工具名直接返回用户。

### Q16：为什么错误分类不能只看异常类型？

答：同一个异常类型在不同场景下语义可能不同，很多第三方库还会用通用异常包装真实原因。因此项目先匹配异常消息，再沿 cause chain 检查异常类名，最后默认 FATAL。生产中还应补充结构化错误码，避免依赖中文字符串和异常文本。

### Q17：如何实现动态工具白名单？

答：`SkillRegistry` 从 `agent-skills/*/SKILL.md` 和 `tools.md` 加载技能、规则与工具名。Planner 输出 selected skills 后，Agent 只把对应 skill 的工具 specification 暴露给 LLM；`general` 作为长尾兜底。这样既减少模型选择空间，也降低不相关工具被误调用的概率。

### Q18：写操作为什么不能只靠 Prompt 说“请先确认”？

答：Prompt 是软约束，模型可能漏掉或错误理解。当前 `WriteGuard` 在代码中固定识别 `takeQueueNumber`、`cancelMyQueue`，AgentNode 发现写工具调用后不直接执行，而是把 `pendingWrite` 写入 checkpoint，Controller 发确认卡片。只有用户后续回复并通过确认意图处理后才真正执行。

### Q19：人工确认是怎么恢复的？

答：线程 id 使用 `user:{userId}`。新请求进入时先通过 `reactGraph.stateOf(config)` 查询最近状态；如果有 `pendingConfirmation`，Controller 用 `updateState` 注入 `USER_CHOICE`、清除挂起标志并把下一节点设为 Agent，然后以 resume 方式继续图执行。这样不需要从 Planner 重跑整轮，也不会丢失待确认的店铺和参数。

### Q20：`askUserToChoose` 和写操作确认有什么不同？

答：写操作是安全确认，通常暂停 SSE，发确认卡片和确认/取消按钮；`askUserToChoose` 是信息不足或候选过多时的人机协同，AnswerNode 先生成自然语言叙述，流式完成后再发选项按钮。两者都使用 checkpoint，但前端事件和恢复语义不同。

### Q21：如何防止工具调用结果诱导 Answer 阶段继续调用工具？

答：Answer 阶段不提供工具 specification。`AnswerInjection` 会删除 assistant tool_calls，把工具结果折叠成普通“工具[xxx]结果”文本，再注入回答规则和最近消息。这样模型只能基于证据生成回答，不会把工具 XML 重新输出给用户。

### Q22：为什么要把店铺结果做 compact？

答：店铺工具结果中可能有长图片 URL、重复评价字段和大量历史字段。Answer 只需要店名、对外 id、评分、人均、距离、亮点等字段，所以 `ShopResultIdObfuscator.compact` 在注入 LLM 前做字段裁剪和文本截断，降低上下文 token；前端卡片仍使用原始 messages，不影响展示完整性。

### Q23：`showCards` 为什么比 `[[id]]` 更可靠？

答：自由文本中的 `[[id]]` 容易受到长上下文、规则冲突和 token 切分影响，模型可能漏输出、改写或把它当内部 ID。`showCards(shopIds)` 是结构化 tool call，参数更容易被解析，且 UI 事件由后端生成。当前仍保留 `[[id]]` 兼容逻辑，是为了兼容历史会话和旧模型输出。

### Q24：showCards 会不会造成工具死循环？

答：会有这个风险。旧方案如果工具执行没有标准 tool result，模型可能认为调用没有完成而重复调用。当前 `ShowCardsTool` 走正常工具执行链并写回 tool result，AgentNode 同时拦截并记录 `_card_ids`，从而给模型明确反馈，避免反复声明。仍应通过“单次声明、重复声明、无效 id、模型连续调用”测试覆盖。

### Q25：什么情况下应该让 Agent 调用 Text2SQL，什么情况下不应该？

答：固定业务能力优先走确定性工具，例如店铺搜索、推荐、排队和订单查询；Text2SQL 作为复杂组合查询和长尾查询兜底。不能让 Text2SQL 代替所有固定工具，因为它的延迟、可解释性、权限控制和结果稳定性更差。

## 9. 高频面试题：上下文、记忆与 checkpoint

### Q26：上下文管理解决什么问题？

答：长对话会导致 token 成本上升、模型注意力稀释、工具轨迹膨胀和 checkpoint 变慢。项目使用滑动窗口、异步摘要、硬消息上限、最近多轮保留、历史搜索工具和回答阶段的消息折叠，兼顾当前指代能力和上下文预算。

### Q27：为什么摘要不能替代全部历史？

答：摘要有信息损失，尤其是店铺 id、用户选择、否定条件和细节参数。项目保留最近若干轮完整消息，旧历史通过摘要承载；当用户提及更早内容但摘要不够时，使用历史搜索工具按关键词回溯。工程上应把关键业务事实抽成结构化状态，而不是完全依赖自然语言摘要。

### Q28：DeltaPostgresSaver 怎么减少 checkpoint 体积？

答：每次保存时对比 parent state：消息只保存新增差集，普通字段只保存变化字段；每 5 个 step 写一次完整 snapshot 作为锚点。恢复时从最近 snapshot 开始按时间顺序回放 delta，重建最新完整 state。这样减少频繁全量序列化和网络/磁盘写入。

### Q29：Delta checkpoint 的风险是什么？

答：

- delta 和 snapshot 写入失败可能造成恢复链不完整。
- 多实例共享 thread 时，内存中的 step counter 不是全局一致的。
- 当前消息差集主要按 `role + content` 去重，两个相同文本消息可能被误认为同一条。
- schema 变更需要兼容旧 checkpoint。
- checkpoint GC、数据库膨胀和恢复耗时仍需监控。

改进方向是给每条消息分配稳定 message id，保存显式 parent checkpoint，计数器落库或按 checkpoint 推导，并增加故障注入测试。

### Q30：为什么图线程 id 使用 userId？有什么问题？

答：`user:{userId}` 能让用户跨请求恢复同一会话和待确认状态，简单可靠。但它把一个用户的所有对话绑定到同一个线程，无法天然区分多个并行会话；并发请求也可能互相覆盖 checkpoint。更好的设计是前端持有 sessionId，使用 `user:{userId}:session:{sessionId}`，同时对同一线程加并发控制。

### Q31：用户改主意时如何避免历史需求绑架当前请求？

答：Context 的核心规则明确近期表述优先；Controller 对新一轮请求走统一 `GraphInputFactory`，而确认恢复才走 updateState resume。Planner 重新根据当前 query 和已有证据规划，不应该盲目复用旧计划。

### Q32：如何处理用户画像和反思记忆？

答：Planner 阶段异步提取当前输入里的画像信息；Context 从 UserStore 读取画像，从 ReflectionStore 检索相似失败经验。画像适合稳定偏好，reflection 适合让 Agent 避免重复踩坑，两者都应限制长度、带时间或相关性，并避免把未经验证的模型总结当作事实。

## 10. 高频面试题：RAG

### Q33：项目的 RAG 完整链路是什么？

答：

```text
文档上传/文件监控
  → 格式转换
  → SHA-256 内容去重
  → AdaptiveSplitter 自适应切片
  → Embedding 批量向量化
  → 确定性 UUID
  → Qdrant upsert
  → BM25 全量索引重建

用户查询
  → LLM Query Rewrite 生成变体
  → 每个变体走向量检索和 BM25
  → RRF 融合去重
  → LLM Rerank
  → Top-K 返回给 Agent
```

### Q34：为什么需要混合检索？

答：向量检索擅长语义相似和同义表达，但对精确业务词、产品名、状态码和专有名词可能不稳定；BM25 擅长关键词精确匹配，但不理解同义和语义。两路召回后用 RRF 按排名融合，不要求两路分数可直接比较。

### Q35：RRF 公式是什么？

答：项目使用：

```text
score(d) = Σ 1 / (k + rank_i(d))
```

其中 `k=60`，rank 从 1 开始。一个文档在多路结果中都靠前，就会获得更高融合分。RRF 的优势是只依赖排名，避免向量相似度和 BM25 分数尺度不同带来的融合问题。

### Q36：为什么要先做 Query Rewrite？

答：用户口语可能很短、含代词或业务隐喻。改写成 2~3 个带同义词和正式表述的查询，可以增加召回覆盖。改写失败时降级为原始 query，避免 RAG 因一次 LLM 调用失败完全不可用。

### Q37：AdaptiveSplitter 的核心设计是什么？

答：优先识别 Markdown 标题、中文章节、节、小节等结构，保留 heading path；代码块、表格、图片先用占位符保护，避免递归切分破坏结构；没有结构时按段落、换行、句号等分隔符递归降级，最后才硬切；切片之间统一加 overlap。

### Q38：为什么不能简单按固定字符数切文档？

答：固定切分可能把标题和正文、代码块、表格或问答对拆开，导致召回片段缺少语义边界。结构优先能提高可读性和检索质量，但结构切分也可能产生长度不均，因此还需要递归切分和 overlap。

### Q39：如何保证文档摄入幂等？

答：先对原文做 SHA-256，内容 hash 放入内存去重集合；切片 id 用 `contentHash + index` 生成确定性 UUID，写入 Qdrant 使用 upsert。这样重复摄入同一内容不会无限增加 points，多实例同时写同一文档也有天然相同 id。生产环境中去重集合不能只放内存，应将 ingestion manifest 持久化或使用分布式锁。

### Q40：BM25 内存索引有什么优缺点？

答：优点是实现简单、查询快、适合当前知识库规模；项目用中文字符 bi-gram 和停用词过滤。缺点是进程重启丢失、多个实例不一致、全量 rebuild 成本随文档量增长。规模变大后应使用 Elasticsearch/OpenSearch 或把关键词索引外置，并做增量更新。

### Q41：Rerank 为什么放在 RRF 之后？

答：先用低成本的向量和 BM25 取得较大的候选集合，再让 LLM 对有限候选做精排，减少昂贵模型调用和上下文长度。Rerank 只在候选数大于 requestedTopK 时触发，避免少量结果的额外延迟。

### Q42：RAG 如何评估，而不是凭感觉？

答：拆成检索和生成两层指标：

- 检索：Recall@K、MRR、nDCG、空召回率、来源覆盖率。
- 生成：答案正确性、引用一致性、拒答准确率、幻觉率、延迟和 token 成本。
- 建立固定 query/文档 gold set，比较原始向量、混合检索、加 rerank 的增益。

当前项目有 GoldenEval 思路，但依赖私有 DB 和 DeepSeek key，不适合直接作为公共 CI；面试时应如实说明这一限制。

## 11. 高频面试题：Text2SQL 与安全

### Q43：Text2SQL 为什么要分成“选表→查 schema→生成 SQL”？

答：渐进式披露。把所有表和字段一次塞给 LLM 会浪费 token，也增加选错字段和泄露隐私的概率。项目先从 Redis 或 information_schema 得到表名，LLM 选择相关表，再只查询选中表的列结构，最后生成 SQL。

### Q44：Text2SQL 做了哪些安全校验？

答：当前代码包含：

- 只允许 SELECT/WITH/EXPLAIN 开头。
- 禁止多语句。
- 禁止 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE 等危险关键字。
- 禁止 LOAD_FILE、INTO OUTFILE、BENCHMARK 等危险函数/模式。
- FROM/JOIN 表名必须在本次允许表集合中。
- 禁止 password 敏感列。
- 强制 LIMIT，默认 20。
- 订单表设计了用户范围隔离逻辑。

但这些正则不是完整 SQL 安全边界，必须配合只读数据库账号、SQL Parser/AST 校验、查询超时、资源限额和行级权限。

### Q45：为什么表域白名单要 default-deny？

答：未来数据库新增表时，如果默认允许，LLM 可能自动查询新表中的隐私或管理数据。当前只把店铺、团购、评论、地区、订单、博客等 C 端业务表放进 `QUERYABLE_TABLES`，用户表等隐私表默认拒绝。

### Q46：订单查询如何防止用户 A 查到用户 B？

答：设计上对 `tb_voucher_order` 做 `USER_SCOPED_TABLES`，要求登录，并在 SQL 中追加当前用户的 `user_id` 条件。当前代码需要复核并修正“先执行、后 enforceRowLevel”的顺序；真正的回答应强调这是必须在执行前完成的安全门禁，并用跨用户测试证明。

### Q47：Text2SQL 失败后为什么允许 LLM 修复 SQL？

答：列名、别名或类型不匹配是可修复的执行错误。项目把 schema、失败 SQL 和截断后的数据库错误回喂给模型，最多修复 3 次，每次修复后重新经过安全校验和 LIMIT 加固。不能把数据库错误原文直接展示给用户。

### Q48：Text2SQL 的最大风险是什么？

答：不是只读 SELECT 就安全。还可能有敏感表、敏感列、跨用户越权、笛卡尔积、全表扫描、慢函数、注释绕过、CTE/子查询/别名解析不完整和错误信息泄露。最佳实践是：固定业务优先使用参数化工具，Text2SQL 使用只读账号、AST 白名单、表/列/行/时间/返回行数多重限制，并对 SQL 做审计。

### Q49：为什么固定查询不直接让 LLM 写 SQL？

答：固定查询可以用参数化 SQL 或 Service 层实现，稳定性更高、权限边界更清楚、延迟更低。Text2SQL 适合长尾和组合查询，但不应该成为所有业务的默认入口。

## 12. 高频面试题：Redis、缓存和一致性

### Q50：项目用了哪些缓存策略？

答：

- 店铺详情：逻辑过期 + Redis 分布式锁 + 后台重建，保证热点请求不被数据库重建阻塞。
- 店铺列表、地图、团购列表：Cache-Aside，命中返回，未命中查库并写缓存。
- 空列表写短 TTL，防止缓存穿透。
- 列表缓存 key 带筛选条件、地区、排序和页码，避免串页。
- 店铺列表和地图使用版本号 key 做批量失效。
- 写入/删除店铺和团购时主动删除相关缓存。

### Q51：逻辑过期和物理过期有什么区别？

答：物理过期由 Redis 删除 key，过期瞬间所有请求都可能回源；逻辑过期把业务过期时间放在 value 中，过期后先返回旧值，再由一个持锁线程异步重建。项目使用随机 token 加锁，并用 Lua 校验 token 后删除，防止误删其他线程的锁。

### Q52：为什么分布式锁释放必须用 Lua？

答：`GET` 后 `DEL` 不是原子操作，线程 A 读到自己的 token 后暂停，锁过期并被线程 B 获取，A 恢复后直接 DEL 会删掉 B 的锁。Lua 可以在 Redis 内原子完成“校验 token + 删除”。

### Q53：缓存穿透、击穿、雪崩怎么处理？

答：

- 穿透：空值缓存、参数校验、必要时布隆过滤器。
- 击穿：热点逻辑过期、互斥锁、后台重建。
- 雪崩：随机 TTL、分批预热、限流和多级缓存。

当前项目已经实现空值防穿透、逻辑过期和锁；随机 TTL、布隆过滤器和更完整的限流仍是可扩展项。

### Q54：为什么列表缓存 key 必须包含地区和筛选条件？

答：当前店铺查询受 `typeId`、`districtId`、`sortBy`、`foodCategory`、页码影响，任一条件遗漏都会把一个请求的结果返回给另一个请求。项目将这些维度拼进 key，并用列表版本号整体失效。

### Q55：缓存和数据库更新的顺序怎么选？

答：常见做法是先更新数据库，再删除缓存，因为删除缓存比更新缓存更不容易把旧逻辑写回。并发下仍可能出现“读旧值后回填”，所以项目用短 TTL、版本号和写路径主动失效兜底；强一致场景应使用 binlog、消息或版本校验。

### Q56：秒杀库存为什么不能在启动时每次都用 MySQL 覆盖 Redis？

答：运行中 Redis 已经发生预扣，但订单可能尚未落库。如果重启时用 MySQL 库存覆盖 Redis，会把未落库的预扣加回来，导致超卖。当前 `SeckillStockWarmer` 使用 `setIfAbsent` 只初始化缺失 key，并由 Lua 把缺失 key 视为库存不足，避免预热期间 nil 比较异常。

## 13. 高频面试题：秒杀、RabbitMQ 和最终一致性

### Q57：为什么秒杀入口使用 Redis Lua？

答：库存检查、库存扣减、一人一单集合写入必须是一个原子操作。如果拆成多个 Redis 命令，高并发下会出现多个请求同时通过检查。Lua 在 Redis 单线程执行，当前脚本还校验 begin/end 时间窗口，返回码区分库存不足、重复下单、未开始和已结束。

### Q58：为什么 Redis 扣库存后还要 MySQL 再扣一次？

答：Redis 是高并发入口的快速预扣，MySQL 是最终业务事实和持久化兜底。消费者落单时用 `stock = stock - 1 where stock > 0` 的条件更新，避免并发落库超卖；两处库存不是简单相加，而是需要通过消息可靠性、补偿和对账保持最终一致。

### Q59：Redis 预扣成功但 RabbitMQ 发布失败怎么办？

答：发布失败可能是同步抛错、Broker nack 或消息不可路由。项目通过 `SeckillCorrelationData` 携带订单上下文，ConfirmCallback 处理 nack；ReturnsCallback 没有 correlationData，就从退回消息体反序列化订单；三种路径都调用 Redis Lua，只有从一人一单集合移除成功时才增加库存，保证补偿幂等。

### Q60：RabbitMQ publisher confirm 和 consumer ACK 有什么区别？

答：publisher confirm 表示 Broker 已接收/确认发布，不能说明业务消费者已经处理；consumer ACK 表示消费者对一条投递消息处理完成。两者分别解决“消息有没有进入 Broker”和“消息有没有被业务成功消费”。项目两边都做了失败处理。

### Q61：为什么消费者要手动 ACK？

答：业务落库成功后再 ACK，异常时可以重试或进入 DLQ；自动 ACK 会在业务处理前确认，进程崩溃可能丢消息。当前消费者在 `handleVoucherOrder` 成功后 `basicAck`，失败时重新发布带重试计数的消息或 `basicNack(requeue=false)`。

### Q62：项目如何实现有限重试？

答：从消息头 `x-retry-count` 读取次数，使用 2s、5s、10s 退避，超过 3 次不再无限重试，而是拒绝并通过队列声明的 dead-letter exchange 进入 DLQ。DLQ 消费者把失败订单落到 `tb_dead_order`，供后台人工重放或丢弃。

### Q63：当前重试实现有什么性能问题？

答：消费者线程中 `Thread.sleep` 会占住 listener 线程，prefetch=1 时尤其影响吞吐。更好的实现是 RabbitMQ TTL + DLX、延迟消息插件或独立 retry queue，把等待时间交给 Broker；同时需要处理发布重试消息失败和原消息 ACK 的原子性问题。

### Q64：如何保证消费者幂等？

答：三层：

- 订单 id 已存在则直接返回，不重复扣库存。
- userId 维度 Redisson 锁串行化同一用户订单处理。
- MySQL `tb_voucher_order` 上有 `(user_id, voucher_id)` 唯一索引作为最终兜底。

幂等不能只依赖分布式锁，因为锁会过期、消息可能跨实例重复投递，最终约束必须放在数据库。

### Q65：为什么消费者还要做时间和券状态校验？

答：消息可能延迟、重试或死信重放，消费时的业务状态可能已经变化。消费者落库前重新校验券类型、上下架状态、开始结束时间和秒杀库存，避免把过期或下架券写成有效订单。

### Q66：死信重放和死信丢弃分别怎么处理库存？

答：死信进入时保留 Redis 预留，不立刻回补，因为人工可能重放；重放成功发布后继续消费流程；确认丢弃且数据库没有订单时，使用幂等 Lua 回补 Redis 库存并移除一人一单标记。重放和丢弃都要防重复操作，状态字段和数据库检查共同保证。

### Q67：秒杀链路出现 Redis、RabbitMQ、MySQL 三者不一致怎么排查？

答：按订单 id 建立审计链：

1. 查 Redis stock 和 order set 是否有预留。
2. 查 RabbitMQ 主队列、重试消息、DLQ 和 `x-death` 原因。
3. 查 MySQL 订单和唯一索引。
4. 查发布确认、消费日志、trace 和 dead_order。
5. 进行可补偿的对账：已落库但 Redis 未清理、无订单但有预留、Redis 库存与 MySQL 库存差异。

补偿操作必须带幂等条件，不能人工直接 `INCR` 造成二次回补。

### Q68：如果 MySQL 落单成功但 ACK 之前进程崩溃，会发生什么？

答：RabbitMQ 会重新投递，消费者再次收到消息；`getById(orderId)` 或唯一索引判断已落库，直接幂等成功，再 ACK，不会重复扣库存。

### Q69：如果消息发布成功但 ConfirmCallback 丢失，会不会回补库存？

答：异步确认丢失属于发布可靠性设计的一部分。仅靠回调无法得到绝对保证，应结合发布确认超时扫描、订单/预留对账和可恢复状态表。当前项目已处理 nack、不可路由和同步异常，但生产级系统还应考虑确认回调丢失和回补任务。

### Q70：为什么订单 id 要转成字符串返回前端？

答：雪花 id 可能超过 JavaScript 安全整数范围，前端 Number 会丢精度，随后用错误 id 查询会出现“订单不存在”。项目在订单接口中把 id 转为字符串返回，前端也保持字符串透传。

## 14. 高频面试题：SSE、前端和卡片协议

### Q71：为什么用 SSE 而不是 WebSocket？

答：当前主要是服务端向客户端单向推送 LLM token、工具过程和卡片事件，SSE 基于 HTTP、浏览器原生支持、接入 Nginx 简单；用户输入仍然通过 POST。只有需要双向低延迟通信、多人协同或服务端主动双向控制时才更适合 WebSocket。

### Q72：为什么 SSE 接口使用 POST？

答：对话请求包含较长自然语言、定位和地区等 JSON body，POST 更适合承载复杂输入；浏览器原生 `EventSource` 只支持 GET，所以前端使用 `fetch` 读取 `ReadableStream`，手动解析 SSE。

### Q73：SSE 事件有哪些？

答：当前前端归约器处理：

- `thinking`：Context/Planner/Agent/Answer 进度。
- `tool`：工具调用和工具过程卡片。
- `answer_chunk`：回答 token 增量。
- `cards`：结构化店铺卡片。
- `actions`：确认、取消或选择按钮。
- `answer`：回答完成。
- `retry`、`error`、`done`：重试、错误和结束。

### Q74：如何防止流式回答重试时重复显示？

答：后端在连接中断后最多重试 3 次，续传 prompt 携带已经生成的 partial；`StreamingResumeDedupe` 以 partial 尾部窗口找最长前缀重叠，只发送新增部分。SSE answer_chunk 还带 event id，前端 chatMachine 忽略不前进的事件 id。后端发送端去重是主防线，前端不是靠全文启发式去重。

### Q75：Nginx 为什么要关闭 SSE buffering？

答：如果代理缓冲响应，后端虽然逐 token send，浏览器可能过很久才收到一整块，打字机效果和实时过程都会失效。配置中对 `/chat/` 设置 `proxy_buffering off`、关闭缓存并延长读取超时；Controller 还发送连接 comment 冲开容器/代理缓冲。

### Q76：卡片如何插入到回答中？

答：后端在流式回答中累积已发送文本，发现已声明店铺的完整店名后发送带 anchor 的 cards 事件；前端 `appendCards` 根据 anchor 拆分 text block，在店名后插入 cards block。为避免 `**店名**` 的 Markdown 闭合符被拆开，后端/前端会吸收紧随店名的 `*`、`_` 或反引号。找不到 anchor 时再在回答末尾兜底。

### Q77：为什么前端要用纯函数 chatMachine？

答：SSE 事件归约和 DOM/UI 解耦：输入是旧 messages 和事件，输出是新 messages，便于对流式 chunk、卡片穿插、事件重复、actions 和 finalize 做单元测试，也减少 Vue 组件里状态分支。

### Q78：LLM 输出的 Markdown 怎么防 XSS？

答：前端用 `marked` 转 HTML，再用 DOMPurify sanitize 后通过 `v-html` 渲染。LLM 输出属于不可信输入，即使 Prompt 要求只输出 Markdown，也不能跳过清洗。生产中还应限制链接协议、图片来源和 HTML 标签集合。

### Q79：Axios 401 如何处理？

答：请求拦截器注入 access token；响应遇到 401 时使用 refresh token 换新 token 后重试原请求，并通过单飞逻辑避免并发 401 同时刷新导致 token 轮换竞态；重试后仍 401 则强制回登录。

### Q80：前后端 ID 混淆解决了什么问题？

答：自增数据库 id 容易被枚举，也不适合直接放到 LLM 上下文和前端 URL。项目使用服务端盐派生 Sqids 字母表，生成短、URL-safe 的 opaque id；解码后做 encode 往返校验，防止任意合法字符串被错误解析；纯数字仍兼容旧客户端。它不是权限控制，后端仍必须校验用户是否有权访问资源。

## 15. 高频面试题：认证、接口和传统业务

### Q81：登录和 Token 续期怎么设计？

答：登录接口获取 access/refresh token，刷新拦截器校验和续期，LoginInterceptor 对受保护接口做登录校验，公共接口显式排除。用户 id 进入 UserHolder/ToolContext，普通业务和 Agent 工具都从统一用户上下文取当前用户。

### Q82：为什么不能只依赖前端传 userId？

答：前端参数不可信，用户可以篡改。当前用户必须从 JWT/refresh token 解析后放到服务端上下文，订单、排队、Text2SQL 行级过滤和评论写入都使用服务端身份。接口传来的 userId 只能作为业务参数，不应作为权限依据。

### Q83：排队取号为什么要做服务端状态校验？

答：前端按钮置灰只是体验优化，不能防恶意请求。后端要校验店铺存在、是否开启排队、用户是否已有排队中记录、人数参数是否合法，并在取消/叫号时用状态条件更新防止重复操作。

### Q84：评论计数为什么采用延迟重算？

答：每发表一条评论都执行全表 AVG/COUNT 会放大写请求成本。项目写入评论后保持评论明细，店铺 comments 和 score 通过定时任务按评论表重算；读取路径快，代价是统计值存在短暂延迟。需要向用户展示强实时计数时可以用增量计数 + 定期校准。

### Q85：为什么要用地区维度扩展 Geo 查询？

答：Redis GEO key 按 `districtId` 和 `typeId` 隔离，避免不同城市相同类型的店铺混在一个地理集合中；用户定位使用高德 GCJ-02 坐标，前端地区选择器提供默认地区中心，Agent context 直接注入该定位。

## 16. 高频面试题：测试、可观测性与部署

### Q86：项目有哪些测试？

答：后端有 Agent 图边、状态、JSON 解析、错误分类、工具参数、ContextEditor、Text2SQL 校验、缓存、秒杀服务、死信、JWT/拦截器等测试；前端有 SSE 解析、chatMachine、卡片 anchor、HTTP、认证、店铺查询、地图 marker 等测试。本次分析实际执行 `npm.cmd test`，11 个测试文件、81 个测试全部通过。

### Q87：为什么 GoldenEval 不能直接放公共 CI？

答：它需要私有 MySQL、Redis、真实 DeepSeek API 和特定数据，模型输出还有随机性，运行成本和稳定性不适合公共流水线。可以把确定性状态机、工具参数和安全校验放 CI；把真实 LLM 评测放受控 nightly job，记录模型版本、Prompt 版本、数据版本和阈值。

### Q88：如何测试秒杀而不是只测 Service 方法？

答：测试分层：

- Lua 脚本：并发库存、一人一单、时间窗口、库存为 0。
- Redis/RabbitMQ 集成：发布确认失败、不可路由、消费重试、DLQ、重放和回补。
- MySQL：唯一索引、条件扣库存、订单幂等、取消/退款 CAS。
- 压测：吞吐、P99、消息堆积、数据库写入、库存对账。

基础设施可连接时，不能只用 mock 假装验证消息和缓存语义。

### Q89：Agent 可观测性记录什么？

答：请求 traceId、用户、节点步骤、工具名、参数摘要、结果摘要、状态和耗时；`LlmTraceListener` 记录 Planner/Agent 等 LLM 的 prompt、response、token usage、模型和 duration，管理端可以查看会话时间线和单条 LLM 详情。敏感信息必须脱敏、分级访问并设置保留周期。

### Q90：如何排查“Agent 答错”？

答：先按 traceId 看完整链路：

1. 输入是否被清洗或截断。
2. Context 注入了哪些画像、摘要和历史。
3. Planner 是否选对 skill。
4. Agent 暴露了哪些工具，参数是否正确。
5. Tool 是否真的返回了数据，是否空结果/错误分类。
6. AnswerInjection 给模型看到了哪些证据。
7. 流式输出是否被续传或前端归约破坏。

这比只看最终回答更容易定位根因。

### Q91：项目如何部署？

答：前端 Vite build 输出到 Nginx `html/hmdp-app`，Nginx 提供 SPA history fallback、图片静态目录和 API/SSE 反向代理；Spring Boot 运行在 8081；Redis、RabbitMQ、PostgreSQL、Qdrant 通过本地或服务器环境提供。敏感配置使用 `application.yaml`/环境变量，不提交真实 key、密码和服务器地址。

### Q92：为什么本地 `@Scheduled` 和服务器 xxl-job 都存在？

答：本地没有调度中心时需要 `@Scheduled` 兜底；服务器有 xxl-job 后，由调度中心统一触发，配置开关避免两套任务同时执行。生产中要确保任务幂等、分布式锁和执行器超时，避免多实例重复重算或重复取消。

## 17. 面试官可能继续追问的“反问题”

### Q93：这个项目最大的不足是什么？

建议诚实回答：

- Text2SQL 当前需要把行级约束前移并改成更强的 AST/权限校验。
- GoldenEval 依赖真实外部服务，公共 CI 覆盖不足。
- RabbitMQ 重试在消费者线程 sleep，吞吐和故障恢复还可优化。
- Agent thread 目前按用户维度，多个并行会话的隔离需要加强。
- BM25 是内存全量索引，多实例一致性和大规模扩展不足。
- 缓存与 MySQL 仍是最终一致，缺少统一对账平台。

重点是同时说出下一步方案，而不是只说“暂时没问题”。

### Q94：如果 DeepSeek 不可用，系统会怎样？

答：Planner/Agent 调用失败会进入错误处理和友好降级；流式 Answer 有有限次数的指数退避和 partial 续传，全部失败则返回中断提示并持久化失败轮次。RAG query rewrite 失败会降级到原始 query。更进一步可以为固定业务提供确定性接口兜底，或者引入模型路由和熔断。

### Q95：如果 Redis 挂了，哪些功能受影响？

答：缓存命中和 Geo 查询受影响，店铺详情逻辑过期无法工作；秒杀入口无法安全执行 Lua，应该快速失败而不是绕过 Redis 直接打 MySQL；Token/分布式锁也可能受影响。工具缓存读取失败可以降级执行，但秒杀预扣不能降级。

### Q96：如果 RabbitMQ 挂了，为什么不直接同步写 MySQL？

答：秒杀高峰同步写库会把数据库暴露在突发流量下，失去削峰和异步解耦。可以提供受控降级，例如限流后进入持久化待处理表，但不能简单绕过 Redis 原子预扣和一人一单逻辑，否则会破坏一致性。

### Q97：如果用户连续点击确认按钮怎么办？

答：恢复操作和写工具都必须幂等。checkpoint 恢复要有状态条件，已经清除的 pendingWrite 不再重复执行；排队记录需要唯一业务约束或状态 CAS；秒杀订单由订单 id 和 `(user_id, voucher_id)` 唯一索引兜底。前端禁用按钮只是辅助。

### Q98：如果两个用户同时抢最后一张券，谁保证最终只有一个订单？

答：Redis Lua 先原子判断并扣减，只允许一个请求获得预留；消费者 MySQL 又使用条件库存更新和唯一索引兜底。若其中一个消息发布/消费失败，必须走幂等补偿，否则 Redis 预留会长期吞库存。

### Q99：为什么不用 UUID 作为所有对外 id？

答：UUID 不可枚举且简单，但长、可读性差、会改变前后端展示和存储链路；本项目使用 Sqids 生成短 URL-safe opaque id，同时保留数据库雪花/自增 id 内部使用。无论使用 Sqids 还是 UUID，都不能替代权限校验。

### Q100：如果让你把 Agent 拆成微服务，怎么拆？

答：先按职责拆，而不是按类拆：

- Agent Orchestrator：状态图、checkpoint、SSE。
- Tool Gateway：工具执行和权限上下文。
- RAG Service：摄入、检索、重排。
- Business API：店铺、订单、排队和秒杀。

需要定义 traceId、user/session identity、tool result schema、checkpoint ownership、超时和幂等契约。第一步可以只把 RAG 或 Agent 推理拆出去，因为它们延迟高、资源模型不同；业务写操作仍应由业务服务掌握。

## 18. 面试演示脚本

建议现场演示一条“查店→选店→取号”的完整链路：

1. 输入“帮我找鼓楼区评分高、适合带孩子的火锅店”。
2. 展示 Planner 选择 `shop` skill。
3. 展示 Agent 调用店铺查询工具并返回结构化卡片。
4. 输入“就在第一家取号，3个人”。
5. 展示先查店/确认参数，再出现确认卡片，不立即写库。
6. 点击“确认”。
7. 展示 checkpoint 恢复、工具执行和排队结果。
8. 打开 AgentTrace，说明 Planner、工具、Answer 和耗时。

秒杀演示不要只点一次按钮，应该准备讲解或测试：Lua 返回码、RabbitMQ 队列、消费者 ACK、重试头、DLQ、唯一索引和 Redis 回补。

## 19. 面试时可以主动展示的代码文件

| 主题 | 建议展示文件 |
|---|---|
| 状态图编排 | `hm-dianping/src/main/java/com/hmdp/agent/graph/GraphConfig.java` |
| 状态与通道 | `agent/graph/state/ReActAgentState.java`、`StateSchema.java` |
| 规划/决策/执行 | `agent/graph/nodes/PlannerNode.java`、`AgentNode.java`、`ToolNode.java`、`ToolExecutor.java` |
| 人工确认 | `agent/graph/nodes/WriteGuard.java`、`controller/ReactStreamController.java` |
| checkpoint | `agent/graph/checkpoint/DeltaPostgresSaver.java` |
| 上下文 | `agent/memory/context/ContextNode.java`、`SlidingWindowManager.java`、`AnswerInjection.java` |
| 技能路由 | `agent/skill/SkillRegistry.java`、`src/main/resources/agent-skills/*` |
| RAG | `rag/ingestion/IngestionService.java`、`rag/splitter/AdaptiveSplitter.java`、`rag/retrieval/RetrievalService.java` |
| Text2SQL | `agent/tool/graph/Text2SqlTool.java`、`agent/tool/text2sql/SqlGenerator.java`、`TableSchemaService.java` |
| 秒杀入口 | `service/impl/VoucherOrderServiceImpl.java`、`src/main/resources/seckill.lua` |
| MQ 可靠性 | `config/RabbitMQConfig.java`、`listener/VoucherOrderConsumer.java`、`DeadLetterConsumer.java` |
| 缓存 | `utils/CacheClient.java`、`service/impl/ShopServiceImpl.java` |
| 前端流式 | `frontend/src/utils/sse.js`、`frontend/src/stores/chatMachine.js`、`components/AiChat.vue` |
| 测试 | `hm-dianping/src/test/java/com/hmdp/agent/eval/GoldenEvalTest.java`、`frontend/src/__tests__/chatMachine.test.js` |

## 20. 最后一天冲刺清单

- 能在 30 秒讲完项目定位，在 2 分钟讲完 Agent 和秒杀两条链路。
- 能画出当前 5 节点状态图，不把早期拆分的观察/判断职责误认为当前独立节点。
- 能解释 Redis Lua、RabbitMQ confirm、consumer ACK、DLQ、唯一索引各自解决什么问题。
- 能解释 logical expire、互斥锁、Lua 解锁、空值缓存和列表版本号。
- 能解释 RAG 的切片、Embedding、BM25、RRF、Rerank 和幂等摄入。
- 能指出 Text2SQL 的权限边界，并知道当前 `enforceRowLevel` 顺序需要修复/复核。
- 能解释 SSE 事件、断点续传、发送端去重和前端 chatMachine。
- 不把模型成功率、GoldenEval 结果、线上吞吐说成未经当前环境验证的事实。
- 面试前重新运行前端测试；本次分析基线为 81/81 通过。
- 后端演示前确认 MySQL、Redis、RabbitMQ、PostgreSQL、Qdrant、DeepSeek 配置和测试数据可用。
- 删除或脱敏工作区中的真实密码、API Key、服务器 IP、临时数据和爬虫产物。

## 21. 适合反问面试官的问题

- 团队目前更重视传统业务稳定性，还是 AI Agent 的评测和产品化？
- 生产环境对模型调用的延迟、成本、可用性和数据安全分别有什么目标？
- 业务写操作是否统一通过领域服务完成，还是允许 Agent 直接编排多个写接口？
- 团队如何建设 LLM 的离线评测集、回归门禁和线上质量监控？
- 当前系统的缓存、消息、数据库是否有统一的对账和补偿平台？

## 22. 后端专题：排队取号功能

### 22.1 排队功能的业务目标

排队功能解决的是“用户在线取号、查询进度、取消排队，商家按顺序叫号”的问题。它的核心要求不是简单插入一条 `tb_queue_ticket` 记录，而是：

- 排队号按店铺、按天递增。
- 同一个用户同一时间只能有一个活跃排队记录。
- 多个用户并发取号时不能拿到重复号。
- 多个商家员工并发叫号时不能叫到同一个号。
- 用户取消、商家叫号后，等待集合和用户索引要同步更新。
- Redis 高并发查询和排序，MySQL 保留历史记录和状态兜底。

### 22.2 排队功能的后端链路怎么设计？

可以按下面这条链路回答：

```text
用户/Agent
  → POST /queue-ticket/take
  → QueueTicketController
       ├─ decodeOrId：混淆 shopId 还原真实店铺 id
       ├─ 读取当前登录用户
       └─ 调用 QueueTicketService
  → 校验店铺存在、是否开启排队、人数参数
  → Redis 检查用户是否已有活跃票
  → Redis INCR 生成当前店铺当天排队号
  → Redis ZSET 加入等待队列
  → Redis ZRANK 计算前方排队数
  → Redis HASH 保存票据详情
  → Redis String 保存 userId → ticketId 的索引
  → MySQL 写入 tb_queue_ticket 作为持久化记录
  → 返回 ticketId、排队号、人数、前方人数、预计等待提示
```

查询、取消和叫号分别走：

```text
查询我的排队：queue:user:{userId}
  → ticketId
  → queue:ticket:{ticketId}
  → queue:waiting:{shopId}:{date} 做 ZRANK

取消排队：
  → 校验当前用户持有 ticketId
  → 校验状态为排队中
  → ZREM 等待集合
  → HASH 状态改为已取消
  → 删除用户当前排队索引
  → 同步 MySQL 状态

商家叫号：
  → ZPOPMIN 等待集合取最小 score
  → 更新 queue:current:{shopId}:{date}
  → 删除用户当前排队索引
  → HASH 状态改为已叫号
  → 同步 MySQL 状态
```

### Q101：为什么排队号用 Redis INCR，而不是 MySQL MAX(queue_number)+1？

答：`MAX + 1` 在并发下会出现多个请求读到相同最大值，除非加锁，数据库压力也更大。Redis `INCR` 是原子的，适合高并发生成序号。key 中包含 `shopId` 和 `yyMMdd`，所以每个店铺每天从自己的计数器开始。MySQL 仍然保留最终排队记录，但不承担高峰期序号生成。

### Q102：排队功能用了哪些 Redis 数据结构？

| Key | 类型 | 作用 |
|---|---|---|
| `queue:seq:{shopId}:{yyMMdd}` | String | `INCR` 生成当天店铺排队号 |
| `queue:waiting:{shopId}:{yyMMdd}` | ZSET | score 是排队号，member 是 `userId:peopleCount` |
| `queue:current:{shopId}:{yyMMdd}` | String | 当前叫号 |
| `queue:user:{userId}` | String | 用户当前 ticketId，限制一人一票 |
| `queue:ticket:{ticketId}` | Hash | 排队号、店铺、用户、人数、状态等详情 |

这些 key 的 TTL 约为一天加一个小时缓冲，避免历史排队运营数据长期占用 Redis。

### Q103：为什么等待队列使用 ZSET？

答：ZSET 可以同时保存“成员”和“有序 score”。项目把排队号作为 score，查询前方人数使用 `ZRANK`，查看前 20 个等待用户使用 `ZRANGE`，叫号使用 `ZPOPMIN` 取最小排队号。相比 List，ZSET 更适合按序查询和原子弹出最小值。

### Q104：如何保证同一个用户不能在多个店铺同时排队？

答：使用全局用户索引 `queue:user:{userId}` 保存当前 ticketId。取号前先读取该 key，并检查对应的 ticket hash 是否存在；如果存在就拒绝再次取号。取消或叫号后删除该索引，用户才可以重新排队。这个设计表达的是“一名用户全局只能有一个排队中记录”，如果未来要支持多店同时排队，key 应改成 `queue:user:{userId}:{shopId}`，并重新定义业务规则。

### Q105：取号流程中哪些步骤是原子的，哪些不是？

答：单个 Redis 命令如 `INCR`、`ZADD`、`ZPOPMIN` 本身是原子的；但完整取号是“检查用户票据 → INCR → ZADD → HSET → SET 用户索引 → 写 MySQL”多个操作，当前实现不是一个 Lua 脚本或 Redis 事务。因此进程在中间崩溃，可能产生排队集合已有成员但详情没写完，或者详情存在但用户索引没写入的脏状态。

面试时可以主动说明改进：把“检查活跃票、生成序号、写等待集合、写详情、写用户索引”收敛为 Lua 脚本；或者使用 Redis Stream/Outbox 做可靠持久化，再增加定时对账和孤儿数据清理。

### Q106：当前代码里的 `persistAsync` 真的是异步吗？

答：从当前实现看，方法名叫 `persistAsync`，但调用链里直接执行 `save(t)`，没有提交线程池或消息队列，所以实际上是同步 MySQL 持久化，只是把异常捕获后记录日志。面试时不要把它说成真正异步。若要改成异步，需要明确可靠性方案：同步写 Redis 后投递持久化消息，消费端幂等写 MySQL，并在投递失败时可重试或进入补偿表。

### Q107：Redis 和 MySQL 在排队功能中的角色是什么？

答：Redis 是运营态主存，负责当前等待队列、序号、实时进度和高并发读写；MySQL 的 `tb_queue_ticket` 保存历史和持久化状态，用于审计、后台查询和 Redis 数据丢失后的恢复。当前实现中 Redis 状态更新后写 MySQL，MySQL 失败只记录日志，因此存在短暂或长期不一致风险，生产环境应增加可靠消息、重试和对账任务。

### Q108：用户取消排队时如何防止越权？

答：Controller 接收 ticketId 后，Service 不直接相信前端参数，而是先读取服务端 `queue:user:{userId}`，要求它等于请求 ticketId；否则返回“只能取消自己的排队”。然后再检查票据状态必须是 0（排队中），之后才从 ZSET 移除并更新状态。ticketId 本身不是权限凭证，真正的权限依据是当前登录用户身份和服务端索引。

### Q109：商家叫号为什么用 `ZPOPMIN`？

答：`ZPOPMIN` 在 Redis 内原子地取出 score 最小的成员。多个叫号请求并发执行时，Redis 会保证同一成员只被一个请求弹出，避免两个工作人员同时叫到同一个排队号。取出后再更新当前叫号、用户索引、票据状态和 MySQL。

### Q110：叫号和取消同时发生，会不会出现状态错乱？

答：当前实现的 Redis 多步操作仍不是完整事务：取消先读 hash 状态再 ZREM，叫号可能同时 ZPOPMIN；二者交错时需要进一步做状态 CAS 或 Lua 脚本统一判断。例如取消脚本应同时检查状态为 0、删除 ZSET member、修改 hash 状态和删除用户索引；叫号脚本也要把弹出、状态变更和用户索引清理放在同一个原子操作中。MySQL 更新还应使用 `WHERE status=0` 的条件更新，避免重复状态迁移。

### Q111：排队状态有哪些？

答：数据库定义了：

- `0`：排队中。
- `1`：已叫号。
- `2`：已取消。
- `3`：已完成/已入座。

当前主要链路覆盖排队中、已叫号和已取消，状态 3 是模型层预留的后续状态。面试时要说明状态迁移，而不是只背数字：`排队中 → 已叫号 → 已完成`，或者 `排队中 → 已取消`，已叫号后不能再取消。

### Q112：排队号为什么按日期拆 key？跨天如何处理？

答：key 使用 `shopId + yyMMdd`，自然实现每日重新编号；同时给 key 设置约一天加一小时的 TTL，覆盖跨天边界。生产上还要统一时区，避免应用服务器时区不一致导致同一时刻生成不同日期；建议明确使用 `Asia/Shanghai`，而不是依赖系统默认时区。

### Q113：如果 Redis 宕机，排队功能能否降级到 MySQL？

答：不能简单把所有请求直接切到 MySQL，因为 MySQL `MAX+1`、实时排名和并发叫号都需要重新设计。可选方案是：短时间快速失败并提示稍后重试；或者准备基于数据库行锁/序列表的降级实现，但必须接受吞吐下降，并保证序号、状态和恢复逻辑与 Redis 路径一致。恢复后还要做 Redis 重建和数据对账。

### Q114：排队接口还需要哪些权限控制？

答：用户取号、查询和取消需要登录；取消必须校验票据归属；商家叫号还应校验当前用户是否是该店铺员工或管理员。当前展示的 Controller/Service 主要体现登录和票据归属校验，面试时可以主动指出叫号接口需要补充店铺角色/店铺归属校验，不能只靠前端管理页面隐藏入口。

### Q115：排队功能应该怎么测试？

答：至少覆盖：

- 店铺不存在、关闭排队、未登录、人数小于 1。
- 同一用户重复取号、不同用户并发取号、序号递增和跨天 key。
- 查询自己的排队和前方人数。
- 只能取消自己的票、已叫号不可取消、重复取消。
- 两个并发叫号不能弹出同一个用户。
- Redis 写到一半失败、MySQL 持久化失败、Redis 数据过期后的恢复。
- Agent 确认卡片到 `pendingWrite`、用户确认恢复、取消恢复的完整链路。

### 22.3 排队功能的面试总结回答

> 排队功能采用 Redis 作为实时运营层，MySQL 作为持久化层。取号时先校验用户、店铺和排队开关，再用按店铺按天的 Redis INCR 生成排队号，使用 ZSET 保存等待顺序，Hash 保存票据详情，String 保存用户当前票据索引；查询通过 ZRANK 计算前方人数，取消通过用户索引校验归属后移除 ZSET 成员，叫号使用 ZPOPMIN 原子弹出最小号。当前实现已经解决了高频排序和并发叫号问题，但完整取号/取消仍是多个 Redis 操作，生产上会用 Lua 或可靠消息收敛原子性，并补充 Redis/MySQL 对账和商家权限校验。

## 23. 后端/前端专题：高德地图 JS API 是怎么引入和使用的？

### 23.1 先给面试官的简短回答

> 项目没有把高德 JS SDK 直接写死在 `index.html`，而是在 `MapView.vue` 的 `onMounted` 阶段按需动态创建 script 标签，地址是 `https://webapi.amap.com/maps?v=2.0&key=${VITE_AMAP_JS_KEY}`。加载完成后通过 `window.AMap` 创建地图实例，再调用后端 `/shop/map` 接口获取当前地区的商家坐标，使用 `AMap.Marker` 创建 marker，点击跳店铺详情，鼠标悬停显示店名。Key 放在 `frontend/.env` 的 `VITE_AMAP_JS_KEY`，构建时注入前端；它不是秘密，所以生产环境要配置高德域名白名单和调用范围限制。项目坐标统一使用高德的 GCJ-02。

### 23.2 高德地图 JS 的实际加载流程

```text
MapView.vue onMounted
  → location store ensure()
       → GET /region/list
       → 恢复当前城市/地区和中心经纬度
  → GET /shop-type/list
  → loadAmap()
       ├─ window.AMap 已存在：直接复用
       ├─ 已有 script[data-amap]：等待 load/error
       └─ 没有 script：动态创建 script
             src=https://webapi.amap.com/maps?v=2.0&key=VITE_AMAP_JS_KEY
  → new window.AMap.Map(mapEl, { zoom: 13, center: [centerX, centerY] })
  → GET /shop/map?districtId=&typeId=
  → 后端按地区/分类查店铺，返回 x/y
  → new AMap.Marker({ position: [shop.x, shop.y] })
  → click：router.push('/shop/' + shop.id)
  → mouseover：setLabel 显示店名
  → setFitView 自动调整视野
```

### Q116：为什么不直接在 `index.html` 里写高德 script？

答：当前采用按需加载，只有进入地图页才加载 SDK，减少首页首屏资源和不必要的第三方请求；同时可以在组件中统一处理加载成功、失败和重复加载。直接写 `index.html` 的优点是简单，但所有页面都会加载地图 SDK。项目采用动态加载时要注意 script promise 复用，避免组件重复挂载时重复插入脚本。

### Q117：项目里高德 Key 放在哪里？前端 `VITE_` 变量安全吗？

答：示例配置是 `frontend/.env.example` 中的 `VITE_AMAP_JS_KEY=your-amap-js-key`，代码通过 `import.meta.env.VITE_AMAP_JS_KEY` 读取。Vite 的 `VITE_` 变量会在构建时打包到浏览器，任何用户都可以在源码或网络请求中看到，所以它不是后端意义上的秘密。

正确的安全措施是：

- 高德控制台配置 Web 端域名白名单/安全域名。
- 限制 Key 的 API 类型和调用来源。
- 不把高德 Web Service 的服务端 Key 放入前端。
- 服务端爬取或调用 Web API 时使用独立服务端 Key，通过环境变量注入。
- 示例文件只放占位符，真实 Key 不提交 Git。

### Q118：地图中心点从哪里来？

答：前端 `location` Pinia store 通过 `/region/list` 获取城市和地区，用户选择后把 `cityId`、`districtId`、`centerX`、`centerY` 保存到 localStorage；没有缓存时默认福州鼓楼区中心。`MapView` 用当前地区中心初始化 AMap，后端查询也带同一个 `districtId`，避免地图中心和商家数据范围不一致。

### Q119：地图 marker 的数据链路怎么走？

答：前端调用 `shopApi.forMap`，请求 `/shop/map`；`ShopController` 接收 `districtId` 和可选 `typeId`，`ShopServiceImpl.queryShopsForMap` 按地区/类型查询店铺列表，并使用列表 Cache-Aside 缓存。返回的店铺对象包含 `id`、`name`、`typeId`、`x`、`y` 等字段，前端用 `[s.x, s.y]` 创建 marker。

这里的 `x` 是经度，`y` 是纬度；Redis GEO 和 AMap 都要求经度在前、纬度在后，不能把顺序写反。

### Q120：为什么地图接口不直接返回所有商家，而要传 districtId/typeId？

答：地区和分类是天然的数据范围过滤，可以减少 MySQL 返回量、前端 marker 数量和地图渲染开销；同时缓存 key 也能按查询维度隔离。当前地图页切换分类时重新调用 `/shop/map`，后端按 `districtId + typeId` 返回数据。

### Q121：高德地图的坐标系是什么？项目为什么强调 GCJ-02？

答：高德中国大陆地图使用 GCJ-02。项目数据库的店铺 `x/y`、城市地区中心点和前端 AMap 都按 GCJ-02 处理；Redis GEO 的地理距离查询也使用同一套坐标。如果把 GPS 的 WGS84 或百度 BD-09 坐标直接传给 AMap，marker 会发生偏移，距离排序也会错误。实际接入 GPS、百度地图或海外地图时需要先做坐标转换，并明确转换责任在哪一层。

### Q122：高德 JS API 和高德 Web API 在项目中分别做什么？

答：

- 高德 JS API：浏览器端地图渲染、地图实例、marker、label、视野调整，代码主要在 `frontend/src/views/MapView.vue`。
- 高德 Web API：服务端/工具侧获取真实商家、地址、坐标等数据，项目 README 和 `tools/` 数据处理流程中用于商家数据爬取/初始化。

两者不要混用：浏览器展示用 JS SDK，服务端采集和后台任务用 Web Service；服务端 Key 不应打包到前端。

### Q123：高德 JS 加载失败时页面如何处理？

答：`loadAmap()` 返回 Promise，Key 缺失、script error 会 reject；`onMounted` 捕获异常后把 `mapError` 设置为错误信息，页面显示地图不可用的空状态，而不是让 Vue 组件继续访问未定义的 `window.AMap`。这种第三方 SDK 接入必须有失败态、超时、重试或降级列表页。

### Q124：为什么要在组件卸载时 `map.destroy()`？

答：地图实例会持有 DOM、事件监听器、marker 和内部资源。Vue 路由离开地图页时调用 `onBeforeUnmount` 销毁实例，避免重复进入地图页后产生旧实例、重复事件和内存泄漏。若只复用全局 script，不代表可以复用旧 map 实例。

### Q125：marker 太多时怎么优化？

答：当前项目按地区和类型过滤，并用 `setFitView` 调整视野，适合当前数据量。数据规模上升后可以：

- 使用 marker clustering 聚合密集点。
- 按地图视口 bounds 请求数据，而不是一次加载整区。
- 只渲染当前可见区域，缩放级别变化时重新取数。
- 对 `/shop/map` 做分页/限制和服务端缓存。
- 对切换分类产生的请求做取消或响应版本校验，避免旧请求覆盖新分类。
- marker 点击详情时使用对外 opaque id，后端再 decode 并做权限/存在性校验。

### Q126：为什么开发环境不直接依赖 Nginx 来访问地图页？

答：Vite 开发服务器提供 HMR，并通过 `vite.config.js` 把 `/api`、`/chat`、`/kb`、`/queue-ticket` 等请求代理到 Spring Boot；构建生产环境再输出到 Nginx 静态目录。高德 JS 是浏览器直接加载的外部资源，不需要经过后端代理；后端商家数据请求则走 Vite/Nginx 的同源代理，减少开发期跨域配置。

### Q127：高德地图接入如何测试？

答：真实 AMap SDK 不适合在普通 jsdom 单元测试中完整加载，因此测试重点分层：

- `location` store：默认地区、地区切换、中心点保存和 localStorage 恢复。
- `shopMarker`：类型颜色、marker HTML、店名 label。
- `shopQuery`：districtId、typeId、坐标和排序参数构造。
- API mock：`/region/list`、`/shop-type/list`、`/shop/map` 的成功、空数据和失败。
- 浏览器 E2E：脚本加载成功、Key 缺失、地图初始化、切换分类、点击 marker 跳详情。
- 后端集成：按地区/分类的地图查询、缓存 key 隔离、GCJ-02 坐标字段不为空。

### 23.3 高德地图的面试总结回答

> 地图页进入时先从地区接口恢复当前城市、地区和 GCJ-02 中心点，再按需动态加载高德 JS SDK。Key 使用 Vite 的 `VITE_AMAP_JS_KEY` 注入，但因为会暴露在浏览器，所以通过高德控制台域名白名单限制，而不是把它当成服务端秘密。SDK 加载成功后创建 AMap.Map，调用后端 `/shop/map` 按地区和类型获取店铺坐标，前端生成彩色 marker，点击跳店铺详情，悬停显示店名；离开页面时销毁地图实例。后端和数据库统一使用经度/纬度顺序以及 GCJ-02，避免坐标系和坐标顺序错误。高德 Web API 则用于服务端/工具侧的数据采集，使用独立的服务端凭证。

## 24. 新增专题的冲刺提醒

- 排队要讲清楚“序号生成、等待排序、用户索引、票据详情、取消、叫号、持久化”六件事。
- 不要说排队整个流程已经 Redis 原子化；当前只有单条 Redis 命令原子，完整多步流程仍有一致性风险。
- 不要把名为 `persistAsync` 的方法直接说成异步，它当前调用 `save` 是同步执行。
- 要主动提到叫号接口还需要商家/员工权限校验，不能只靠管理端页面入口隐藏。
- 高德 JS Key 会暴露在前端，安全重点是域名白名单和权限限制，不是把 `.env` 当成绝对保密。
- 高德地图中的 `[经度, 纬度]` 顺序和 GCJ-02 是最容易被追问的细节。
- 高德 JS SDK 负责浏览器渲染，高德 Web API 负责服务端数据采集，两者职责要分开。
