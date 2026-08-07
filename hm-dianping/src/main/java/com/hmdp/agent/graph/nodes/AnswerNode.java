package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.graph.prompt.PromptTemplates;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Answer Node — 组装 streaming prompt，设置 __STREAMING__ 标记。
 * 实际的流式 LLM 调用由 Controller 层完成，
 * 这样 SSE token 能直接推送到前端。
 */
public class AnswerNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(AnswerNode.class);
    private final OpenAiChatModel model;

    public AnswerNode(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        String presetAnswer = state.finalAnswer();

        // Planner 已设置 streamingPrompt（如 replan 上限），直接透传
        if ("__STREAMING__".equals(presetAnswer)) {
            log.info("Answer: streaming prompt already set by upstream, passing through");
            return Map.of("nextNode", "__END__");
        }

        // Executor 触发的错误 → 让 LLM 生成用户友好的脱敏回答
        if ("__ERROR__".equals(presetAnswer)) {
            return buildErrorAnswer(state);
        }

        // ask_user 等预设回答直接使用
        if (presetAnswer != null && !presetAnswer.isEmpty()) {
            log.info("Answer (preset): {}", presetAnswer.length() > 100
                    ? presetAnswer.substring(0, 100) + "..." : presetAnswer);
            Map<String, String> userMsg = ReActAgentState.userMsg(state.userQuery());
            Map<String, String> aiMsg = ReActAgentState.aiMsg(presetAnswer);
            return Map.of(
                    "nextNode", "__END__",
                    "messages", Arrays.asList(userMsg, aiMsg)
            );
        }

        // 正常路径：组装 streaming prompt
        String query = state.userQuery();
        Map<String, Object> sp = state.scratchpad();

        // 提取最后一次工具调用的参数，帮助 LLM 了解查询范围
        String queryContext = "";
        Object lastTool = sp.get(StateKeys.SP_LAST_TOOL);
        Object lastArgs = sp.get(StateKeys.SP_LAST_ARGS);
        if (lastTool != null && lastArgs != null) {
            queryContext = "\n## 查询条件（最后一次工具调用）\n工具: " + lastTool
                    + "\n参数: " + lastArgs + "\n";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(state.contextBlock()).append("\n");
        prompt.append("## 用户问题\n").append(query).append("\n\n");
        prompt.append("## 收集信息\n").append(PlannerNode.formatToolResultsFull(sp)).append("\n");
        prompt.append(queryContext).append("\n");
        prompt.append("请基于以上信息友好回答。不足则坦诚告知。\n");
        prompt.append("重要规则：\n");
        prompt.append("- 当告知用户「没有」或「未找到」时，必须说明查询范围和条件（如「在5公里范围内」「搜索关键词为海底捞」），让用户知道你是怎么查的\n");
        prompt.append("- 可以适当展示脱敏后的查询结果数据（如查到几条记录、匹配了什么条件），帮助用户理解现状\n");
        prompt.append("- 避免模糊表述如「附近」「周边」，改用具体数值（半径、距离等）\n");
        prompt.append("- 禁止向用户暴露内部ID（如 shopId、typeId、userId、数据库主键等任何数字ID），用名称或描述替代\n");
        prompt.append("- 禁止向用户暴露 SQL 语句、表名、工具名、API 参数名等内部技术细节\n");
        prompt.append("- 只回答当前问题，不要索要与当前问题无关的信息");

        String promptStr = prompt.toString();
        log.info("Answer prompt assembled ({} chars), signaling streaming", promptStr.length());

        // 流式回答：标记 __STREAMING__，Controller 负责流式生成 + 追加本轮 Q&A 到 checkpoint
        return Map.of(
                "streamingPrompt", promptStr,
                "finalAnswer", "__STREAMING__",
                "nextNode", "__END__"
        );
    }

    /**
     * 错误场景：用 LLM 根据错误上下文生成用户友好、脱敏的回答。
     * 不走 streaming——错误场景通常简短，直接一次性生成。
     */
    private Map<String, Object> buildErrorAnswer(ReActAgentState state) {
        String errorContext = state.errorContext() != null ? state.errorContext() : "";
        String query = state.userQuery();

        String prompt = errorContext +
                "\n请根据以上错误信息，生成一句友好的用户回复。要求：\n" +
                "1. 用口语化中文解释发生了什么（不要暴露技术细节如SQL、表名、工具名、内部ID、异常类名）\n" +
                "2. 如果用户可以修正（如未登录、缺参数），委婉引导用户操作\n" +
                "3. 如果是系统错误，表达歉意并建议稍后重试\n" +
                "4. 保持简洁，不超过100字\n" +
                "5. 只输出给用户看的内容，不要加标题、标签、后缀";

        String answer;
        try {
            // 使用 sync 模型快速生成错误回复
            var resp = model.chat(List.of(
                    SystemMessage.from(PromptTemplates.ERROR_ANSWER_SYSTEM),
                    UserMessage.from(prompt)));
            answer = resp.aiMessage().text().trim();
        } catch (Exception e) {
            log.warn("Error answer generation failed, falling back to generic", e);
            answer = "抱歉，系统遇到了一点问题，请稍后再试。";
        }

        log.info("Answer (error): {}", answer.length() > 150 ? answer.substring(0, 150) + "..." : answer);
        Map<String, String> userMsg = ReActAgentState.userMsg(query);
        Map<String, String> aiMsg = ReActAgentState.aiMsg(answer);
        return Map.of(
                "finalAnswer", answer,
                "nextNode", "__END__",
                "messages", Arrays.asList(userMsg, aiMsg)
        );
    }
}
