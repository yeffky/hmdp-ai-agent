# Agent 注入机制重构设计 — filter / trim / summarize 三层组合拳

> 生成日期：2026-08-13
> 范围：hm-dianping `com.hmdp.agent` 子系统注入链路。目标：抛弃 scratchpad 数据面，参考 LangChain 官方消息控制组合拳（filter_messages / trim_messages / SummarizationMiddleware），将 Agent 决策与 Answer 阶段的上下文注入统一为「过滤 → 裁剪 → 摘要」三层管线，实现**轮次对齐、近几轮工具结果全量保留、历史摘要浓缩**。
> 前置文档：`docs/agent-redesign.md`（图重构 + plan+react 落地）。本文聚焦其后续的**注入/记忆层重构**。
> 状态：已实现并编译通过（2026-08-13）。

---

## 1. 现状诊断

| 环节 | 现状 | 问题 |
|---|---|---|
| 工具结果数据源 | Answer 阶段混读 scratchpad（`_last_*` 覆盖式只留最后一次）+ `observerReport`（AgentNode 从 messages 提取的字符串） | 双通道不一致；scratchpad 兜底分支 `formatToolResultsFull` 重构后已失效（所有 key 都 `_` 开头被过滤） |
| 决策注入 | `AgentNode` `Transcript.rebuildMessages(messages)` 全量重放 | HARD_MESSAGE_CAP=100（约 50 轮），token 高、无轮次边界、无过滤 |
| Answer 注入 | `streamingPrompt` 字符串塞进一条 UserMessage | 独立二次流式调用看不到 messages；工具数据需手工"封装"进 prompt，与决策阶段看到的不一致 |
| 轮次区分 | `AgentNode.lastRealUserIdx`（`:403-412`）从最后真实 user 往后扫 | 锚点逻辑散落，Answer/Controller 未复用 |
| 摘要 | `ContextCompressor` 已把旧消息 LLM 压缩成 `compressedSummary` | 行为≈SummarizationMiddleware，但摘要以字符串拼进 contextBlock，不是 message 形态注入 |

## 2. 调研结论（决策依据）

### 2.1 LangChain(JS/Py) 原生组合拳（已抓源码验证）

| API | 位置 | 语义 |
|---|---|---|
| `filter_messages` | `libs/core/langchain_core/messages/utils.py:857` | 按 type/id/name 精准过滤消息 |
| `trim_messages` | `utils.py:1133` | `max_tokens` + `strategy` + **`start_on="human"`（停在 HumanMessage 边界，即轮次对齐）** + `include_system` |
| `SummarizationMiddleware` | `libs/langchain/src/agents/middleware/summarization.ts:279` | trigger/keep 模型（`ContextSize`/`KeepSize`：`{tokens\|messages\|fraction}`，多条件数组 = 条内 AND / 条间 OR）；超限 → `determineCutoffIndex` 找 cutoff → cutoff 前消息 LLM 压成 summary message → 返回 `[summaryMessage, ...preserved]` |
| `ClearToolUsesEdit` | `contextEditing.ts:214` | 同一模型：孤儿 tool 清理（无条件）→ trigger 判断 → 旧 tool 结果替换 placeholder（保留 tool_call_id/name，excludeTools 豁免） |

### 2.2 Java 生态原生 API 现状（langchain4j 1.16.2 + langgraph4j 1.8.19，已抓源码验证）

| 能力 | langchain4j | langgraph4j |
|---|---|---|
| trim | ✅ **`TokenWindowChatMemory`**：token 窗口 + **消息不可分割**（超限整条驱逐）+ **SystemMessage 常驻** + **孤儿 tool 自动清理** + maxTokens 动态（fraction 等价）。但**无轮次边界**（等价缺 `start_on="human"`）、常驻窗口非超限触发 | ❌ 纯图编排（StateGraph + Checkpoint + Channel），无消息控制原语 |
| filter | 🟡 孤儿清理内建，无独立函数 | ❌ |
| summarize | ❌ **无原生**（需自写，即现有 ContextCompressor） | ❌ |

**集成硬约束**：langchain4j `ChatMemory` 体系（`List<ChatMessage>` + 自带 ChatMemoryStore 持久化）与 langgraph4j 的 state messages channel（`List<Map<String,String>>`）是两套消息体系，适配需转换层且会绕过 checkpoint。

### 2.3 结论

Java 生态不构成完整组合拳，且缺的恰是核心诉求（轮次对齐 + 摘要 + 超限触发）。**决策：自实现组合拳**，参照 `TokenWindowChatMemory` 的消息不可分割/孤儿清理设计 + `trim_messages(start_on="human")` 轮次对齐语义 + 复用现有 `ContextCompressor`。

## 3. 目标架构

### 3.1 注入管线（Agent 决策 & Answer 共用）

```
原始 messages ─► ① filter ─► ② trim ─► ③ 注入模型
                 │             │
                 └─ 孤儿tool清理 + 剔除注入提醒  └─ 超限：cutoff 前按"轮次"丢弃（start_on=human 语义）
```

**注入后的消息形态：**
- **Agent 决策**：`[SystemMessage(规则+领域规则+无历史contextBlock), ...rebuildMessages(trim(filter(messages)))]`
- **Answer**：`[SystemMessage(角色), SystemMessage(规则段=streamingPrompt), SystemMessage(摘要=compressedSummary), SystemMessage(无历史contextBlock), ...rebuildMessages(trim(filter(messages)))]`

### 3.2 三层职责

**① filter（≈`filter_messages`）** — `Transcript.filterMessages`
- 无条件清理孤儿 tool 消息：无对应 `assistant.toolCalls` 的 tool 消息（借鉴 `ClearToolUsesEdit.apply` 第一步）
- 剔除注入提醒 user 消息（`[操作未完成]`/`[结果为空]`）

**② trim（≈`trim_messages(start_on="human")`）** — `ContextEditor`
- `trigger` 多条件：`ContextSize[]`，条内 tokens/messages AND、数组间 OR（对齐 `summarization.ts` 的 `contextSizeSchema`）
- 超限触发后按 `keep-rounds`（保最近 N 轮）裁剪：从后往前找第 N 个真实 user 消息为 cutoff，**cutoff 之后全量保留（含 tool 轨迹，start_on=human 轮次对齐）**
- cutoff 之前：tool 结果 → placeholder（保留 toolName/toolCallId，excludeTools 豁免）；user/assistant 纯文本丢弃（由摘要承载）

**③ summarize（≈`SummarizationMiddleware`）** — 复用 `ContextCompressor`
- `ContextCompressor.compressIfNeeded` 已实现：trigger 超限 → `keepRecentTokens` 保留区 → cutoff 前 LLM 压缩成 `compressedSummary`（丢弃 tool 细节）
- 注入：摘要以 summary message 形态出现在 Answer 消息列表头部
- `fraction` 触发（模型 max tokens 比例）：langchain4j 难取模型 context 长度 → 配置用固定 token 预算替代

## 4. 组件设计

### 4.1 `ContextEditor`（`com.hmdp.agent.memory.context`）

```java
@Component
public class ContextEditor {
    private final ContextEditingConfig config;   // 构造器注入

    /** 编辑注入副本，不写回 checkpoint（存储层裁剪仍归 SlidingWindowManager） */
    public void apply(List<Map<String,String>> messages) {
        Transcript.filterMessages(messages, config);   // ① 孤儿清理 + 剔除注入提醒
        if (!shouldEdit(messages)) return;              // ② trigger 多条件
        int cutoff = Transcript.nthRealUserIdx(messages, config.getKeepRounds());
        if (cutoff <= 0) return;
        // cutoff 之前：tool 结果 → placeholder（保留 toolName/toolCallId，excludeTools 豁免）
        //             user/assistant 纯文本丢弃（由 compressedSummary 摘要承载）
    }
}
```

### 4.2 `Transcript.nthRealUserIdx`（公共轮次锚点）
从 `AgentNode.lastRealUserIdx` 提取为公共静态方法，扩展 `nthRealUserIdx(messages, n)`（从后往前第 n 个真实 user），保持前缀过滤。对齐 `trim_messages(start_on="human")`：trim 停在 user 边界。

### 4.3 配置（`agent.memory.context-editing.*`）

```yaml
agent.memory.context-editing:
  enabled: true
  trigger:
    - tokens: 20000
      messages: 40
    - tokens: 12000
      messages: 80
  keep-rounds: 3
  exclude-tools: []
  placeholder: "[已清理]"
```

## 5. 改动清单（已落地）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `graph/nodes/Transcript.java` | 新增 `filterMessages`（孤儿清理+剔除注入提醒）+ `nthRealUserIdx`（轮次锚点） |
| 2 | `graph/nodes/AgentNode.java` | 决策注入改三层管线：`rebuildMessages` 前先 `ContextEditor.apply`（副本）；system 用 `contextBlockNoHistory`；`lastRealUserIdx` 改调 `Transcript.nthRealUserIdx(messages,1)` |
| 3 | `graph/nodes/AnswerNode.java` | GENERATE 分支规则段化：`streamingPrompt` 只留纯规则段，不再拼 contextBlock/observerReport/scratchpad；messages 空时回退 observerReport（安全网）；PRESET/ERROR/STREAMING 分支不动 |
| 4 | `controller/ReactStreamController.java` | `doStreamingAnswer` 改消息注入（`AnswerInjection.build`）；断点续传锚点改为追加 UserMessage |
| 5 | `controller/ChatRagController.java` | `generateSyncAnswer` 同样改消息注入 |
| 6 | `memory/context/ContextNode.java` | 拆 `contextBlock`（含历史，Planner 用）/ `contextBlockNoHistory`（Agent/Answer 用） |
| 7 | `graph/state/StateSchema.java` + `StateKeys` + `ReActAgentState` | 新增 `contextBlockNoHistory` channel + 访问器 |
| 8 | `memory/context/AnswerInjection.java` | 新增共享注入器：`[角色, 规则段, 摘要message, 无历史contextBlock, ...rebuildMessages(trim(filter(messages)))]` |
| 9 | `memory/context/ContextEditor.java` + `ContextEditingConfig.java` | 新增（构造器注入） |
| 10 | `config/MemoryProperties.java` | 新增 `ContextEditing` 配置块 |
| 11 | `graph/GraphConfig.java` | `AgentNode` 传入 `ContextEditor` |
| 12 | 清理 | 删 `PlannerNode.formatToolResultsFull`（重构残留）；AnswerNode 不再读 `_last_*`/`formatToolResultsFull` |

**保留的控制面（不进 messages 注入）**：`_pending_tool`（决策↔执行握手）、`_domain_rules`（L2/L3 领域规则）、`_run_failed`/`_no_cards`（标记）。

**不动的部分**：SSE 协议（`answer_chunk`/`answer`/`done`）、`[[id]]` 卡片占位符剥离、`SlidingWindowManager` HARD_MESSAGE_CAP（存储层裁剪，与注入层 ContextEditor 职责分离）、checkpoint delta 存储。

## 6. 验证

- **后端编译**：`mvn compile` ✅（IDEA bundled Maven）
- **单元测试**：`ContextEditorTest`（新增，filter/trim/轮次锚点/placeholder/excludeTools）✅；前端 `npm test` 75 用例 ✅
- **既有测试**：~~3 个失败（`WriteGuardTest`/`AgentTraceServiceImplTest`/`AgentConfigTest` 引用源码不存在的方法、`AgentGraphEdgesTest` 断言旧拓扑、`SkillRegistryTest`）均为**改动前已存在**的测试与源码不同步问题，与本次重构无关~~ → **已修复（2026-08-14，P0 收尾）**：`WriteGuardTest`/`AgentConfigTest`/`AgentTraceServiceImplTest` 对齐当前 API，`AgentGraphEdgesTest` 更新为当前拓扑断言，`SkillRegistryTest` 随 skill 资源同步通过；测试模块已恢复可编译、可全绿
- **手动回归（待做，IDEA + npm run dev）**：多轮对话注入、长对话触发 trigger、`searchHistory` 兜底、写确认/ask_user/超限分支透传、`[[id]]` 卡片与打字机
