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
 * Answer Node — 基于 scratchpad 生成最终回答。
 * 同时将本轮 Q&A 追加到消息历史。
 * 实现 NodeAction<ReActAgentState>，类型安全。
 */
public class AnswerNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(AnswerNode.class);
    private final OpenAiChatModel model;

    public AnswerNode(OpenAiChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        // 如果 Executor 已设置了 finalAnswer（ask_user / 工具异常），直接使用，不再调 LLM
        String presetAnswer = state.finalAnswer();
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

        String query = state.userQuery();
        Map<String, Object> sp = state.scratchpad();

        StringBuilder prompt = new StringBuilder();
        prompt.append(state.contextBlock()).append("\n");
        prompt.append("## 用户问题\n").append(query).append("\n\n");
        prompt.append("## 收集信息\n").append(PlannerNode.formatToolResults(sp)).append("\n\n");
        prompt.append("请基于以上信息友好回答。不足则坦诚告知。");

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是黑马点评AI客服小黑。友好、专业、简洁。"),
                    UserMessage.from(prompt.toString())));
            String answer = resp.aiMessage().text();
            log.info("Answer: {}", answer.length() > 100 ? answer.substring(0, 100) + "..." : answer);

            // 将本轮 Q&A 追加到消息历史（带时间戳标识）
            Map<String, String> userMsg = ReActAgentState.userMsg(query);
            Map<String, String> aiMsg = ReActAgentState.aiMsg(answer);

            return Map.of(
                    "finalAnswer", answer,
                    "nextNode", "__END__",
                    "messages", Arrays.asList(userMsg, aiMsg)
            );
        } catch (Exception e) {
            log.error("Answer failed", e);
            return Map.of("finalAnswer", "抱歉，处理出错，请稍后重试。",
                    "nextNode", "__END__");
        }
    }
}
