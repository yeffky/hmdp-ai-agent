# hmdp Agent 重构设计（grill-me 决策汇总 + 落地计划）

> 生成日期：2026-08-09
> 范围：hm-dianping `com.hmdp.agent` 子系统。目标：参考同类开源方案（LangGraph / openai-cs-agents / Ctrip-Style-AI-Travel-Assistant / WrenAI / Reflexion / smithers / ChinaTravel），把 Agent 从「文本 JSON + 自写 parser 的 Plan-Execute 混合」升级为「原生 function calling + 轻量 Plan→Execute + 确定性护栏 + 经验记忆 + 可评测」的形态。

---

## 1. 现状诊断

| 环节 | 现状 | 问题 |
|---|---|---|
| 图拓扑 | `context → planner ⇄ agent → answer`；`AgentNode` 内联 observer/judge/retry/confirm | AgentNode ~770 行；一次查询约 7 次 LLM 调用 |
| 结构化输出 | LLM 全程输出**文本 JSON**，自写 `JsonParser`(正则) 解析 | 最脆弱一环：解析失败/幻觉工具名/参数错 |
| 工具调用 | 单工具逐步；`toolService` 已能原生执行并回传 `ToolExecutionResultMessage` | 未走原生 tool-calling |
| LLM 调用 | planner + 每步(选工具+结果翻译) + judge + answer | 结果翻译每步 1 次、judge 末尾 1 次，均为额外成本 |
| 错误处理 | RETRYABLE/USER_FIXABLE/FATAL + `classifyWithLLM` 重分类 + 退避 | 每次错误多花 1 次 LLM 调用；分类靠猜 |
| Text2SQL | 三阶段（表发现→生成→限流执行）+ 安全校验 | 无执行失败自修复；无 few-shot |
| 记忆 | 滑窗 + 压缩摘要 + UserStore 画像 + 历史检索 | 无跨会话经验/偏好强化 |
| 可观测 | `agent_trace` 表 + AgentTrace 页 | 无 LLM 调用级明细/时间线回放 |
| 评测 | 仅 Service 层单元测试 | 无 Agent 级黄金用例集 |
| 代理 | `DeepSeekProxyController` 做 `role=function→tool` | LangChain4j 1.16.2 已发 `role=tool`（已反编译验证），代理是 0.31 时代遗留，直连即可 |

**字节码级证据**（LangChain4j-open-ai 1.16.2 jar）：
- `ToolMessage` 构造器 `getstatic Role.TOOL` → 工具结果消息 `role=tool`
- `ChatCompletionRequest$Builder.addToolMessage(...)` 走 `ToolMessage.from`，且含 `parallelToolCalls`
- 即：`DeepSeekProxyController` 的 `role=function→tool` 替换永不触发，**base-url 可直连 `https://api.deepseek.com`**

---

## 2. 决策汇总（grill-me 结论）

| # | 分支 | 决策 |
|---|---|---|
| 1 | 结构化输出 | **原生 function calling**；base-url 直连 DeepSeek，去代理 |
| 2 | 架构形态 | **轻量 Plan→Execute**：planner(json_object 出 plan) + agent 步原生工具调用；**砍每步结果翻译 + 末尾 judge** |
| 3 | 并行工具 | **不开**（单工具逐步；prompt 引导多条件合并进一条 Text2Sql） |
| 4 | Text2SQL | **执行失败自修复循环**（错误回喂 LLM 改 SQL，限 2-3 次）+ **few-shot 示例库** |
| 5 | 错误处理 | **LLM 自纠 + 工具错误结构化标签**（`[retryable]/[user_fixable]/[fatal]`）+ 确定性护栏；**砍 `classifyWithLLM`** |
| 6 | 记忆 | **用户偏好结构化记忆** + **Reflexion 跨会话失败教训记忆** |
| 7 | 评测 | **建黄金用例评测集**，进 CI |
| 8 | 可观测 | **补 LLM 调用级 trace**（每步 prompt/响应/token/耗时 + 会话时间线回放） |
| 9 | 落地 | **分阶段，先核心协议** |

---

## 3. 目标架构

```
context(注入:定位/画像/偏好/Reflexion经验/skill领域知识)
  → planner(原生 json_object 出结构化 plan)
  ⇄ agent(原生 tool-calling 循环)
  → answer(streaming)

agent 循环（原生）:
  messages = [system + context + 历史工具往返]
  resp = model.chat(messages, toolSpecifications=skill过滤后)
  if resp 有 tool_calls:
     逐个执行 → ToolExecutionResultMessage(错误带标签) → append → 继续
  else:
     resp.text = 最终回答 / ask_user（不调工具即问用户/直答）
```

**护栏（确定性，不靠 LLM 自觉）**：
- 重试上限 + 指数退避（防循环）
- 空结果检测（`[]`/`rows:0` → 注入「必须扩大搜索」强提示）
- 必填参数校验（缺参 → ask_user）
- 写操作确认 `WriteGuard`
- 总迭代上限（现有 `maxIterations`）

---

## 4. 分阶段落地

### Phase 1 · 核心协议（最大收益，先做）
1. `application.yaml`：`deepseek.base-url` → `https://api.deepseek.com`（直连，去代理）
2. `AgentNode` 重构为**原生 tool-calling 循环**：
   - 用 `ChatRequest + toolSpecifications`（skill 过滤后）驱动，不再让 LLM 输出文本 JSON
   - **删** `translateToolResults`（每步结果翻译）、`judgeDecision`（末尾 judge）
   - ask_user 判定：LLM 不调工具直接回文本 → ask_user / 直接答
   - 工具抛错 → 返回带 `[retryable]/[user_fixable]/[fatal]` 标签的 tool 结果
   - 保留 WriteGuard、空结果检测、重试上限+退避、总迭代上限
3. `PlannerNode`：改用 `response_format:json_object` 出结构化 plan，弃文本 JSON parser
4. 冒烟：搜店/团购/评论/订单/排队 全链路；IDEA 编译 + 单测

### Phase 2 · Text2SQL
- `Text2SqlTool`：SQL 执行异常 → 错误回喂 `SqlGenerator` 重试 2-3 次 → 仍失败抛带标签错误
- `SqlGenerator`：加 few-shot 示例库（各表典型查询模板）

### Phase 3 · 记忆
- **偏好记忆**：`UserStore` 扩展 常选地区/口味偏好/价格带，跨会话注入 `ContextNode`
- **Reflexion 教训记忆**：见 §5

### Phase 4 · 评测 + 可观测
- **黄金用例评测集**：典型 O2O 查询 × (期望工具链 + 回答关键点)，自动判定 pass/fail，进 CI
- **LLM 调用级 trace**：`agent_trace` 补每步 prompt/响应/token/耗时，AgentTrace 页会话时间线回放

---

## 5. Reflexion 跨会话失败教训记忆

参照 Reflexion (Shinn et al.)：任务失败后生成一条「口头反思/教训」存记忆，下次相似任务读取避免重蹈覆辙。

### 写路径（失败时沉淀教训）
**触发信号**（任一）：
- replan ≥ 1 且最终 insufficient
- 空结果扩大重试 ≥ 2
- 工具错误 ≥ 2 或 Text2SQL 修复循环用尽
- 用户负面反馈（"不对/不是这个/没找到"）

触发 → LLM 生成一条中文教训 → 存表：
```sql
CREATE TABLE agent_reflection (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(64),
  domain VARCHAR(32),        -- 领域标签：搜店/团购/订单/排队/...
  user_query VARCHAR(255),
  lesson VARCHAR(1000),      -- 失败原因 + 正确做法
  keywords VARCHAR(255),     -- 检索关键词（逗号分隔）
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
例：
```
domain=搜店, keywords=[火锅,拱墅区,评分]
lesson=「拱墅区问评分≥8的火锅，按 food_category='火锅' AND score>=80 查为空；
        正确做法：放宽 score>=70 或用 avg_price 区间，别用精确评分卡死」
```

### 读路径（新查询注入经验）
`ContextNode` 组装 contextBlock 时，用当前查询关键词/意图匹配 `agent_reflection`，取 top-2 相关教训，注入 `## 过往经验` 段（Planner/Agent/Answer 共享 contextBlock）。

### 成本与护栏
- 写：**仅失败运行结束时**生成（成功不触发）→ LLM 调用有界
- 读：关键词匹配检索（复用现有检索思路），廉价
- 上限：同 domain 最多 N 条（防膨胀），可手动清

### 图集成
- **读**：进 `ContextNode`（零拓扑改动）
- **写**：Controller 流式结束后异步生成（不动图拓扑）

---

## 6. 开源参照

| 方案 | 借鉴点 |
|---|---|
| LangGraph | 图编排/checkpoint/条件边（本项目已用 langgraph4j） |
| openai/openai-cs-agents-demo | 原生工具调用 + guardrails + handoff 思路 |
| Haohao-end/Ctrip-Style-AI-Travel-Assistant | 多轮状态管理、user context 注入 |
| Canner/WrenAI | Text2SQL 语义上下文层 + 治理（本项目取其 few-shot/修复思想） |
| Reflexion (Shinn et al.) | 跨会话失败教训记忆 |
| smithersai/smithers | LLM 调用级 trace / 时间线回放 |
| LAMDA-NeSy/ChinaTravel | 领域黄金用例评测集方法论 |

---

## 7. 附带事项
- ~~`DeepSeekProxyController` **保留兜底**，直连验证稳定后再删~~ → **已删除（2026-08-14，P0 清理）**：`base-url` 直连稳定，代理随 LangChain4j 原生 `role=tool` 一并移除
- `AgentNode` 内聚为 helper 类（标签、护栏、截断），**不动图拓扑**
- prompt 明确「一次只调一个工具」（配合不开并行）；多实体用 Text2Sql 合并
- 每次 Phase 落地后跑「黄金用例首版」看退化

---

## 8. 后续文档

- **[Agent 注入机制重构设计](agent-injection-refactor.md)**（2026-08-13）— filter / trim / summarize 三层组合拳：抛弃 scratchpad 数据面，统一 Agent 决策与 Answer 阶段注入，轮次对齐 + 近几轮工具结果全量保留 + 历史摘要浓缩

