package com.hmdp.agent.graph.nodes;

import cn.hutool.json.JSONUtil;
import com.hmdp.agent.graph.NodeNames;
import com.hmdp.agent.graph.dto.JsonParser;
import com.hmdp.agent.graph.dto.PlanRequest;
import com.hmdp.agent.graph.prompt.PromptTemplates;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.agent.tool.ShopTypeProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Planner Node — 纯规划，不做充分性判断。
 *
 * <p>两种模式：
 * <ul>
 *   <li><b>初始规划</b>：分析用户意图，制定执行计划</li>
 *   <li><b>重规划</b>（写确认时用户调整参数，重新执行）：旧计划未执行，直接按新意图重规划</li>
 * </ul>
 */
public class PlannerNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);
    private final ChatModel model;
    private final int maxIterations;
    private final LC4jToolService toolService;
    private final ShopTypeProvider shopTypeProvider;
    private final com.hmdp.agent.skill.SkillRegistry skillRegistry;
    private final com.hmdp.agent.memory.context.ProfileExtractor profileExtractor;

    public PlannerNode(ChatModel model, int maxIterations,
                       LC4jToolService toolService, ShopTypeProvider shopTypeProvider,
                       com.hmdp.agent.skill.SkillRegistry skillRegistry,
                       com.hmdp.agent.memory.context.ProfileExtractor profileExtractor) {
        this.model = model;
        this.maxIterations = maxIterations;
        this.toolService = toolService;
        this.shopTypeProvider = shopTypeProvider;
        this.skillRegistry = skillRegistry;
        this.profileExtractor = profileExtractor;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        Map<String, Object> counters = state.counters();
        int iter = ((Number) counters.getOrDefault(StateKeys.ITERATION, 0)).intValue() + 1;
        counters.put(StateKeys.ITERATION, iter);
        if (iter > maxIterations) {
            log.warn("Planner: max iterations reached ({})", maxIterations);
            return Map.of(StateKeys.COUNTERS, counters, StateKeys.NEXT_NODE, NodeNames.ANSWER);
        }

        String query = state.userQuery();
        // 实时画像提取：Planner 阶段异步检测当前输入中的画像信息并保存，不必等 ContextCompressor 压缩
        profileExtractor.asyncExtract(state.userId(), query);
        // Planner 只暴露 skill（能力层），不暴露具体工具 —— 规划用「技能+目的」表达，
        // 具体工具由 Agent 执行时按 skill 展开
        String skillListBlock = buildSkillListBlock(skillRegistry, query);
        String roundEvidence = state.roundEvidence();
        boolean isReplan = (roundEvidence != null && !roundEvidence.isEmpty());

        // ============================================================
        // 重规划次数限制：最多 1 次 replan，超出后基于已有信息生成回答
        // ============================================================
        if (isReplan && state.replanCount() >= 1) {
            log.warn("Planner: replan limit reached ({}), routing to answer with context", state.replanCount());
            String judgeReason = "未找到足够信息";
            StringBuilder limitPrompt = new StringBuilder();
            limitPrompt.append(state.contextBlock()).append("\n");
            limitPrompt.append("## 用户问题\n").append(query).append("\n\n");
            limitPrompt.append("## 已执行的计划\n").append(state.plan()).append("\n\n");
            limitPrompt.append("## 收集到的数据\n").append(formatToolResults(state.scratchpad())).append("\n\n");
            limitPrompt.append("## 最终判断\n").append(judgeReason).append("\n\n");
            limitPrompt.append("请基于以上信息生成回答。要求：\n");
            limitPrompt.append("- 坦诚告知用户查询结果，说明查询范围和尝试的方式\n");
            limitPrompt.append("- 给出具体建议帮助用户下一步操作（如扩大范围、换个关键词、提供更多信息等）\n");
            limitPrompt.append("- 禁止暴露内部ID、SQL、表名、工具名等技术细节\n");
            limitPrompt.append("- **若收集到的数据包含店铺信息（searchShops/searchShop/recommendShops/geoSearch 结果含 id 与 name）**："
                    + "在回答中为涉及的店铺输出卡片占位符——店名后紧跟 `[[对外ID]]`（对外ID 原样复制工具结果里该店的 id 字段，禁止编造/改写）；"
                    + "占位符后不加标点/空格/换行，前端据此渲染店铺卡片。数据库数字 ID 一律不对外\n");
            return Map.of(StateKeys.COUNTERS, counters,
                    StateKeys.STREAMING_PROMPT, limitPrompt.toString(),
                    StateKeys.FINAL_ANSWER, StateKeys.SENTINEL_STREAMING,
                    StateKeys.NEXT_NODE, NodeNames.ANSWER);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(state.contextBlock());
        prompt.append("\n");

        if (isReplan) {
            // ============================================================
            // 重规划模式：旧计划失败，必须换思路
            // ============================================================
            prompt.append("## 已失败的原始计划\n").append(state.plan()).append("\n\n");
            prompt.append("## 执行结果\n").append(roundEvidence).append("\n\n");
            prompt.append("## 用户请求\n").append(query).append("\n\n");
            prompt.append("上述计划未能获取足够信息。请制定一个**全新**的计划：\n");
            prompt.append("- 不要重复原始计划中已失败的步骤，换一个思路\n");
            prompt.append("- 例如：之前用精确条件没查到，改用更宽的条件（降评分/去类型/换美食细分/换关键词）；换一个技能切入\n");
            prompt.append("- 如果确实无法通过任何技能获取所需数据，输出 ask_user\n");
            prompt.append("- 如果所有技能都无法满足用户需求（即使换思路也不行），输出 cannot_fulfill\n");
            prompt.append("- 输出JSON：{\"intent\":\"用户意图\",\"complex\":true,\"plan\":[\"第1步：用X技能...\",\"第2步：...\"]}\n");
            prompt.append("- 或：{\"ask_user\": \"需要用户提供什么信息\"}\n");
            prompt.append("- 或：{\"cannot_fulfill\": \"坦诚说明限制并给出替代建议\"}\n\n");
            prompt.append(skillListBlock).append("\n");
        } else {
            // ============================================================
            // 初始规划模式
            // ============================================================
            prompt.append("## 当前请求\n用户: ").append(query).append("\n");
            prompt.append("已有数据: ").append(formatToolResults(state.scratchpad())).append("\n\n");
            prompt.append("分析用户意图并输出JSON：\n");
            prompt.append("- 无需查询（问候/闲聊/常识/建议等）：{\"intent\":\"用户意图\",\"complex\":false}\n");
            prompt.append("- 需查询数据（店铺/团购/订单/评价/排队/历史/流程等）：{\"intent\":\"用户意图\",\"complex\":true,\"skills\":[\"shop\"],\"plan\":[\"第1步：用shop技能做Y，因为Z\",\"第2步：...\"]}\n");
            prompt.append("- 缺信息且无法通过任何技能获取（如登录凭证）：{\"ask_user\":\"需要补充什么信息\"}\n");
            prompt.append("- 超出能力（无技能可代做）：{\"cannot_fulfill\":\"坦诚说明限制+替代建议\"}\n\n");
            prompt.append(skillListBlock).append("\n");
        }

        try {
            String promptStr = prompt.toString();
            log.debug("Planner prompt (iter {}, replan={}):\n{}", iter, isReplan, promptStr);
            // 原生 structured output：强制 json_object，保证 plan/ask_user/cannot_fulfill 是合法 JSON
            ChatResponse resp = model.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from(PromptTemplates.PLANNER_SYSTEM),
                            UserMessage.from(promptStr))
                    // DeepSeek 不支持 response_format:json_schema（实测 400），故用 json_object + prompt 字段约束
                    .responseFormat(ResponseFormat.JSON)
                    .build());
            String raw = resp.aiMessage().text();
            log.info("Planner (iter {}): {}", iter, raw);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(StateKeys.COUNTERS, counters);

            PlanRequest planReq = JsonParser.parsePlan(raw);

            if (planReq != null && hasText(planReq.getCannotFulfill())) {
                result.put(StateKeys.FINAL_ANSWER, planReq.getCannotFulfill());
                result.put(StateKeys.NEXT_NODE, NodeNames.ANSWER);
                log.info("Planner: cannot_fulfill detected, routing to answer: {}", planReq.getCannotFulfill());
            } else if (planReq != null && hasText(planReq.getAskUser())) {
                result.put(StateKeys.FINAL_ANSWER, planReq.getAskUser());
                result.put(StateKeys.NEXT_NODE, NodeNames.ANSWER);
                log.info("Planner: ask_user detected, routing to answer: {}", planReq.getAskUser());
            } else if (planReq != null && Boolean.FALSE.equals(planReq.getComplex())) {
                // 闲聊、问候、自我介绍等无需工具的问题 → 直接回答
                log.info("Planner: simple intent detected, routing to answer");
                result.put(StateKeys.NEXT_NODE, NodeNames.ANSWER);
            } else {
                result.put(StateKeys.NEXT_NODE, NodeNames.AGENT);
                // 显式步骤列表 + 执行索引（替代 remainPlan 裁剪）
                if (planReq != null && planReq.getPlan() != null) {
                    result.put("plan", JSONUtil.toJsonStr(planReq.getPlan()));
                }
                // Planner 语义选中的技能名 → Agent 按此暴露工具
                if (planReq != null && planReq.getSkills() != null && !planReq.getSkills().isEmpty()) {
                    result.put("selectedSkills", JSONUtil.toJsonStr(planReq.getSkills()));
                }
                if (isReplan) {
                    result.put("roundEvidence", "");
                    counters.put(StateKeys.REPLAN_COUNT, state.replanCount() + 1);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Planner failed at iter {}", iter, e);
            // 异常详情只在服务端日志，不向用户暴露技术细节
            return Map.of(StateKeys.COUNTERS, counters, StateKeys.NEXT_NODE, NodeNames.ANSWER,
                    StateKeys.FINAL_ANSWER, "抱歉，我暂时无法处理这个请求，请换个说法再试一次。");
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }

    static String formatToolResults(Map<String, Object> sp) {
        if (sp == null || sp.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sp.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals(StateKeys.SP_ASK_USER_MISSING) || k.equals(StateKeys.SP_ERROR)) continue;
            String v = e.getValue() != null ? e.getValue().toString() : "";
            if (v.length() > 800) v = v.substring(0, 800) + "...";
            sb.append(k).append(": ").append(v).append("\n");
        }
        return sb.toString();
    }

    /** Planner 语义选 skill：展示全部技能的能力目录，让 LLM 按语义挑选 plan.skills。 */
    private String buildSkillListBlock(com.hmdp.agent.skill.SkillRegistry skillRegistry, String query) {
        java.util.List<com.hmdp.agent.skill.Skill> skills = skillRegistry.all();
        StringBuilder sb = new StringBuilder("## 可用技能（能力层，按语义挑选，输出到 plan/skills）\n\n");
        if (skills.isEmpty()) {
            sb.append("- **general**：通用查询兜底\n");
            return sb.toString();
        }
        for (com.hmdp.agent.skill.Skill s : skills) {
            String desc = s.description() != null ? s.description() : "";
            int i = desc.indexOf("。触发词");
            String brief = i > 0 ? desc.substring(0, i) : desc;
            sb.append("- **").append(s.name()).append("**：").append(brief).append("\n");
        }
        sb.append("\n## 规划规则\n");
        sb.append("- 步骤用「技能名 + 目的」表达（如「第1步：用 shop 技能搜索火锅店」「第2步：用 queue 技能取号」），不要写具体工具名或 SQL。\n");
        sb.append("- **必须输出 skills 字段**：从上面技能目录挑选本次要用的技能名列表（如 [\"shop\",\"queue\"]），Agent 只暴露这些技能对应的工具。\n");
        sb.append("- **技能依赖**：取号/查排队（queue）、查评价/笔记（content）都需先有 shopId——**只有 shop 技能能提供**。「查肯德基评价」→ skills:[\"shop\",\"content\"]；「在肯德基取号」→ skills:[\"shop\",\"queue\"]；上轮已查过目标店（消息里有店信息）可只选本技能，复用历史 shopId。\n");
        sb.append("- 最多 5 步；大结果集查询（所有/全部/哪些）先缩小范围（加地区/类型/条件），禁止逐条遍历。\n");
        return sb.toString();
    }

    private static String schemaType(JsonSchemaElement prop) {
        String className = prop.getClass().getSimpleName();
        return className.replace("Json", "").replace("Schema", "").toLowerCase();
    }
}
