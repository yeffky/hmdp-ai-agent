package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.state.ReActAgentState;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Judge Node — 判断当前收集的信息是否足够回答用户问题。
 *
 * <p>职责单一：只做充分性判断，不做规划。
 * <ul>
 *   <li>信息充足 → answer</li>
 *   <li>信息不足 → planner（重新规划，且提示避免重复已失败的路径）</li>
 *   <li>需要用户补充 → answer</li>
 * </ul>
 */
public class JudgeNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(JudgeNode.class);
    private final OpenAiChatModel model;

    public JudgeNode(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        String query = state.userQuery();
        String planJson = state.planJson();
        String report = state.observerReport();
        Map<String, Object> sp = state.scratchpad();

        StringBuilder prompt = new StringBuilder();
        prompt.append(state.contextBlock()).append("\n");
        prompt.append("## 用户问题\n").append(query).append("\n\n");
        prompt.append("## 已执行的计划\n").append(planJson != null ? planJson : "无").append("\n\n");
        prompt.append("## 收集到的数据\n").append(PlannerNode.formatToolResults(sp)).append("\n\n");
        prompt.append("## 观察报告\n").append(report != null ? report : "无").append("\n\n");
        prompt.append("请判断：以上信息是否足以完整回答用户问题？输出JSON：\n");
        prompt.append("- 信息充足 → {\"verdict\": \"sufficient\"}\n");
        prompt.append("- 信息不足，需要重新规划 → {\"verdict\": \"insufficient\", \"reason\": \"缺什么/为什么当前路径走不通\"}\n");
        prompt.append("- 需要用户补充信息 → {\"verdict\": \"ask_user\", \"message\": \"需要用户补充什么\"}\n\n");
        prompt.append("注意：如果查询多次返回空结果，应考虑是否搜索方向错了，标记为 insufficient 并说明原因。");

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是信息充分性判断器。只输出JSON。"),
                    UserMessage.from(prompt.toString())));
            String raw = resp.aiMessage().text().trim();
            log.info("Judge verdict: {}", raw);

            if (raw.contains("\"sufficient\"")) {
                log.info("Judge: sufficient, routing to answer");
                return Map.of("nextNode", "answer");
            }

            if (raw.contains("\"ask_user\"")) {
                String msg = extractJsonStr(raw, "message");
                String finalMsg = msg != null ? msg : "请提供更多信息";
                log.info("Judge: ask_user, message={}", finalMsg);
                return Map.of("finalAnswer", finalMsg, "nextNode", "answer");
            }

            // insufficient → route to planner with the reason as feedback
            String reason = extractJsonStr(raw, "reason");
            String feedback = reason != null ? reason : "当前信息不足，请尝试其他查询方式";
            log.info("Judge: insufficient, routing to planner. reason={}", feedback);
            return Map.of(
                    "observerFeedback", feedback,
                    "nextNode", "planner"
            );

        } catch (Exception e) {
            log.error("Judge failed, defaulting to answer", e);
            return Map.of("nextNode", "answer");
        }
    }

    private static String extractJsonStr(String json, String key) {
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) return null;
        int s = json.indexOf("\"", i + key.length() + 3);
        if (s < 0) return null;
        int e = json.indexOf("\"", s + 1);
        return e > s ? json.substring(s + 1, e) : null;
    }
}
