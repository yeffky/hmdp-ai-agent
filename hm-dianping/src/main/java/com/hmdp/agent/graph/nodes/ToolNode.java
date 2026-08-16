package com.hmdp.agent.graph.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.graph.NodeNames;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool Node — ReAct 的「Action」节点：执行 AgentNode 决策出的工具调用。
 *
 * <p>读 {@link AgentNode#SP_PENDING_TOOL}（{tool,args,id}）→ 执行 → 结果写入转录 →
 * 空结果注入换思路提示 → 回 agent 继续决策。写操作 / askUserToChoose 已在 AgentNode 挂起，不经过这里。
 */
public class ToolNode implements NodeAction<ReActAgentState> {

    private static final Logger log = LoggerFactory.getLogger(ToolNode.class);
    private final ToolExecutor toolExecutor;

    public ToolNode(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Map<String, Object> apply(ReActAgentState state) throws Exception {
        Map<String, Object> scratchpad = state.scratchpad();
        // 标准消息通道（append 式）：工具结果作为 tool 消息追加，永不丢、可回放

        Object pending = scratchpad.get(AgentNode.SP_PENDING_TOOL);
        if (pending == null) {
            log.warn("ToolNode: no pending tool, routing back to agent");
            return Map.of(StateKeys.NEXT_NODE, NodeNames.AGENT);
        }
        JSONObject jo = JSONUtil.parseObj(pending.toString());
        String toolName = jo.getStr("tool");
        String argsStr = jo.getStr("args", "");
        String callId = jo.getStr("id");
        if (callId == null || callId.isBlank()) callId = "call_" + state.toolCallCount();

        ToolExecutionResultMessage result = toolExecutor.execute(state, toolName, argsStr, callId);
        boolean err = Boolean.TRUE.equals(result.isError());
        String resultText = result.text() != null ? result.text() : "";
        log.info("ToolNode: {} -> {} chars", toolName, resultText.length());

        // 工具结果作为 tool 消息追加到标准 messages 通道（LangGraph 标准）
        List<Map<String, String>> toAppend = new java.util.ArrayList<>();
        toAppend.add(err ? ReActAgentState.toolErrorMsg(toolName, callId, resultText)
                : ReActAgentState.toolMsg(toolName, callId, resultText));

        // ReAct 观察：空结果（确定性协议信号）→ 注入换思路提示，引导下一次模型调用换思路，禁止重复相同查询
        boolean empty = toolExecutor.latestResultEmpty(state, scratchpad);
        if (empty) {
            toAppend.add(ReActAgentState.userMsg("[结果为空] 工具 " + toolName
                    + " 未返回数据。请换一种查询思路：换工具、放宽条件、改关键词或美食细分（如用 food_category、降低评分/去掉类型限制）。禁止重复相同的查询。若确实无数据，如实告知用户并给出调整建议。"));
        }

        scratchpad.remove(AgentNode.SP_PENDING_TOOL);

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("messages", toAppend);
        delta.put("scratchpad", scratchpad);
        Map<String, Object> counters = state.counters();
        counters.put(StateKeys.TOOL_CALL_COUNT, state.toolCallCount() + 1);
        // 步骤推进不在 ToolNode：一个计划步骤可能需多次工具调用（如「取消排队」= 查排队记录 + 取消），
        // 每次调用都推进会导致前置查询把步骤错误地推到下一步。步骤推进交由 agent 按 plan 顺序 + 观察判断。
        if (empty) {
            counters.put(StateKeys.EMPTY_RESULT_RETRIES, state.emptyResultRetries() + 1);
        }
        delta.put(StateKeys.COUNTERS, counters);
        delta.put(StateKeys.NEXT_NODE, NodeNames.AGENT);
        return delta;
    }
}
