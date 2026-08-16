package com.hmdp.agent.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.graph.NodeNames;
import com.hmdp.agent.graph.state.AnswerMode;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Answer Node — 组装 streaming prompt，设置 __STREAMING__ 标记。
 * 实际的流式 LLM 调用由 Controller 层完成，
 * 这样 SSE token 能直接推送到前端。
 */
public class AnswerNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(AnswerNode.class);
    private final ChatModel model;

    public AnswerNode(ChatModel model) {
        this.model = model;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        // 写操作确认 / 选项选择挂起：HITL 原地暂停——两种挂起的处理不同：
        // - 写确认（takeQueueNumber 等）：只发确认卡片，不生成文本（图直接 END，Controller 发卡片+按钮）；
        // - 选项选择（askUserToChoose）：先生成一段文字性叙述（流式），Controller 叙述完成后补发选择按钮。
        boolean pendingWrite = state.pendingWrite() != null && !state.pendingWrite().isEmpty();
        boolean pendingChoose = state.pendingOptions() != null && !state.pendingOptions().isEmpty();
        if (pendingWrite) {
            log.info("Answer: HITL pending (write), pausing in place");
            return Map.of(StateKeys.NEXT_NODE, NodeNames.END);
        }
        if (pendingChoose) {
            log.info("Answer: HITL pending (choose), generating narration");
            return Map.of(
                    StateKeys.STREAMING_PROMPT, buildNarrationPrompt(state),
                    StateKeys.FINAL_ANSWER, StateKeys.SENTINEL_STREAMING,
                    StateKeys.NEXT_NODE, NodeNames.END);
        }

        String presetAnswer = state.finalAnswer();

        switch (AnswerMode.fromFinalAnswer(presetAnswer)) {
            case STREAMING:
                // Planner 已设置 streamingPrompt（如 replan 上限），直接透传
                log.info("Answer: streaming prompt already set by upstream, passing through");
                return Map.of(StateKeys.NEXT_NODE, NodeNames.END);
            case ERROR:
                // Executor 触发的错误 → 让 LLM 生成用户友好的脱敏回答
                return buildErrorAnswer(state);
            case PRESET:
                // ask_user / 写操作确认等预设回答 → 仍走真流式管线：
                // 让 LLM 逐字复述预设文本（保持打字机效果，且不改变交互语义）
                log.info("Answer (preset → streaming replay): {}", presetAnswer.length() > 100
                        ? presetAnswer.substring(0, 100) + "..." : presetAnswer);
                String replayPrompt = "请逐字、原样输出下面的预设回复内容（不要改写、不要增删、不要加任何解释或说明）：\n\n"
                        + presetAnswer;
                return Map.of(
                        StateKeys.STREAMING_PROMPT, replayPrompt,
                        StateKeys.FINAL_ANSWER, StateKeys.SENTINEL_STREAMING,
                        StateKeys.NEXT_NODE, NodeNames.END);
            default:
                break; // GENERATE：下方组装 streaming prompt
        }

        // 正常路径：组装纯规则段。用户问题/查询条件/工具结果由 Controller 从 trimmed messages 注入，
        // streamingPrompt 只承载回答规则（Markdown/脱敏/卡片占位符/展示要求）——抛弃 scratchpad 数据面
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于「用户问题」与上下文中的工具查询结果友好回答。不足则坦诚告知。\n");
        // 安全网：messages 意外为空（绕过消息通道的路径）时，回退 roundEvidence 内联工具结果
        if (state.messages() == null || state.messages().isEmpty()) {
            String report = state.roundEvidence();
            if (report != null && !report.isEmpty()) {
                prompt.append("\n## 收集信息（安全网回退）\n").append(report).append("\n");
            }
        }
        // 写操作/选项挂起已在 apply 开头原地暂停（PENDING → END），走到 GENERATE 的只有普通最终回答
        // 通用回答规则；skill 相关规则（如 shop 的 [[id]] 卡片占位符）已外置到对应 skill 的「回答规则」段，
        // 由 AnswerInjection 注入 skill 全貌时带出
        prompt.append("重要规则：\n");
            prompt.append("- **Markdown 排版**（加粗/列表/换行，前端已支持渲染），不用标题层级（#）、代码块、链接。\n");
            prompt.append("- 告知「没有/未找到」时必须说明查询范围与条件（如「5公里内」「关键词为海底捞」）；可简述查到几条、匹配了什么条件。\n");
            prompt.append("- **评价/排名/清单等列表类信息全量列出**，不要只挑 2-3 条——用户要完整信息（数据已全量提供）。\n");
            prompt.append("- 用具体数值代替「附近/周边」（半径、距离、人均等）。\n");
            prompt.append("- 涉及店铺时**不要输出任何标记/编号/占位符/ID**（如 `[[...]]`、`[1]`、`（编号123）`）——店铺卡片由系统按 Agent 的 showCards 声明渲染，正文只写店名 + 一句亮点即可\n");
            prompt.append("- 禁止暴露 SQL、表名、工具名、API 参数名。\n");
            prompt.append("- 只回答当前问题，不要索要与当前问题无关的信息");

        String promptStr = prompt.toString();
        log.info("Answer prompt assembled ({} chars), signaling streaming", promptStr);

        // 流式回答：标记 __STREAMING__，Controller 负责流式生成 + 追加本轮 Q&A 到 checkpoint
        return Map.of(
                StateKeys.STREAMING_PROMPT, promptStr,
                StateKeys.FINAL_ANSWER, StateKeys.SENTINEL_STREAMING,
                StateKeys.NEXT_NODE, NodeNames.END
        );
    }

    /**
     * 选项选择的叙述 prompt：基于 agent 决策文本（confirmationPrompt，即 ai.text + 工具 prompt）
     * 与选项清单，生成一段 2-4 句的推荐/介绍性文字，引导用户选择；由 Controller 流式输出，
     * 完成后补发选择按钮（叙述不持久化、不进 checkpoint——它只是选择前的介绍语）。
     */
    private String buildNarrationPrompt(ReActAgentState state) {
        StringBuilder sb = new StringBuilder();
        String hint = state.confirmationPrompt();
        if (hint != null && !hint.isBlank()) {
            sb.append("以下是你已经向用户给出的推荐/说明：\n").append(hint).append("\n\n");
        }
        String labels = optionLabels(state.pendingOptions());
        if (!labels.isBlank()) {
            sb.append("候选内容概要（仅作你的上下文，叙述中不要重复罗列）：\n").append(labels).append("\n\n");
        }
        sb.append("任务：把以上内容组织成一段自然、口语化的叙述文字（2-4 句），")
          .append("向用户说明你为他找到了什么、各自亮点是什么，以引导用户做出选择的语气收尾。\n")
          .append("要求：\n")
          .append("- 直接输出叙述正文，不要输出「选项列表」「编号」「按钮」等字面内容，不要以问句结尾；\n")
          .append("- 保持与已给出推荐文字一致的信息与风格；\n")
          .append("- 这是用户做出选择前的介绍语，不是最终回答，不要给出结论性建议。");
        return sb.toString();
    }

    /** 从 pendingOptions JSON 提取选项 label 摘要（{label,value} 或字符串）。 */
    private static String optionLabels(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return "";
        try {
            JSONArray arr = JSONUtil.parseArray(optionsJson);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                Object o = arr.get(i);
                if (o instanceof JSONObject jo) {
                    sb.append("- ").append(jo.getStr("label", jo.getStr("value", ""))).append("\n");
                } else if (o instanceof String s) {
                    sb.append("- ").append(s).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 错误场景：返回流式生成 prompt（由 Controller 的 doStreamingAnswer 用 LLM 流式生成友好、脱敏回答）。
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

        log.info("Answer (error, streaming): queryLen={}", query.length());
        return Map.of(
                StateKeys.STREAMING_PROMPT, prompt,
                StateKeys.FINAL_ANSWER, StateKeys.SENTINEL_STREAMING,
                StateKeys.NEXT_NODE, NodeNames.END);
    }
}
