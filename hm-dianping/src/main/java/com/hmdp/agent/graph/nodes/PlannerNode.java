package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.dto.JsonParser;
import com.hmdp.agent.graph.dto.PlanRequest;
import com.hmdp.agent.graph.prompt.PromptTemplates;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import com.hmdp.agent.tool.ShopTypeProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
 *   <li><b>初始规划</b>（observerReport 为空）：分析用户意图，制定执行计划</li>
 *   <li><b>重规划</b>（observerReport 有内容 + observerFeedback 来自 JudgeNode）：
 *       旧计划已失败，必须换思路，不能重复已失败的路径</li>
 * </ul>
 */
public class PlannerNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);
    private final OpenAiChatModel model;
    private final int maxIterations;
    private final String toolListBlock;
    private final String toolNamesBlock;


    public PlannerNode(OpenAiChatModel model, int maxIterations,
                       LC4jToolService toolService, ShopTypeProvider shopTypeProvider) {
        this.model = model;
        this.maxIterations = maxIterations;
        this.toolListBlock = buildToolListBlock(toolService, shopTypeProvider);
        this.toolNamesBlock = buildToolNamesBlock(toolService);
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        int iter = state.iteration() + 1;
        if (iter > maxIterations) {
            log.warn("Planner: max iterations reached ({})", maxIterations);
            return Map.of("iteration", iter, "nextNode", "answer");
        }

        String query = state.userQuery();
        String observerReport = state.observerReport();
        String feedback = state.observerFeedback();
        boolean isReplan = (observerReport != null && !observerReport.isEmpty())
                || (feedback != null && !feedback.isEmpty());

        // ============================================================
        // 重规划次数限制：最多 1 次 replan，超出后基于已有信息生成回答
        // ============================================================
        if (isReplan && state.replanCount() >= 1) {
            log.warn("Planner: replan limit reached ({}), routing to answer with context", state.replanCount());
            String judgeReason = feedback != null && !feedback.isEmpty() ? feedback : "未找到足够信息";
            StringBuilder limitPrompt = new StringBuilder();
            limitPrompt.append(state.contextBlock()).append("\n");
            limitPrompt.append("## 用户问题\n").append(query).append("\n\n");
            limitPrompt.append("## 已执行的计划\n").append(state.planJson()).append("\n\n");
            limitPrompt.append("## 收集到的数据\n").append(formatToolResults(state.scratchpad())).append("\n\n");
            limitPrompt.append("## 最终判断\n").append(judgeReason).append("\n\n");
            limitPrompt.append("请基于以上信息生成回答。要求：\n");
            limitPrompt.append("- 坦诚告知用户查询结果，说明查询范围和尝试的方式\n");
            limitPrompt.append("- 给出具体建议帮助用户下一步操作（如扩大范围、换个关键词、提供更多信息等）\n");
            limitPrompt.append("- 禁止暴露内部ID、SQL、表名、工具名等技术细节\n");
            return Map.of("iteration", iter,
                    "streamingPrompt", limitPrompt.toString(),
                    "finalAnswer", "__STREAMING__",
                    "nextNode", "answer");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(state.contextBlock());
        prompt.append("\n");

        if (isReplan) {
            // ============================================================
            // 重规划模式：旧计划失败，必须换思路
            // ============================================================
            prompt.append("## 已失败的原始计划\n").append(state.planJson()).append("\n\n");
            prompt.append("## 执行结果\n").append(observerReport).append("\n\n");
            if (feedback != null && !feedback.isEmpty()) {
                prompt.append("## Judge 反馈\n").append(feedback).append("\n\n");
            }
            prompt.append("## 用户请求\n").append(query).append("\n\n");
            prompt.append("上述计划未能获取足够信息。请制定一个**全新**的执行计划：\n");
            prompt.append("- 不要重复原始计划中已失败的步骤，换一个查询思路\n");
            prompt.append("- 例如：如果之前只查了单表，考虑联表；如果之前用精确匹配，改用模糊匹配\n");
            prompt.append("- 如果确实无法通过任何工具获取所需数据，输出 ask_user\n");
            prompt.append("- 如果所有工具都无法满足用户需求（即使换思路也不行），输出 cannot_fulfill\n");
            prompt.append("- 输出JSON：{\"intent\":\"用户意图\",\"complex\":true,\"plan\":[\"第1步：...\",\"第2步：...\"]}\n");
            prompt.append("- 或：{\"ask_user\": \"需要用户提供什么信息\"}\n");
            prompt.append("- 或：{\"cannot_fulfill\": \"坦诚说明限制并给出替代建议\"}\n\n");
            prompt.append(toolListBlock).append("\n");
        } else {
            // ============================================================
            // 初始规划模式
            // ============================================================
            prompt.append("## 当前请求\n用户: ").append(query).append("\n");
            prompt.append("已有数据: ").append(formatToolResults(state.scratchpad())).append("\n\n");
            prompt.append("分析用户意图并制定计划。输出JSON：\n\n");
            prompt.append("简单问题（无需查询数据，LLM 自身知识即可回答）：\n");
            prompt.append("{\"intent\":\"用户意图一句话\",\"complex\":false}\n");
            prompt.append("适用场景：问候、闲聊、自我介绍、感谢、道别、常识问答、建议咨询等\n\n");
            prompt.append("需要查询数据（必须调用工具才能获取信息）：\n");
            prompt.append("{\"intent\":\"用户意图\",\"complex\":true,\"plan\":[\"第1步：用X工具做Y，因为Z\",\"第2步：...\"]}\n");
            prompt.append("适用场景：查店铺、搜商品、查订单、看评价、排队取号等需要数据库/外部数据的请求\n\n");
            prompt.append("历史回溯（用户提及上下文窗口中不存在的历史对话）：\n");
            prompt.append("{\"intent\":\"历史回溯\",\"complex\":true,\"plan\":[\"第1步：用 searchHistory 搜索关键词X、Y、Z\"]}\n");
            prompt.append("适用场景：用户说「上次我们聊过」「之前推荐的」「还记得我问过」等引用历史对话但窗口中没有相关内容时。\n");
            prompt.append("从用户消息中提取关键词（名词、实体、话题），用 searchHistory 工具检索。\n\n");
            prompt.append("缺少用户信息且无法通过工具获取（如地理位置、登录凭证）：\n");
            prompt.append("{\"ask_user\": \"需要补充什么信息\"}\n\n");
            prompt.append("超出能力范围（用户直接要求执行一个操作，且没有任何工具能做到）：\n");
            prompt.append("{\"cannot_fulfill\": \"坦诚说明目前做不到，并给出替代建议或告知后续可能会支持\"}\n");
            prompt.append("适用场景：「帮我领取优惠券」「帮我把订单退了」「帮我改一下收货地址」——用户要求 agent 代为执行某个动作。\n");
            prompt.append("判断标准：逐一检查每个可用工具的能力，如果所有工具都与用户需求无关，则输出 cannot_fulfill。\n");
            prompt.append("关键区分——知识问题 VS 操作请求：\n");
            prompt.append("  - 「怎么领取优惠券？」「如何退款？」→ 这是知识问题，应调用 knowledgeRetrieval 查询知识库，不要输出 cannot_fulfill\n");
            prompt.append("  - 「帮我领取优惠券」「帮我把这个订单退掉」→ 这是操作请求，工具做不到才输出 cannot_fulfill\n");
            prompt.append("  - 总结：用户问「怎么」「如何」「什么是」开头的是知识问题，用工具查；用户用「帮我」「给我」开头的是操作请求，判断工具能力。\n\n");
            prompt.append(toolListBlock).append("\n");
        }

        try {
            String promptStr = prompt.toString();
            log.info("Planner prompt (iter {}, replan={}):\n{}", iter, isReplan, promptStr);
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from(PromptTemplates.PLANNER_SYSTEM),
                    UserMessage.from(promptStr)));
            String raw = resp.aiMessage().text();
            log.info("Planner (iter {}): {}", iter, raw);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("iteration", iter);
            result.put("planJson", raw);

            PlanRequest planReq = JsonParser.parsePlan(raw);

            if (planReq != null && hasText(planReq.getCannotFulfill())) {
                result.put("finalAnswer", planReq.getCannotFulfill());
                result.put("nextNode", "answer");
                log.info("Planner: cannot_fulfill detected, routing to answer: {}", planReq.getCannotFulfill());
            } else if (planReq != null && hasText(planReq.getAskUser())) {
                result.put("finalAnswer", planReq.getAskUser());
                result.put("nextNode", "answer");
                log.info("Planner: ask_user detected, routing to answer: {}", planReq.getAskUser());
            } else if (planReq != null && Boolean.FALSE.equals(planReq.getComplex())) {
                // 闲聊、问候、自我介绍等无需工具的问题 → 直接回答
                log.info("Planner: simple intent detected, routing to answer");
                result.put("nextNode", "answer");
            } else {
                result.put("nextNode", "executor");
                result.put("remainPlan", raw);
                if (isReplan) {
                    result.put("observerReport", "");
                    result.put("replanCount", state.replanCount() + 1);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Planner failed at iter {}", iter, e);
            return Map.of("iteration", iter, "nextNode", "answer",
                    "finalAnswer", "规划失败: " + e.getMessage());
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

    private String buildToolListBlock(LC4jToolService toolService, ShopTypeProvider typeProvider) {
        StringBuilder sb = new StringBuilder("## 可用工具\n\n");
        List<ToolSpecification> specs = toolService.toolSpecifications();
        for (int i = 0; i < specs.size(); i++) {
            ToolSpecification ts = specs.get(i);
            sb.append(i + 1).append(". **").append(ts.name()).append("**：").append(ts.description()).append("\n");
            if (ts.parameters() != null && ts.parameters().properties() != null) {
                List<String> required = ts.parameters().required() != null
                        ? ts.parameters().required() : List.of();
                ts.parameters().properties().forEach((name, prop) -> {
                    String marker = required.contains(name) ? "必填" : "可选";
                    sb.append("   - `").append(name).append("`(")
                      .append(schemaType(prop)).append(") [").append(marker).append("]");
                    if (prop.description() != null && !prop.description().isEmpty()) {
                        sb.append(" — ").append(prop.description());
                    }
                    sb.append("\n");
                });
            }
            sb.append("\n");
        }
        sb.append("## 规划规则\n");
        sb.append("- 计划中只写可执行的工具调用步骤，不要写条件分支（如「如果为空则...」）或用户通知步骤（如「告知用户」）。\n");
        sb.append("- 工具执行后系统会自动观察结果并判断下一步，你不需要在计划里预判各种分支。\n");
        sb.append("- 制定计划前，先检查工具所需的 [必填] 参数。如果用户未提供，计划中必须包含获取该参数的步骤。\n");
        sb.append("- 例如：用户给的是商铺名称但工具需要 shopId → 计划中必须先查询商铺ID。\n");
        sb.append("- 如果 [必填] 参数无法通过任何工具获取（如用户地理位置坐标、登录凭证），第一步必须输出 ask_user 向用户索要，不要规划无法执行的步骤。\n");
        sb.append("- 商家类型映射（共").append(typeProvider.typeMap().size()).append("类：")
          .append(typeProvider.typeText()).append("）。用户说的「茶餐厅」「火锅」等具体菜系统统归入美食，不要规划「查询茶餐厅类型ID」这种步骤，直接查美食。\n");
        sb.append("- 写操作确认规则：排队取号(takeQueueNumber)、取消排队(cancelMyQueue) 等写操作必须由用户明确指定目标后才能执行。如果用户没有指定具体商铺，计划中必须先查询并列出选项，最后一步用 ask_user 让用户选择，禁止代用户决定。\n");
        sb.append("- 步骤数量限制：每个计划最多 5 步。复杂任务请优先合并步骤（如同时查多个条件），不要拆成过多小步骤。\n");
        sb.append("- 大结果集防护：用户问「所有」「全部」「哪些」等可能返回大量数据的查询时，工具会自动限制返回 20 条并提示总数。如果总数超过 20，你必须在下一步让用户缩小范围（如加条件、选区域、选类型），禁止逐条遍历全部结果。\n");
        return sb.toString();
    }

    private static String buildToolNamesBlock(LC4jToolService toolService) {
        return "可用工具：" + toolService.toolSpecifications().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.joining(" / "));
    }

    private static String schemaType(JsonSchemaElement prop) {
        String className = prop.getClass().getSimpleName();
        return className.replace("Json", "").replace("Schema", "").toLowerCase();
    }

    /** 不截断版本 — AnswerNode 使用，确保最终回答能看到全部数据 */
    public static String formatToolResultsFull(Map<String, Object> sp) {
        if (sp == null || sp.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sp.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals(StateKeys.SP_ASK_USER_MISSING) || k.equals(StateKeys.SP_ERROR)) continue;
            String v = e.getValue() != null ? e.getValue().toString() : "";
            sb.append(k).append(": ").append(v).append("\n");
        }
        return sb.toString();
    }
}
