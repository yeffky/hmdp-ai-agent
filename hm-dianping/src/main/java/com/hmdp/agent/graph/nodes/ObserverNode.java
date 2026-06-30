package com.hmdp.agent.graph.nodes;

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
 * Observer Node — 评估信息充足性，路由到 plan / answer / ask_user。
 * 信息不足时不再回 planner 死循环，直接让用户提供更多信息。
 */
public class ObserverNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ObserverNode.class);
    private final OpenAiChatModel model;
    private final int maxIterations;

    public ObserverNode(OpenAiChatModel model, int maxIterations) {
        this.model = model;
        this.maxIterations = maxIterations;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        int fatalCount = state.fatalErrorCount();
        int iter = state.iteration();

        // 熔断：致命错误 >= 3 次
        if (fatalCount >= 3) {
            log.warn("Circuit breaker: fatalErrorCount={}", fatalCount);
            return Map.of("nextNode", "answer",
                    "finalAnswer", "抱歉，查询服务暂时不可用，请稍后重试。");
        }
        // 熔断：超过最大迭代次数
        if (iter >= maxIterations) {
            log.warn("Max iterations reached: iter={}", iter);
            return Map.of("nextNode", "answer");
        }

        // 如果 executor 已决定需要询问用户（ask_user / 异常），直接放行
        if ("answer".equals(state.nextNode())) {
            return Map.of("nextNode", "answer");
        }

        Map<String, Object> sp = state.scratchpad();

        // 首次执行，尚无任何信息 → 交给 planner 生成计划
        if (sp.isEmpty()) {
            return Map.of("nextNode", "plan");
        }

        // 已有工具执行结果 → 评估是否足够回答
        String prompt = state.contextBlock() + "\n" +
                "用户问题: " + state.userQuery() + "\n" +
                "收集信息:\n" + PlannerNode.formatToolResults(sp) +
                "\n信息足够回答吗？够了回 'done'，不够回 'more: <缺什么>'";

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是质量检查器。只回 done 或 more:"),
                    UserMessage.from(prompt)));
            String raw = resp.aiMessage().text().trim();
            log.info("Observer (iter={}): {}", iter, raw);

            String lower = raw.toLowerCase();
            if (lower.startsWith("done") || lower.contains("足够")) {
                return Map.of("nextNode", "answer");
            }

            // 信息不足 → 优先回 planner 重新规划，让 planner 根据已有信息
            // 决定下一步（可能调用另一个工具，也可能生成 ask_user）
            String missing = raw.startsWith("more:") ? raw.substring(5).trim() : raw;

            // 已规划多次仍未解决 → 直接问用户，避免死循环
            if (iter > maxIterations / 2) {
                String question = "请提供以下信息：" + missing;
                sp.put("ask_user_missing", question);
                log.info("Observer asks user (iter={}, exhausted re-plan): {}", iter, question);
                return Map.of("scratchpad", sp,
                        "finalAnswer", question,
                        "nextNode", "answer");
            }

            // 回 context：用消息历史重建完整上下文，再交给 planner 制定下一步计划
            log.info("Observer routes to context (iter={}): missing={}", iter,
                    missing.length() > 80 ? missing.substring(0, 80) + "..." : missing);
            return Map.of("observerFeedback", missing, "nextNode", "context");
        } catch (Exception e) {
            log.error("Observer failed", e);
            return Map.of("nextNode", "answer");
        }
    }
}
