package com.hmdp.agent.memory.context;

import com.hmdp.agent.graph.GraphInputFactory;
import com.hmdp.agent.graph.NodeNames;
import com.hmdp.agent.graph.nodes.Transcript;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.agent.memory.reflection.Reflection;
import com.hmdp.agent.memory.reflection.ReflectionStore;
import com.hmdp.agent.skill.Skill;
import com.hmdp.dto.ChatHistoryRound;
import com.hmdp.repository.ChatHistoryRepository;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 上下文管理节点 — 图入口，注入结构化上下文块并执行滑动窗口/压缩。
 *
 * <p>构建的 contextBlock 供 Planner/Agent/Answer 共用，包含：
 * <ol>
 *   <li>System Prompt（核心规则）</li>
 *   <li>用户画像（跨会话持久化记忆，含时间戳冲突解决）</li>
 *   <li>记忆摘要（压缩后的历史摘要）</li>
 *   <li>对话历史（从 PostgreSQL tb_chat_history 加载最近 N 轮）</li>
 * </ol>
 * <p>工具调用结果不在此块中，由各节点从 state.scratchpad() 动态读取。</p>
 */
public class ContextNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ContextNode.class);

    private final SlidingWindowManager windowManager;
    private final UserStore userStore;
    private final ChatHistoryRepository chatHistoryRepo;
    private final com.hmdp.agent.skill.SkillRegistry skillRegistry;
    private final ReflectionStore reflectionStore;
    private final CompressionConfig compressionConfig;

    private static final int MAX_CONTEXT_ROUNDS = 3;
    private static final int MAX_REFLECTIONS = 2;

    private static final String SYSTEM_RULES = """
            ## 核心规则
            - 绝对不要为了迎合用户而强行编造逻辑来解释冲突。
            - 如果发现不可调和的事实矛盾，请直接询问用户，而不是自作主张地修改历史数据。
            - 当用户提供的信息前后冲突时，优先采纳用户近期的表述。
            - 你不会的就坦诚告知，不要编造。
            - 用户改变主意时，无条件跟随新意图，不要被历史需求绑架。
            """;

    public ContextNode(SlidingWindowManager windowManager, UserStore userStore,
                       ChatHistoryRepository chatHistoryRepo,
                       com.hmdp.agent.skill.SkillRegistry skillRegistry,
                       ReflectionStore reflectionStore, CompressionConfig compressionConfig) {
        this.windowManager = windowManager;
        this.userStore = userStore;
        this.chatHistoryRepo = chatHistoryRepo;
        this.skillRegistry = skillRegistry;
        this.reflectionStore = reflectionStore;
        this.compressionConfig = compressionConfig;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        long t0 = System.currentTimeMillis();
        log.debug("ContextNode: building context for session {}", state.sessionId());

        // 1. 滑动窗口压缩
        Map<String, Object> updates = new LinkedHashMap<>(windowManager.manageContext(state));
        log.info("ContextNode: sliding window done in {} ms, messages={}",
                System.currentTimeMillis() - t0, state.messages().size());

        // 2. 构建结构化上下文块（优先用本轮异步压缩产出的摘要，否则用已有摘要）
        long t1 = System.currentTimeMillis();
        Object newSummary = updates.get("compressedSummary");
        String effectiveSummary = newSummary != null ? newSummary.toString() : state.compressedSummary();
        String contextBlock = buildContextBlock(state, effectiveSummary);
        updates.put("contextBlock", contextBlock);
        // 无历史版本：供 Agent 决策/Answer 注入（历史由 trimmed messages 承担，避免与 contextBlock 对话历史重复）
        String contextBlockNoHistory = buildContextBlockNoHistory(state, effectiveSummary);
        updates.put("contextBlockNoHistory", contextBlockNoHistory);
        log.info("ContextNode: context block built in {} ms ({} chars, noHistory {} chars)",
                System.currentTimeMillis() - t1, contextBlock.length(), contextBlockNoHistory.length());

        // 2b. L2/L3 领域规则单独存 scratchpad（匹配 skill 的 SOP + references）——
        //     只在 Agent 执行阶段注入；Planner/Answer 不暴露，避免规划阶段看到执行细节
        String domainRules = buildDomainRules(state);
        if (!domainRules.isBlank()) {
            Map<String, Object> scratchpad = state.scratchpad();
            scratchpad.put(StateKeys.SP_DOMAIN_RULES, domainRules);
            updates.put("scratchpad", scratchpad);
        }

        // 2c. 本轮 user 请求追加到标准 messages 通道（LangGraph：图输入作为消息）。
        //     resume（写确认恢复）不经过这里（updateState 直达 agent），用户响应由 AgentNode 恢复分支写入。
        String query = state.userQuery();
        if (query != null && !query.isBlank()) {
            Object existing = updates.get("messages");
            if (existing instanceof List<?> list && !list.isEmpty()) {
                List<Map<String, String>> merged = new ArrayList<>();
                for (Object o : list) if (o instanceof Map) merged.add((Map<String, String>) o);
                merged.add(ReActAgentState.userMsg(query));
                updates.put("messages", merged);
            } else {
                updates.put("messages", List.of(ReActAgentState.userMsg(query)));
            }
        }

        // 2d. 轮次计数器重置（唯一轮次重置点）：context 是新一轮的入口——
        //     新对话 init 已清零，这里覆盖「agent → context 全新话题」路径；resume 不经过 context，计数保留。
        updates.put(StateKeys.COUNTERS, GraphInputFactory.newCounters());

        // 3. 确保路由到 planner
        if (!updates.containsKey(StateKeys.NEXT_NODE)) {
            updates.put(StateKeys.NEXT_NODE, NodeNames.PLANNER);
        }
        log.info("ContextNode: total {} ms", System.currentTimeMillis() - t0);
        return updates;
    }

    /**
     * 组装匹配 skill 的领域规则（skill 的决策期 SOP：何时使用/工具决策/流程/护栏 + references/ 参考文档）。
     * 「回答规则」段（Answer 阶段输出规范）与 answer- 前缀 references 不注入——那是 Answer 阶段的事，
     * 决策阶段不需要卡片输出规范，避免每轮决策重复占 token。
     * 由 AgentNode 在组装执行 prompt 时注入，Planner 阶段不参与。
     */
    private String buildDomainRules(ReActAgentState state) {
        List<Skill> matched = resolveSkills(state);
        if (matched == null || matched.isEmpty() || skillRegistry == null) return "";
        StringBuilder sb = new StringBuilder();
        String rules = skillRegistry.decisionRulesText(matched);
        if (rules != null && !rules.isBlank()) {
            sb.append("## 领域规则\n").append(rules).append("\n");
        }
        String refs = skillRegistry.referencesText(matched);
        if (refs != null && !refs.isBlank()) {
            sb.append("\n").append(refs).append("\n");
        }
        return sb.toString();
    }

    /** skill 匹配：优先 Planner 语义选中的 selectedSkills（与工具白名单一致），为空回退触发词匹配。 */
    private List<Skill> resolveSkills(ReActAgentState state) {
        List<String> selected = state.selectedSkills();
        if (selected != null && !selected.isEmpty()) {
            List<Skill> skills = new ArrayList<>();
            for (String name : selected) {
                Skill s = skillRegistry.get(name);
                if (s != null) skills.add(s);
            }
            if (!skills.isEmpty()) return skills;
        }
        return skillRegistry.matchSkills(state.userQuery());
    }

    /**
     * 完整上下文块（含对话历史，供 Planner——Planner 只读 contextBlock 不重放 messages）。
     */
    private String buildContextBlock(ReActAgentState state, String summary) {
        String core = buildContextCore(state, summary);
        String history = buildContextHistory(state);
        if (history.isEmpty()) return core;
        return core + "\n" + history;
    }

    /**
     * 不含对话历史段的上下文块（供 Agent 决策/Answer 注入——历史由 trimmed messages 承担，避免重复）。
     */
    private String buildContextBlockNoHistory(ReActAgentState state, String summary) {
        return buildContextCore(state, summary);
    }

    /** 核心上下文：系统规则 + 定位 + 地区 + 画像 + 记忆摘要 + 过往经验。 */
    private String buildContextCore(ReActAgentState state, String summary) {
        StringBuilder sb = new StringBuilder();

        // --- System Prompt（规则） ---
        sb.append(SYSTEM_RULES);

        // --- 用户定位（前端地区中心坐标，供 geoSearch 等直接使用） ---
        String location = state.userLocation();
        if (location != null && !location.isEmpty()) {
            sb.append("\n## 用户定位\n").append(location)
              .append("\n（geoSearch 等地理位置工具直接使用此坐标，不要向用户索要经纬度）\n");
        }
        // --- 用户当前地区（让 Agent 明确自己所在地区，避免猜错 districtId） ---
        Long districtId = state.userDistrictId();
        if (districtId != null && districtId > 0) {
            sb.append("\n用户当前地区：").append(districtName(districtId))
              .append("（districtId=").append(districtId).append("）。搜索店铺时不要传 districtId，工具会自动按此地区过滤；只有用户明确要求查其它地区时才传对应 districtId（1=杭州拱墅区/2=福州鼓楼区）。\n");
        }

        // --- 匹配 skill 的领域规则：已移出 contextBlock，单独存 scratchpad（buildDomainRules），
        //     仅 Agent 执行阶段注入，避免 Planner 提前暴露执行细节 ---
        String query = state.userQuery();

        // --- 用户画像（从 PostgreSQL 读取） ---
        Long userId = extractUserId(state);
        if (userId != null) {
            String profile = userStore.toPromptContext(userId);
            if (!profile.isEmpty()) {
                sb.append("\n").append(profile).append("\n");
            }
        }

        // --- 记忆摘要（异步压缩结果，可能为本轮刚生成） ---
        if (summary != null && !summary.isEmpty()) {
            sb.append("\n## 历史记忆\n").append(summary).append("\n");
        }

        // --- Reflexion 过往经验（跨会话失败教训，相似查询时提醒避免重蹈覆辙） ---
        if (query != null && !query.isBlank() && reflectionStore != null) {
            try {
                List<Reflection> reflections = reflectionStore.findRelevant(query, MAX_REFLECTIONS);
                if (!reflections.isEmpty()) {
                    sb.append("\n## 过往经验\n");
                    for (Reflection r : reflections) {
                        if (r.getLesson() != null && !r.getLesson().isBlank()) {
                            sb.append("- [").append(nullSafe(r.getDomain())).append("] ")
                              .append(r.getLesson()).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Reflexion inject skipped: {}", e.getMessage());
            }
        }

        return sb.toString();
    }

    /** 对话历史段：优先从 checkpoint state.messages() 读取，不足时用 MySQL 补充。 */
    private String buildContextHistory(ReActAgentState state) {
        StringBuilder sb = new StringBuilder();

        List<Map<String, String>> stateMsgs = state.messages();
        if (!stateMsgs.isEmpty()) {
            // 按真实轮次对齐：保留最近 keepRecentRounds 轮（含 tool 执行结果），供 Planner/ReAct 跨轮复用已获取信息
            int keepRounds = compressionConfig.getKeepRecentRounds();
            int start = Transcript.nthRealUserIdx(stateMsgs, keepRounds);
            if (start < 0) start = 0;
            sb.append("\n## 对话历史（含工具执行结果）\n");
            for (int i = start; i < stateMsgs.size(); i++) {
                Map<String, String> m = stateMsgs.get(i);
                String role = m.get("role");
                String content = m.get("content");
                if (content == null || content.isBlank()) continue;
                if ("user".equals(role)) {
                    // 跳过注入提醒（nudge / 空结果换思路）
                    if (content.startsWith("[操作未完成]") || content.startsWith("[结果为空]")) continue;
                    sb.append("user: ").append(truncate(content, 300)).append("\n");
                } else if ("assistant".equals(role)) {
                    if (m.get("toolCalls") != null && !m.get("toolCalls").isBlank()) {
                        sb.append("assistant 调用工具：").append(truncate(m.get("toolCalls"), 150)).append("\n");
                    } else {
                        sb.append("assistant: ").append(truncate(content, 300)).append("\n");
                    }
                } else if ("tool".equals(role)) {
                    sb.append("工具[").append(m.getOrDefault("toolName", "")).append("] 结果：")
                      .append(truncate(content, 300)).append("\n");
                }
            }
        } else {
            Long userId = extractUserId(state);
            if (userId != null && chatHistoryRepo != null) {
                // checkpoint 无消息时回退到 MySQL（迁移期兼容）
                List<ChatHistoryRound> rounds = chatHistoryRepo.findRounds(userId, null, MAX_CONTEXT_ROUNDS);
                if (!rounds.isEmpty()) {
                    sb.append("\n## 对话历史\n");
                    for (int i = rounds.size() - 1; i >= 0; i--) {
                        ChatHistoryRound r = rounds.get(i);
                        String userMsg = r.getUserMessage();
                        String aiMsg = r.getAssistantMessage();
                        if (userMsg != null && userMsg.length() > 300)
                            userMsg = userMsg.substring(0, 300) + "...";
                        if (aiMsg != null && aiMsg.length() > 300)
                            aiMsg = aiMsg.substring(0, 300) + "...";
                        sb.append("user: ").append(userMsg).append("\n");
                        sb.append("assistant: ").append(aiMsg).append("\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "通用" : s;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    /** 地区 id → 名称（与 tb_district 一致） */
    private static String districtName(Long id) {
        return id != null && id == 2L ? "福州鼓楼区" : "杭州拱墅区";
    }

    private Long extractUserId(ReActAgentState state) {
        try {
            Object val = state.data().get("userId");
            if (val instanceof Number) return ((Number) val).longValue();
            if (val instanceof String) return Long.parseLong((String) val);
        } catch (Exception ignored) {}
        return null;
    }
}
