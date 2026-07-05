package com.hmdp.agent.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Observer Node — 提取工具执行结果，剔除已完成步骤，根据剩余计划路由。
 *
 * <p>路由逻辑：
 * <ul>
 *   <li>remainPlan 还有步骤 → executor（继续执行下一步）</li>
 *   <li>remainPlan 已空 → planner（让 planner 判断完整性/生成新计划）</li>
 *   <li>executor 预设 answer → 透传 answer</li>
 * </ul>
 */
public class ObserverNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ObserverNode.class);
    private final OpenAiChatModel model;

    public ObserverNode(OpenAiChatModel model, int maxIterations) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        // ============================================================
        // 确认恢复检测：用户回复了之前的 ask_user，LLM 判断是否匹配
        // ============================================================
        String userChoice = state.userChoice();
        if (userChoice != null && !userChoice.isEmpty()) {
            String prompt = state.confirmationPrompt();
            boolean matches = validateUserResponse(prompt, userChoice);
            if (!matches) {
                log.info("Observer: user response doesn't match confirmation context, routing to planner for replan");
                return Map.of("observerReport",
                        "用户回复了「" + userChoice + "」，但与上一个问题「" + prompt + "」无关，需要重新规划。",
                        "nextNode", "planner",
                        "userChoice", "");  // 清空，避免下次再触发
            }
            log.info("Observer: user response validated, continuing from confirmation — {}", userChoice);
            // 用户回复有效 → 写入 scratchpad 供后续 executor 使用，继续当前流程
            Map<String, Object> sp = state.scratchpad();
            sp.put("_user_response", userChoice);
            return Map.of("scratchpad", sp,
                    "userChoice", "",  // 清空标记
                    "nextNode", "executor");
        }

        // executor 已预设 answer（ask_user / 异常），先检查是否有异常但工具已执行
        if ("answer".equals(state.nextNode()) && isToolActuallyExecuted(state)) {
            log.info("Observer: executor reported error but toolCallCount increased, tool executed — forwarding to judgeNode");
            return Map.of("nextNode", "judgeNode",
                    "observerReport", "工具执行后发生异常，但调用已成功，结果见下方数据。");
        }

        // executor 已预设 answer（ask_user / 异常），直接放行
        if ("answer".equals(state.nextNode())) {
            return Map.of("nextNode", "answer");
        }

        Map<String, Object> sp = state.scratchpad();
        String remainPlan = state.remainPlan();
        String report = translateToolResults(sp, state.planJson(), remainPlan);
        log.info("Observer report (iter={}): {}",
                state.iteration(),
                report.length() > 500 ? report.substring(0, 500) + "..." : report);

        Map<String, Object> result = new LinkedHashMap<>();

        // ============================================================
        // 空结果检测：全部结果为空时触发 1-2 次扩大搜索重试
        // ============================================================
        if (allResultsEmpty(sp)) {
            int retries = state.emptyResultRetries();
            if (retries < 2) {
                log.info("Observer: all results empty, triggering broaden retry {}/2", retries + 1);
                result.put("emptyResultRetries", retries + 1);
                result.put("remainPlan", remainPlan);  // 保持当前步骤不前进
                result.put("observerReport", buildRetryHint(report, retries + 1));
                result.put("nextNode", "executor");
                return result;
            }
            log.info("Observer: all results empty after {} retries, routing to judgeNode", retries);
            String nextRemain = stripFirstStep(remainPlan);
            result.put("observerReport", report + "\n\n[扩大搜索 " + retries + " 次后仍未找到匹配数据]");
            result.put("remainPlan", nextRemain);
            result.put("emptyResultRetries", 0);  // 重试耗尽，清零，避免影响下一轮
            result.put("nextNode", "judgeNode");
            return result;
        }

        // ============================================================
        // 正常路径：有数据，剔除已完成步骤，继续或转 judgeNode
        // ============================================================
        String nextRemain = stripFirstStep(remainPlan);
        result.put("observerReport", report);
        result.put("remainPlan", nextRemain);
        result.put("emptyResultRetries", 0);  // 有数据时清零重试计数

        // 上一步返回空结果 → 后续步骤可能依赖此数据，不能盲目推进
        // 交给 judgeNode 决定是 replan 还是继续
        if (latestResultEmpty(sp) && !nextRemain.isEmpty()) {
            log.info("Observer: latest step returned empty, fallback to judgeNode (subsequent steps may depend on it)");
            result.put("observerReport", report + "\n\n[上一步返回空结果，后续步骤可能依赖此数据，请判断是否需要重新规划]");
            result.put("nextNode", "judgeNode");
            return result;
        }

        if (!nextRemain.isEmpty()) {
            log.info("Observer: remaining steps exist, routing to executor");
            result.put("nextNode", "executor");
        } else {
            log.info("Observer: all planned steps done, routing to judgeNode for sufficiency check");
            result.put("nextNode", "judgeNode");
        }
        return result;
    }

    /**
     * 从 plan JSON 中移除第一个步骤。
     * 例如 plan:["step1","step2"] → plan:["step2"]
     * 如果只剩一个步骤或解析失败 → 返回空字符串
     */
    static String stripFirstStep(String planJson) {
        if (planJson == null || planJson.isEmpty()) return "";
        try {
            JSONObject obj = JSONUtil.parseObj(planJson);
            JSONArray plan = obj.getJSONArray("plan");
            if (plan == null || plan.isEmpty()) return "";
            plan.remove(0);
            if (plan.isEmpty()) return "";
            obj.set("plan", plan);
            return obj.toString();
        } catch (Exception e) {
            log.debug("stripFirstStep: unable to parse plan JSON, returning empty. raw: {}",
                    planJson.length() > 100 ? planJson.substring(0, 100) + "..." : planJson);
            return "";
        }
    }

    /**
     * 检测 scratchpad 中所有工具结果是否都为空。
     * 空信号：0 rows returned、空数组 []、未匹配到数据、空字符串等。
     */
    private static boolean allResultsEmpty(Map<String, Object> sp) {
        if (sp == null || sp.isEmpty()) return true;

        boolean hasData = false;
        boolean hasContent = false;

        for (Map.Entry<String, Object> e : sp.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals("error") || k.equals("ask_user_missing")) continue;
            hasContent = true;
            String v = e.getValue() != null ? e.getValue().toString().trim() : "";
            if (v.isEmpty()) continue;
            // 有实际数据（非空信号）
            if (!isResultEmptyValue(v)) {
                hasData = true;
                break;
            }
        }
        return hasContent && !hasData;
    }

    /** 判断单个结果值是否为空 */
    private static boolean isResultEmptyValue(String v) {
        return v.equals("[]")
                || v.equals("无有效工具执行结果")
                || v.contains("\"rows\": 0")
                || v.contains("0 rows returned")
                || v.contains("未匹配到数据")
                || v.contains("【结果】[]");  // Text2SQL 空结果格式
    }

    /**
     * 检测最近一次工具执行结果是否为空。
     * 通过 Executor 写入的 _last_result 字段判断。
     */
    private static boolean latestResultEmpty(Map<String, Object> sp) {
        if (sp == null || sp.isEmpty()) return true;
        Object last = sp.get("_last_result");
        if (last == null) return false;
        String v = last.toString().trim();
        return v.isEmpty() || isResultEmptyValue(v);
    }

    /**
     * 构造扩大搜索的重试提示，注入到 observerReport 中传给 Executor。
     */
    private static String buildRetryHint(String originalReport, int retryNum) {
        StringBuilder hint = new StringBuilder(originalReport);
        hint.append("\n\n[自动重试 ").append(retryNum).append("/2] 上一步查询返回空结果。");
        if (retryNum < 2) {
            hint.append("这是第").append(retryNum).append("次重试，必须换一个查询思路：\n");
            hint.append("- 换一个工具或用不同的参数组合重新查询\n");
            hint.append("- 使用更宽泛的条件（更短关键词、更大范围、更少过滤）\n");
            hint.append("- 禁止输出 ask_user，必须先尝试不同的查询策略");
        } else {
            hint.append("已是最后一次重试机会：\n");
            hint.append("- 如果确认无法通过任何工具获取数据，输出 ask_user 如实告知用户\n");
            hint.append("- 如果还有未尝试的查询方式，请再试一次");
        }
        return hint.toString();
    }

    /**
     * 检查工具是否真正执行过：对比 executor 执行前后的 toolCallCount。
     */
    private static boolean isToolActuallyExecuted(ReActAgentState state) {
        Map<String, Object> sp = state.scratchpad();
        if (sp == null) return false;
        Object before = sp.get("_tool_call_count_before");
        if (before == null) return false;
        int beforeCount = before instanceof Number ? ((Number) before).intValue() : 0;
        int currentCount = state.toolCallCount();
        return currentCount > beforeCount;
    }

    /**
     * LLM 判断用户回复是否与确认场景匹配。
     * 如果用户换了话题/问了无关新问题 → 返回 false → replan。
     */
    private boolean validateUserResponse(String prompt, String response) {
        String llmPrompt = String.format("""
                上一个问题：%s
                用户回复：%s

                判断用户的回复是否在回答上一个问题。
                - 如果用户在正面回答问题（选数字、给参数、确认/拒绝、追问）→ 输出 YES
                - 如果用户完全换了话题、问了无关的新问题 → 输出 NO
                只输出 YES 或 NO。""",
                prompt.length() > 300 ? prompt.substring(0, 300) : prompt,
                response.length() > 200 ? response.substring(0, 200) : response);
        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是对话匹配判断器，只输出 YES 或 NO。"),
                    UserMessage.from(llmPrompt)));
            String result = resp.aiMessage().text().trim().toUpperCase();
            log.info("Observer validation: prompt='{}', response='{}', result={}",
                    prompt.length() > 60 ? prompt.substring(0, 60) + "..." : prompt,
                    response.length() > 60 ? response.substring(0, 60) + "..." : response, result);
            return result.contains("YES");
        } catch (Exception e) {
            log.warn("Observer: LLM validation failed, defaulting to accept", e);
            return true;
        }
    }

    /**
     * 根据计划提取工具执行结果中的关键数据。
     * 不做全量翻译，只提取计划下一步需要的信息，避免污染上下文。
     */
    private String translateToolResults(Map<String, Object> sp, String planJson, String remainPlan) {
        if (sp == null || sp.isEmpty()) return "无工具执行结果";

        // 收集非内部字段的结果
        StringBuilder rawData = new StringBuilder();
        for (Map.Entry<String, Object> e : sp.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("_") || k.equals("error") || k.equals("ask_user_missing")) continue;
            String v = e.getValue() != null ? e.getValue().toString() : "";
            if (v.length() > 8000) v = v.substring(0, 8000) + "...(截断)";
            rawData.append("[").append(k).append("]: ").append(v).append("\n\n");
        }

        if (rawData.isEmpty()) return "无有效工具执行结果";

        String planContext = (planJson != null && !planJson.isEmpty())
                ? "\n## 原始计划\n" + planJson + "\n"
                : "";

        boolean hasMoreSteps = remainPlan != null && !remainPlan.isEmpty();

        String prompt = "## 工具执行结果\n" + rawData +
                planContext +
                (hasMoreSteps
                        ? "\n## 剩余步骤\n" + remainPlan + "\n"
                        : "\n## 状态\n所有计划步骤已执行完毕，即将交由 Planner 判断完整性。\n")
                + "\n根据以上信息，提取关键数据。要求：\n";

        if (hasMoreSteps) {
            prompt += "1. 聚焦剩余步骤要用到的信息，无关字段不要输出，保持简洁\n";
        } else {
            prompt += "1. 汇总所有工具执行结果中的关键数据，为 Planner 完整性判断提供完整依据\n";
            prompt += "1a. 每一项工具结果都要覆盖到（如 result_1、result_2 等），不要遗漏最后一步的结果\n";
        }
        prompt += "2. 必须保留所有 ID（主键、外键），标注清楚含义（如「店铺ID: 5」），区分相似字段（如 id vs type_id）\n" +
                "3. 如果结果是 SQL 查询结果，不要重复 SQL 语句，直接说查到了什么\n" +
                "4. 结果为[]或空时，写明「未匹配到数据」\n" +
                "5. 不要添加主观判断，不要写「建议」「接下来」「还需要」\n" +
                "6. 输出控制在 300 字以内";

        String systemMsg = hasMoreSteps
                ? "你是数据提取器。聚焦剩余步骤需要的信息，只输出下一步要用的关键数据。"
                : "你是数据提取器。汇总所有工具结果中的关键数据，每一项结果都要覆盖，为后续判断提供完整依据。";

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from(systemMsg),
                    UserMessage.from(prompt)));
            return resp.aiMessage().text().trim();
        } catch (Exception e) {
            log.error("Observer translation failed", e);
            return rawData.toString().trim();
        }
    }
}
