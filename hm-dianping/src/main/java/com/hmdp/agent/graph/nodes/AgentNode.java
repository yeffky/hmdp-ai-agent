package com.hmdp.agent.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.graph.NodeNames;
import com.hmdp.agent.graph.dto.JsonParser;
import com.hmdp.agent.graph.dto.ToolCallRequest;
import com.hmdp.agent.graph.prompt.PromptTemplates;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.agent.memory.context.ContextEditor;
import com.hmdp.agent.skill.Skill;
import com.hmdp.agent.skill.SkillRegistry;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent Node — ReAct 的「决策」节点（Plan-Action-Observe 分离后的决策侧）。
 *
 * <p>每次 apply() 一次原生模型调用，产出决策：
 * <ul>
 *   <li><b>有 tool call</b>：写操作 / askUserToChoose → 挂起等用户确认；普通工具 → 写入
 *       {@code _pending_tool} 交 {@link ToolNode} 执行（Action），完成后回环再次决策；</li>
 *   <li><b>无 tool call</b>：组装已收集信息 + 店铺卡片引用表，直接交 Answer 流式生成——
 *       充分性判断已由 skill SOP（「结果不匹配就重查」）与 ReAct 观察自评下沉到决策循环自身。</li>
 * </ul>
 * 写确认 / 选项选择暂停后的恢复也在此节点：恢复后重新决策。
 */
public class AgentNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(AgentNode.class);

    /** scratchpad 键：决策→执行的握手（待执行工具 JSON：{tool,args,id}） */
    public static final String SP_PENDING_TOOL = "_pending_tool";

    /** scratchpad 键：本次运行是否失败（供 Reflexion 写路径触发） */
    public static final String SP_RUN_FAILED = "_run_failed";

    /** scratchpad 键：工具尝试超限/未收敛，本次不产生最终卡片（Answer 不展示无关卡片） */
    public static final String SP_NO_CARDS = "_no_cards";

    /** scratchpad 键：showCards 声明的店铺卡片 id 列表（对外混淆短串），Controller 流式回答时按其发 cards 事件 */
    public static final String SP_CARD_IDS = "_card_ids";

    private final ChatModel model;
    private final LC4jToolService toolService;
    private final int maxRetries;
    private final SkillRegistry skillRegistry;
    private final ToolExecutor toolExecutor;
    private final ContextEditor contextEditor;
    private final com.hmdp.utils.IdObfuscator idObfuscator;

    public AgentNode(ChatModel model, LC4jToolService toolService, int maxRetries,
                     SkillRegistry skillRegistry, ToolExecutor toolExecutor, ContextEditor contextEditor,
                     com.hmdp.utils.IdObfuscator idObfuscator) {
        this.model = model;
        this.toolService = toolService;
        this.maxRetries = maxRetries;
        this.skillRegistry = skillRegistry;
        this.toolExecutor = toolExecutor;
        this.contextEditor = contextEditor;
        this.idObfuscator = idObfuscator;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        Map<String, Object> scratchpad = state.scratchpad();
        // 标准消息通道（append 式）：user/assistant(tool_calls)/tool 结果都追加到 messages，永不丢、可回放
        List<Map<String, String>> messages = state.messages();

        // ============================================================
        // 1. 写操作确认恢复（WriteGuard：程序级强制确认）
        // ============================================================
        String pendingWrite = state.pendingWrite();
        if (pendingWrite != null && !pendingWrite.isEmpty()) {
            ToolCallRequest pendingCall = JsonParser.parseToolCall(pendingWrite);
            if (pendingCall != null && pendingCall.getTool() != null
                    && WriteGuard.isWriteOperation(pendingCall.getTool())) {
                String choice = userResponse(state);
                WriteIntent intent = resolveWriteIntent(pendingCall.getTool(), choice, state.confirmationPrompt());
                if (intent == WriteIntent.CANCEL) {
                    // 用户拒绝（取消/不要）：用户响应写 messages，回 agent 收尾（LLM 理解取消，无工具调用）
                    log.info("Agent: write {} response '{}' (intent=CANCEL), letting agent conclude",
                            pendingCall.getTool(), choice);
                    return Map.of("messages", List.of(ReActAgentState.userMsg(choice)),
                            StateKeys.PENDING_WRITE, "",
                            StateKeys.PENDING_CONFIRMATION, false,
                            StateKeys.USER_CHOICE, "",
                            StateKeys.NEXT_NODE, NodeNames.AGENT);
                }
                if (intent == WriteIntent.NEW) {
                    // 用户开启全新话题（与挂起写操作无关，如「推荐快餐店」）：旧 plan 已失效，
                    // 用户响应写 messages，回 planner 重新规划（plan+react 的正确做法，避免用旧计划执行）
                    log.info("Agent: write {} response '{}' (intent=NEW), re-planning",
                            pendingCall.getTool(), choice);
                    return Map.of("messages", List.of(ReActAgentState.userMsg(choice)),
                            StateKeys.PENDING_WRITE, "",
                            StateKeys.PENDING_CONFIRMATION, false,
                            StateKeys.USER_CHOICE, "",
                            StateKeys.USER_QUERY, choice,
                            StateKeys.NEXT_NODE, NodeNames.PLANNER);
                }
                if (intent == WriteIntent.MODIFY) {
                    // MODIFY（调整参数，如「改成4人」）：用户响应写 messages，回 planner 重新规划。
                    // 渐进式披露下 agent 决策只暴露旧 skill 白名单工具，调整参数可能需新工具；
                    // replan 让 Planner 基于新意图重新选 skill（plan+react 正确做法）。
                    log.info("Agent: write {} response '{}' (intent=MODIFY), re-planning",
                            pendingCall.getTool(), choice);
                    return Map.of("messages", List.of(ReActAgentState.userMsg(choice)),
                            StateKeys.PENDING_WRITE, "",
                            StateKeys.PENDING_CONFIRMATION, false,
                            StateKeys.USER_CHOICE, "",
                            StateKeys.USER_QUERY, choice,
                            StateKeys.NEXT_NODE, NodeNames.PLANNER);
                }
                log.info("Agent: user confirmed write operation {}", pendingCall.getTool());
                // 执行被挂起的写调用 → 补写 assistant tool_call + tool 结果到 messages（完整执行记录）
                String toolName = pendingCall.getTool();
                String argsStr = JsonParser.toJson(pendingCall.getArgs());
                String resumeId = "write-call";
                String callJson = JSONUtil.toJsonStr(List.of(Map.of(
                        "id", resumeId, "name", toolName, "arguments", argsStr)));
                ToolExecutionResultMessage result = toolExecutor.execute(state, toolName, argsStr, resumeId);
                boolean err = Boolean.TRUE.equals(result.isError());
                // 写操作已执行 → 回 agent 继续决策（支持复合意图如「取消后重新取号」继续下一步；
                // 单意图则 agent 看到执行结果后直接收尾）。防重复执行：messages 已有执行记录，
                // agent 看到结果不会重复同一写操作；若有未完成计划步骤（plan 下一步）则继续。
                String resultText = result.text() != null ? result.text() : "操作已完成。";
                String evidence = evidenceWithCards(state, state.messages());
                String collected = "执行结果：" + resultText
                        + (evidence.isBlank() ? "" : "\n\n收集信息（之前上下文）：\n" + evidence);
                Map<String, Object> counters = state.counters();
                counters.put(StateKeys.TOOL_CALL_COUNT, state.toolCallCount() + 1);
                counters.put(StateKeys.EMPTY_RESULT_RETRIES, 0);
                return Map.of("messages", List.of(
                                ReActAgentState.assistantToolMsg(callJson),
                                err ? ReActAgentState.toolErrorMsg(toolName, resumeId, resultText)
                                        : ReActAgentState.toolMsg(toolName, resumeId, resultText)),
                        StateKeys.PENDING_WRITE, "",
                        StateKeys.PENDING_CONFIRMATION, false,
                        StateKeys.USER_CHOICE, "",
                        StateKeys.CONFIRMATION_PROMPT, "",
                        StateKeys.COUNTERS, counters,
                        StateKeys.ROUND_EVIDENCE, collected,
                        StateKeys.NEXT_NODE, NodeNames.AGENT);
            }
        }

        // ============================================================
        // 2. 选项选择恢复（askUserToChoose 挂起后用户点选按钮 / 自由输入）
        //    意图分类：选择/补充 → 回 agent 继续执行；新话题/新需求 → 回 planner 重新规划。
        //    对齐写确认分支（resolveWriteIntent）的 MODIFY/NEW 处理：用户意图变化时，
        //    旧 skill 白名单可能已不足以支撑新任务，必须 replan 重新选技能（plan+react 正确做法）。
        // ============================================================
        String pendingOptions = state.pendingOptions();
        if (pendingOptions != null && !pendingOptions.isEmpty()) {
            String choice = userResponse(state);
            String choiceText = choice != null && !choice.isBlank() ? choice : "（未选择）";
            OptionIntent intent = resolveOptionIntent(choice, pendingOptions, model);
            if (intent == OptionIntent.NEW) {
                // 用户开启新话题/新需求（与挂起的选项无关）：旧 plan 已失效，更新 userQuery 回 planner 重新规划
                log.info("Agent: option response '{}' (intent=NEW), re-planning", choiceText);
                return Map.of("messages", List.of(ReActAgentState.userMsg("用户开启了新话题：" + choiceText)),
                        StateKeys.PENDING_OPTIONS, "",
                        StateKeys.PENDING_CONFIRMATION, false,
                        StateKeys.USER_CHOICE, "",
                        StateKeys.USER_QUERY, choiceText,
                        StateKeys.NEXT_NODE, NodeNames.PLANNER);
            }
            // SELECT（选择某选项 / 补充信息 / 澄清）：作为 user 消息追加回 agent，让 LLM 基于对话继续执行
            log.info("Agent: option response '{}' (intent=SELECT), letting agent continue", choiceText);
            return Map.of("messages", List.of(ReActAgentState.userMsg("用户选择：" + choiceText)),
                    StateKeys.PENDING_OPTIONS, "",
                    StateKeys.PENDING_CONFIRMATION, false,
                    StateKeys.USER_CHOICE, "",
                    StateKeys.USER_QUERY, choiceText,
                    StateKeys.NEXT_NODE, NodeNames.AGENT);
        }

        // ============================================================
        // 3. 原生决策：一次模型调用
        // ============================================================
        int toolCallsSoFar = state.toolCallCount();
        if (toolCallsSoFar >= maxRetries * 2) {
            // 工具尝试超限：不再硬编码失败提示，改用 LLM 基于已收集的工具结果总结回答；
            // 同时标记「本次无最终卡片」，Answer 不展示探索过程中的无关店铺卡片
            log.warn("Agent: tool call limit reached ({}), summarizing from collected results", toolCallsSoFar);
            scratchpad.put(SP_RUN_FAILED, true);
            scratchpad.put(SP_NO_CARDS, true);
            String summaryPrompt = buildExhaustedSummaryPrompt(state, messages);
            return Map.of(StateKeys.SCRATCHPAD, scratchpad,
                    StateKeys.STREAMING_PROMPT, summaryPrompt,
                    StateKeys.FINAL_ANSWER, StateKeys.SENTINEL_STREAMING,
                    StateKeys.NEXT_NODE, NodeNames.ANSWER);
        }

        List<ChatMessage> msgs = new ArrayList<>();
        // 全量 contextBlock：含「对话历史」段（最近几轮 Q&A 文本，跳过 tool 轨迹），
        // 帮 Agent 理解本轮指代（如「刚才那家」）；历史 tool 执行细节不进 system，也不进 messages（本轮封装）
        String context = state.contextBlock();
        // 领域规则（L2/L3：匹配 skill 的 SOP + references）——仅执行阶段注入，Planner 阶段不暴露
        Object dr = scratchpad.get(StateKeys.SP_DOMAIN_RULES);
        String domainRules = dr != null ? dr.toString().trim() : "";
        // 系统消息 = 上下文 + 领域规则 + 逐步 ReAct 指引（Plan/当前步骤/上一步观察，每次迭代新鲜刷新）
        String sysContent = (context != null && !context.isBlank() ? context : PromptTemplates.EXECUTOR_SYSTEM)
                + (domainRules.isEmpty() ? "" : "\n\n" + domainRules)
                + "\n\n" + buildStepGuidance(state);
        msgs.add(SystemMessage.from(sysContent));
        // 本轮显式封装：只注入最后一个真实 user 之后的轨迹（本轮 user + 工具调用/结果）。
        // 历史轮由 Planner 消化为 plan + 摘要（compressedSummary），Agent 专注执行 plan——
        // 不重新理解对话上下文（那是 Planner 的职责），也避免历史 tool 结果跨轮污染。
        if (messages.isEmpty()) {
            // 兜底：messages 意外为空（如某 resume 路径绕过 ContextNode）时，以当前 user 请求为第一条消息
            msgs.add(UserMessage.from(state.userQuery() != null ? state.userQuery() : ""));
        } else {
            List<Map<String, String>> injected = new ArrayList<>(messages);
            int lastUser = Transcript.nthRealUserIdx(injected, 1);
            if (lastUser >= 0) {
                injected = new ArrayList<>(injected.subList(lastUser, injected.size()));
            }
            // 副本上压缩店铺工具结果（去 image 长 URL 等）——每轮决策都带上一轮 tool 结果，
            // 精简可省大量 token；不碰原始 messages（前端卡片数据源是原始内容）
            compressToolMessages(injected);
            contextEditor.apply(injected);
            msgs.addAll(Transcript.rebuildMessages(injected));
        }

        // 技能 → 工具白名单：优先 Planner 语义选中的 skills，为空/无效则回退关键词匹配
        List<Skill> matchedSkills = skillRegistry.matchSkills(state.userQuery());
        Set<String> allowedTools = skillRegistry.toolNames(matchedSkills);
        java.util.List<String> selectedSkills = state.selectedSkills();
        if (selectedSkills != null && !selectedSkills.isEmpty()) {
            Set<String> tools = new LinkedHashSet<>();
            boolean foundSkill = false;
            for (String name : selectedSkills) {
                Skill sk = skillRegistry.get(name);
                if (sk != null) { tools.addAll(sk.tools()); foundSkill = true; }
            }
            if (foundSkill) {
                tools.add("askUserToChoose"); // 通用交互工具始终可用
                allowedTools = tools;
            }
        }
        final Set<String> effectiveTools = allowedTools; // 供 lambda 引用（需 effectively final）
        // Text2SQL 前置条件：确定性 skill 存在时，query 不做初次选择——
        // 空结果重试 ≥1 次或已调用 ≥2 次工具后才放开，强制先走确定性工具
        boolean hasDeterministic = effectiveTools.stream()
                .anyMatch(t -> !"query".equals(t) && !"askUserToChoose".equals(t));
        boolean allowText2Sql = !hasDeterministic
                || state.emptyResultRetries() >= 1
                || toolCallsSoFar >= 2;
        List<ToolSpecification> specs = toolService.toolSpecifications().stream()
                .filter(ts -> effectiveTools.contains(ts.name()))
                .filter(ts -> allowText2Sql || !"query".equals(ts.name()))
                .collect(Collectors.toList());

        // 注入日志：验证决策阶段喂给模型的真实消息组成（排查"为何不调工具"）
        long toolResults = msgs.stream().filter(m -> m instanceof ToolExecutionResultMessage).count();
        long userMsgs = msgs.stream().filter(m -> m instanceof UserMessage).count();
        log.debug("Agent decision messages: {}", summarizeForLog(msgs));
        log.info("Agent: 注入 {} 条消息（user {} / tool结果 {}），system={} 字符",
                msgs.size(), userMsgs, toolResults, sysContent.length());

        ChatResponse resp;
        try {
            resp = model.chat(ChatRequest.builder()
                    .messages(msgs)
                    .toolSpecifications(specs)
                    .build());
        } catch (Exception e) {
            log.error("Agent native LLM call failed", e);
            return Map.of(StateKeys.FINAL_ANSWER, "系统开小差了，请稍后再试。",
                    StateKeys.NEXT_NODE, NodeNames.ANSWER);
        }
        AiMessage ai = resp.aiMessage();

        if (!ai.hasToolExecutionRequests()) {
            // ============================================================
            // 4a. 无工具调用 → 收尾：组装证据 + 卡片引用表，走 Answer 流式生成
            // ============================================================
            // 确定性护栏：用户明确要「取号」但 agent 没调用 takeQueueNumber 就准备回答
            // → 注入一次提醒让它继续调写工具（系统会自动暂停请用户确认）。
            // 仅针对取号意图；取消排队/排队查询不在内（无记录时如实告知是对的）。
            // 转录里已有提醒则不再重复（天然防死循环）。
            if (wantsTakeQueue(state.userQuery())
                    && !writeAttempted(messages)
                    && !alreadyNudged(messages)) {
                log.info("Agent: 用户要取号但未调用 takeQueueNumber，注入提醒继续");
                return Map.of("messages", List.of(ReActAgentState.userMsg(
                                "[操作未完成] 用户要求取号，但你还没有调用 takeQueueNumber。请先 searchShop/searchShops 找到店铺，然后直接调用 takeQueueNumber（系统会自动暂停请用户确认）。有多家店需选择时用 askUserToChoose 让用户选。禁止用文字询问代替调用写工具。")),
                        StateKeys.NEXT_NODE, NodeNames.AGENT);
            }
            String answerText = ai.text() != null ? ai.text().trim() : "好的，已收到。";
            // 能力不足 replan：当前 skill 工具不足以完成任务（如 Planner 漏选依赖：只有 content 无 shop）时，
            // agent 输出【需要重新规划】信号 → 回 planner 重新规划补技能，不硬编结果、不越权访问
            if (answerText.contains("【需要重新规划】")) {
                String reason = answerText.replace("【需要重新规划】", "").trim();
                log.info("Agent: 当前 skill 能力不足，触发 replan: {}", reason);
                return Map.of(StateKeys.NEXT_NODE, NodeNames.PLANNER,
                        StateKeys.ROUND_EVIDENCE, "重新规划需求：" + reason);
            }
            log.info("Agent: draft answer ({} chars), routing to answer", answerText.length(),
                    answerText.length() > 120 ? answerText.substring(0, 120) + "..." : answerText);
            // 组装收集信息（证据 + 店铺卡片引用表），Answer GENERATE 以此为流式生成依据；
            // 不再经 Observer 做 LLM 充分性判断——已由 skill SOP（结果不匹配就重查）与 ReAct 观察自评保障
            if (state.emptyResultRetries() >= 2) {
                scratchpad.put(SP_RUN_FAILED, true);
            }
            String collected = buildEvidence(state, messages);
            String cardRefs = buildCardRefTable(state, messages);
            if (!cardRefs.isBlank()) {
                collected += "\n\n## 店铺卡片引用（推荐这些店铺时，把它们 id 放入 showCards 工具的 shopIds 参数）\n" + cardRefs;
            }
            return Map.of(StateKeys.SCRATCHPAD, scratchpad,
                    StateKeys.ROUND_EVIDENCE, collected,
                    StateKeys.NEXT_NODE, NodeNames.ANSWER);
        }

        // ============================================================
        // 4b. 有工具调用 → 每图迭代只执行第一个（强制单工具，与「不开并行」一致，
        //     也保证 assistant tool_calls 与 tool 结果一一对应，协议一致）
        // ============================================================
        ToolExecutionRequest first = ai.toolExecutionRequests().get(0);
        String toolName = first.name();
        String argsStr = first.arguments();
        String callId = first.id() != null ? first.id() : "call_" + toolCallsSoFar;

        // 写操作：先挂起确认，不写入转录（pendingWrite 记录，恢复时一并补写 assistant+result）。
        // HITL 原地暂停：不跳 answer 生成确认文本——AnswerNode 对 PENDING_WRITE 直接 END，
        // 前端渲染确认卡片（店名+人数）+ 确认/取消按钮 + 自由输入框；恢复回 agent 原地执行。
        if (WriteGuard.isWriteOperation(toolName)) {
            String encoded = encodeCallFrom(toolName, argsStr);
            log.info("Agent: write tool {} requires confirmation, suspending", toolName);
            // 挂起不写 messages：tool_calls 未执行，写了会成孤儿（rebuild 后 assistant(tool_calls)
            // 无对应 tool 结果 → API 400）。确认后 CONFIRM 分支补写 assistant+tool 结果；
            // 取消/调整/新话题只追加 userMsg，无孤儿。
            return Map.of(
                    StateKeys.PENDING_WRITE, encoded,
                    StateKeys.PENDING_CONFIRMATION, true,
                    StateKeys.CONFIRMATION_PROMPT, "",
                    StateKeys.NEXT_NODE, NodeNames.ANSWER);
        }

        // askUserToChoose：让用户从多个选项中选择 → 挂起（前端渲染选项按钮 + 自由输入框，点选/输入后恢复）。
        // 原地暂停（不跳 answer）：推荐介绍用 agent 决策文本（ai.text()，含每家店亮点）作为 hint 发给前端。
        if ("askUserToChoose".equals(toolName)) {
            String[] parsed = parseAskOptions(argsStr);
            if (parsed != null) {
                String promptText = parsed[0];
                String optionsJson = parsed[1];
                String agentText = ai.text() != null && !ai.text().isBlank() ? ai.text().trim() : "";
                String hint = (agentText.isEmpty() || agentText.equals(promptText))
                        ? promptText : agentText + "\n\n" + promptText;
                log.info("Agent: askUserToChoose suspending: {}", promptText);
                // 挂起不写 messages（tool_calls 未执行，避免孤儿）：恢复时追加 userMsg 即可
                return Map.of(
                        StateKeys.PENDING_OPTIONS, optionsJson,
                        StateKeys.PENDING_CONFIRMATION, true,
                        StateKeys.CONFIRMATION_PROMPT, hint,
                        StateKeys.NEXT_NODE, NodeNames.ANSWER);
            }
        }

        // showCards：声明式 UI 工具（对齐 OpenAI/Anthropic「工具调用驱动 UI」范式）——LLM 声明要展示的店铺卡片。
        // 声明 id 记入 scratchpad；走 TOOLS 正常执行（ShowCardsTool 返回"已记录"，tool 结果写回 messages），
        // LLM 下一轮看到执行结果，不会重复声明（避免无反馈导致的死循环）。
        if ("showCards".equals(toolName)) {
            List<String> ids = parseShowCardsIds(argsStr);
            if (ids != null && !ids.isEmpty()) {
                log.info("Agent: showCards 声明 {} 张店铺卡片: {}", ids.size(), ids);
                scratchpad.put(SP_CARD_IDS, ids);
            } else {
                log.warn("Agent: showCards 参数无效/为空: {}", argsStr);
            }
            String callJson = JSONUtil.toJsonStr(List.of(Map.of(
                    "id", callId, "name", toolName, "arguments", argsStr)));
            scratchpad.put(SP_PENDING_TOOL, JSONUtil.toJsonStr(Map.of(
                    "tool", toolName, "args", argsStr, "id", callId)));
            return Map.of(StateKeys.MESSAGES, List.of(ReActAgentState.assistantToolMsg(callJson)),
                    StateKeys.SCRATCHPAD, scratchpad, StateKeys.NEXT_NODE, NodeNames.TOOLS);
        }

        // 普通工具：assistant tool_call 追加到 messages（标准消息通道），待执行信息（_pending_tool）交 ToolNode
        String callJson = JSONUtil.toJsonStr(List.of(Map.of(
                "id", callId, "name", toolName, "arguments", argsStr)));
        scratchpad.put(SP_PENDING_TOOL, JSONUtil.toJsonStr(Map.of(
                "tool", toolName, "args", argsStr, "id", callId)));
        return Map.of(StateKeys.MESSAGES, List.of(ReActAgentState.assistantToolMsg(callJson)),
                StateKeys.SCRATCHPAD, scratchpad, StateKeys.NEXT_NODE, NodeNames.TOOLS);
    }

    // ============================================================
    // 决策辅助
    // ============================================================

    /**
     * 工具尝试超限时的收尾 prompt：让 LLM 基于已收集的工具结果总结回答，
     * 说明查询范围与结果、给出替代建议，而不是硬编码失败提示。
     */
    private String buildExhaustedSummaryPrompt(ReActAgentState state, List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        String ctx = state.contextBlock();
        if (ctx != null && !ctx.isBlank()) sb.append(ctx).append("\n");
        sb.append("## 用户问题\n").append(state.userQuery()).append("\n\n");
        sb.append("## 已执行查询（工具调用及结果）\n");
        String evidence = evidenceFromMessages(messages);
        sb.append(evidence.isBlank() ? "（无有效结果）" : evidence).append("\n\n");
        sb.append("## 任务\n多次尝试后仍未完整获取用户所需数据。请基于以上已收集的信息坦诚回答用户：\n");
        sb.append("- 说明查询了哪些范围/条件、结果如何（如「搜索了足浴类目，但没有匹配店铺」）\n");
        sb.append("- 如有最接近的替代（如按摩/SPA 店）给出建议；确实没有就如实说明并给下一步建议\n");
        sb.append("- 口语化中文，禁止暴露内部 ID、SQL、表名、工具名");
        return sb.toString();
    }

    /** 决策消息列表的紧凑日志摘要：role + 截断内容（避免全量消息/工具结果刷日志）。 */
    private static String summarizeForLog(List<ChatMessage> msgs) {
        StringBuilder sb = new StringBuilder("[");
        for (ChatMessage m : msgs) {
            String content;
            if (m instanceof UserMessage u) content = u.singleText();
            else if (m instanceof SystemMessage s) content = s.text();
            else if (m instanceof ToolExecutionResultMessage t) content = t.text();
            else if (m instanceof AiMessage a) content = a.text() != null ? a.text() : "(tool_calls)";
            else content = m.toString();
            sb.append(m.type()).append(":").append(truncate(content, 60)).append(" | ");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    /** 从标准消息通道取全部工具调用及结果摘要（供超限总结使用）。 */
    private String evidenceFromMessages(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : messages) {
            if ("tool".equals(m.get("role"))) {
                String name = m.getOrDefault("toolName", "");
                String content = m.getOrDefault("content", "");
                if (content.length() > 300) content = content.substring(0, 300) + "...";
                sb.append("- ").append(name).append(" → ").append(content).append("\n");
            }
        }
        return sb.toString();
    }

    /** 收集信息：从标准 messages 取本轮（最后一个真实用户消息之后）的 tool 结果——追加式、无跨轮污染；
     *  nudge/空结果注入的提醒消息（带 [前缀]）不计为真实用户，避免边界错乱；无则回退最近 tool / SP_LAST_RESULT_FULL。 */
    private String buildEvidence(ReActAgentState state, List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        int lastUserIdx = Transcript.nthRealUserIdx(messages, 1);
        for (int i = lastUserIdx + 1; i < messages.size(); i++) {
            if ("tool".equals(messages.get(i).get("role"))) {
                appendToolEvidence(sb, messages.get(i));
            }
        }
        if (sb.length() == 0) { // 回退：最近一次 tool 结果（挂起响应后无新工具，取挂起前的）
            for (int i = messages.size() - 1; i >= 0; i--) {
                if ("tool".equals(messages.get(i).get("role"))) { appendToolEvidence(sb, messages.get(i)); break; }
            }
        }
        if (sb.length() == 0) { // 兜底：旧 checkpoint 无 messages tool 时用 SP_LAST_RESULT_FULL
            Object lastFull = state.scratchpad().get(StateKeys.SP_LAST_RESULT_FULL);
            if (lastFull != null && !lastFull.toString().isBlank()) {
                Object lastTool = state.scratchpad().get(StateKeys.SP_LAST_TOOL);
                return "- " + (lastTool != null ? lastTool : "工具") + " → " + lastFull;
            }
        }
        return sb.length() == 0 ? "（无）" : sb.toString();
    }

    private void appendToolEvidence(StringBuilder sb, Map<String, String> m) {
        String content = m.getOrDefault("content", "");
        String compacted = com.hmdp.utils.ShopResultIdObfuscator.compact(
                m.getOrDefault("toolName", ""), content, idObfuscator);
        sb.append("- ").append(m.getOrDefault("toolName", "")).append(" → ").append(compacted).append("\n");
    }

    /** 压缩注入副本上店铺工具结果的 content（数字 id 混淆 + 精简字段），原始 messages 不动。 */
    private void compressToolMessages(List<Map<String, String>> messages) {
        if (messages == null || idObfuscator == null) return;
        for (Map<String, String> m : messages) {
            if ("tool".equals(m.get("role"))) {
                String toolName = m.getOrDefault("toolName", "");
                String compacted = com.hmdp.utils.ShopResultIdObfuscator.compact(
                        toolName, m.getOrDefault("content", ""), idObfuscator);
                m.put("content", compacted);
            }
        }
    }

    /** 本轮店铺「店名 → id」引用表：从标准 messages 的 tool 消息提取（追加式）；无则回退最近店铺 tool。 */
    private String buildCardRefTable(ReActAgentState state, List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        int lastUserIdx = Transcript.nthRealUserIdx(messages, 1);
        for (int i = lastUserIdx + 1; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            if ("tool".equals(m.get("role")) && isShopToolName(m.getOrDefault("toolName", ""))) {
                String content = m.getOrDefault("content", "");
                if (!content.isBlank()) appendCardRefs(sb, m.getOrDefault("toolName", ""), content);
            }
        }
        if (sb.length() == 0) { // 回退：最近一次店铺 tool 结果
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, String> m = messages.get(i);
                if ("tool".equals(m.get("role")) && isShopToolName(m.getOrDefault("toolName", ""))) {
                    String content = m.getOrDefault("content", "");
                    if (!content.isBlank()) { appendCardRefs(sb, m.getOrDefault("toolName", ""), content); break; }
                }
            }
        }
        return sb.toString();
    }

    /** 解析店铺工具结果 JSON，追加「店名 → id」引用。 */
    private void appendCardRefs(StringBuilder sb, String toolName, String content) {
        try {
            JSONArray arr;
            if ("geoSearch".equals(toolName)) {
                // geoSearch 结构为 {"shops":[...]}
                JSONObject obj = JSONUtil.parseObj(content);
                Object shops = obj.get("shops");
                arr = shops instanceof JSONArray a ? a : null;
            } else {
                arr = JSONUtil.parseArray(content);
            }
            if (arr == null) return;
            for (Object o : arr) {
                if (!(o instanceof JSONObject jo)) continue;
                // id 为对外混淆 ID（Sqids 字符串），直接引用即可；LLM 回传时工具内部自动还原
                String id = jo.getStr("id");
                String shopName = jo.getStr("name", "");
                if (id != null && !id.isBlank() && !shopName.isBlank()) {
                    sb.append("- ").append(shopName).append(" → id: ").append(id).append("\n");
                }
            }
        } catch (Exception ignored) {
            // 解析失败忽略（不阻塞）
        }
    }

    /** 本轮工具结果（证据 + 卡片引用表），供写操作执行后保留上下文 / 无工具调用收尾。 */
    private String evidenceWithCards(ReActAgentState state, List<Map<String, String>> messages) {
        String evidence = buildEvidence(state, messages);
        String cardRefs = buildCardRefTable(state, messages);
        if (cardRefs.isBlank()) return evidence;
        return evidence + "\n\n## 店铺卡片引用\n" + cardRefs;
    }

    private static boolean isShopToolName(String name) {
        return "searchShops".equals(name) || "recommendShops".equals(name)
                || "searchShop".equals(name) || "geoSearch".equals(name);
    }

    /**
     * 逐步 ReAct 指引（放入系统消息，每次迭代刷新）：
     * 展示执行计划 + 当前步骤 + 上一步观察，驱动「每步 plan→Act→Observe」。
     */
    private String buildStepGuidance(ReActAgentState state) {
        StringBuilder sb = new StringBuilder();
        List<String> steps = parsePlan(state.plan());
        if (!steps.isEmpty()) {
            sb.append("## 执行计划（按顺序逐步推进：一个步骤可能需要多次工具调用，全部完成后进入下一步）\n");
            for (int i = 0; i < steps.size(); i++) {
                sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
            sb.append("\n从第 1 步开始逐步执行；完成当前步骤所需的全部工具调用（含前置查询）后再进入下一步，未完成不要跳过。\n");
        }
        Object lastResult = state.scratchpad().get(StateKeys.SP_LAST_RESULT);
        if (lastResult != null) {
            String r = lastResult.toString();
            sb.append("\n上一步观察：").append(r.length() > 400 ? r.substring(0, 400) + "..." : r).append("\n");
        }
        sb.append("\n规则：逐步执行；一次调用一个工具；信息足够或无法继续时直接输出最终回答（不要再调用工具）。");
        sb.append("\n观察自评：每次看到工具结果，判断它服务于哪个计划步骤——当前步骤的前置查询与核心调用都完成后，再进入下一步；不满足用户核心诉求就继续查，禁止把不匹配的结果当最终回答。");
        sb.append("\n卡片声明：回答涉及店铺（推荐/查询/列举/比较/单店详情）时，在输出最终回答前调用 showCards 声明要展示的店铺卡片（shopIds=工具结果里这些店的 id 字段值）；回答文本只写店名+亮点，不要输出任何标记或 id（卡片由系统渲染）。");
        sb.append("\n能力边界：若当前可用的工具不足以完成用户请求（如需要查询店铺但没有搜索/推荐店铺的工具，且消息上下文中也没有目标店铺的 shopId），**不要编造结果、不要硬用不匹配的工具**——直接输出一行「【需要重新规划】原因：…」，系统会据此重新规划以补充所需技能。");
        return sb.toString();
    }

    /** 解析计划 JSON 数组为步骤列表。 */
    private List<String> parsePlan(String planJson) {
        if (planJson == null || planJson.isBlank()) return new ArrayList<>();
        try {
            return JSONUtil.parseArray(planJson).toList(String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 解析 showCards 参数：{"shopIds":[...]} → id 字符串列表；无效/空返回 null。 */
    private List<String> parseShowCardsIds(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) return null;
        try {
            JSONObject jo = JSONUtil.parseObj(argsStr);
            JSONArray arr = jo.getJSONArray("shopIds");
            if (arr == null) return null;
            List<String> ids = new ArrayList<>();
            for (Object o : arr) {
                String v = String.valueOf(o).trim();
                if (!v.isBlank() && !"null".equals(v)) ids.add(v);
            }
            return ids.isEmpty() ? null : ids;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写操作确认挂起时，用 LLM 判断用户输入的意图（确认/取消/修改参数/开启新对话）——语义判断不写死正则。
     *
     * @param confirmPrompt 确认提示原文（如「确认取消您在XX的排队吗？」），帮助消歧：
     *                      用户回复「取消」是拒绝（不执行），而非确认「取消排队」。
     * @return CONFIRM / CANCEL / MODIFY / NEW；LLM 失败默认 CANCEL（保守不执行）
     */
    private WriteIntent resolveWriteIntent(String toolName, String choice, String confirmPrompt) {
        try {
            String confirmText = (confirmPrompt != null && !confirmPrompt.isBlank())
                    ? confirmPrompt : "确认执行「" + toolName + "」操作？";
            String prompt = "系统向用户展示了如下确认提示：\n「" + confirmText + "」\n\n"
                    + "用户对此回复了：" + choice + "\n"
                    + "请判断用户是否**同意执行确认提示中的操作**，只输出 JSON："
                    + "{\"intent\":\"confirm\"|\"cancel\"|\"modify\"|\"new\",\"reason\":\"一句话\"}\n"
                    + "confirm=明确同意执行（如 确认/好的/可以/执行/去吧/取消吧/那就取消/确认取消——带执行语气的同意）；\n"
                    + "cancel=拒绝或反悔（如 取消/不要/不了/算了/不用了/不/别——**即使确认提示本身是『确认取消X』，用户单独回复『取消』也代表不同意执行取消，属拒绝**）；\n"
                    + "modify=调整参数或提出新要求（如 改成4人/换一家店/不是这家），此时**不应执行原操作**；\n"
                    + "new=**开启了全新话题**，与确认提示中的操作完全无关（如用户说「帮我推荐快餐店」「查我的订单」），此时**不执行原操作、开启新一轮对话**。\n"
                    + "无法明确判断时取 modify（保守不执行，绝不在不确定时执行写操作）。";
            ChatResponse resp = model.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from("你是写操作意图识别器，只输出 JSON。"),
                            UserMessage.from(prompt)))
                    .responseFormat(ResponseFormat.JSON)
                    .build());
            String text = resp.aiMessage().text();
            cn.hutool.json.JSONObject jo = JSONUtil.parseObj(text);
            String intent = jo.getStr("intent", "modify");
            if ("cancel".equals(intent)) return WriteIntent.CANCEL;
            if ("confirm".equals(intent)) return WriteIntent.CONFIRM;
            if ("new".equals(intent)) return WriteIntent.NEW;
            return WriteIntent.MODIFY;
        } catch (Exception e) {
            log.warn("resolveWriteIntent failed, default CANCEL: {}", e.getMessage());
            return WriteIntent.CANCEL;
        }
    }

    /** 写操作确认挂起时用户的意图。 */
    private enum WriteIntent { CONFIRM, CANCEL, MODIFY, NEW }

    /**
     * 选项选择挂起恢复时的意图（可测试：包级可见）。
     */
    enum OptionIntent { SELECT, NEW }

    /**
     * 选项挂起恢复的意图判断：
     * <ol>
     *   <li><b>确定性优先</b>：choice 精确命中某选项 value/label（前端点按钮场景）→ SELECT；</li>
     *   <li><b>LLM 分类</b>：选择/补充（select）vs 与选项无关的新话题/新需求（new）——
     *       新意图需要新技能时强制 replan，避免 agent 用旧白名单工具硬答；</li>
     *   <li><b>兜底</b>：LLM 失败默认 SELECT（不打断当前任务；agent 仍有【需要重新规划】信号兜底）。</li>
     * </ol>
     */
    static OptionIntent resolveOptionIntent(String choice, String optionsJson, ChatModel model) {
        if (choice == null || choice.isBlank()) return OptionIntent.SELECT;
        // 1) 确定性：点按钮时 choice 精确等于某选项 value/label
        try {
            JSONArray arr = JSONUtil.parseArray(optionsJson);
            for (Object o : arr) {
                String v = null;
                String l = null;
                if (o instanceof JSONObject jo) {
                    v = jo.getStr("value", jo.getStr("label", null));
                    l = jo.getStr("label", null);
                } else if (o instanceof String s) {
                    v = l = s;
                }
                String c = choice.trim();
                if (c.equals(v) || c.equals(l)) return OptionIntent.SELECT;
            }
        } catch (Exception ignored) {
            // 解析失败走 LLM 分类
        }
        // 2) LLM 分类（模型不可用时跳过，走默认 SELECT）
        if (model != null) {
            try {
                String prompt = "系统向用户展示了以下选择项：\n" + optionsJson + "\n\n用户对此回复了：" + choice
                        + "\n请判断用户意图，只输出 JSON {\"intent\":\"select\"|\"new\",\"reason\":\"一句话\"}：\n"
                        + "select=用户在选择某个选项、补充信息或澄清（如「选第一家」「第二家吧」「就它了」「选评分高的」）；\n"
                        + "new=用户开启了与选项完全无关的新话题或新需求（如「帮我取号」「推荐快餐店」「查我的订单」），此时不应继续当前选择流程。";
                ChatResponse resp = model.chat(ChatRequest.builder()
                        .messages(List.of(
                                SystemMessage.from("你是选择意图识别器，只输出 JSON。"),
                                UserMessage.from(prompt)))
                        .responseFormat(ResponseFormat.JSON)
                        .build());
                cn.hutool.json.JSONObject jo = JSONUtil.parseObj(resp.aiMessage().text());
                return "new".equals(jo.getStr("intent", "select")) ? OptionIntent.NEW : OptionIntent.SELECT;
            } catch (Exception e) {
                log.warn("resolveOptionIntent failed, default SELECT: {}", e.getMessage());
            }
        }
        return OptionIntent.SELECT;
    }

    private String userResponse(ReActAgentState state) {
        Object r = state.scratchpad().get("_user_response");
        if (r != null && !r.toString().isEmpty()) {
            return r.toString();
        }
        return state.userChoice();
    }

    private String encodeCallFrom(String toolName, String argsStr) {
        try {
            return "{\"tool\":\"" + toolName + "\",\"args\":" + argsStr + "}";
        } catch (Exception e) {
            return "{\"tool\":\"" + toolName + "\"}";
        }
    }

    /** 解析 askUserToChoose 参数 → [提示语, 选项JSON数组]。解析失败返回 null。 */
    private String[] parseAskOptions(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) return null;
        try {
            JSONObject jo = JSONUtil.parseObj(argsStr);
            String prompt = jo.getStr("prompt", "请选择");
            Object opts = jo.get("options");
            if (opts instanceof JSONArray arr && !arr.isEmpty()) {
                return new String[]{prompt, arr.toString()};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 用户查询是否明确要「取号」（取个号/取号排队等）；取消排队/排队查询不算。 */
    private boolean wantsTakeQueue(String query) {
        if (query == null) return false;
        return query.contains("取号") || query.contains("取个号");
    }

    /** 消息中是否已有取号提醒（防重复 nudge 死循环）。 */
    private boolean alreadyNudged(List<Map<String, String>> messages) {
        for (Map<String, String> m : messages) {
            String c = m.get("content");
            if (c != null && c.contains("[操作未完成]")) return true;
        }
        return false;
    }

    /** 消息中是否已执行/尝试过写工具（takeQueueNumber/cancelMyQueue）。 */
    private boolean writeAttempted(List<Map<String, String>> messages) {
        for (Map<String, String> m : messages) {
            if ("tool".equals(m.get("role"))) {
                String n = m.get("toolName");
                if (n != null && (n.contains("takeQueueNumber") || n.contains("cancelMyQueue"))) return true;
            }
        }
        return false;
    }
}
