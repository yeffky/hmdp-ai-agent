package com.hmdp.agent.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 消息工具 — 从 LangGraph 标准 messages channel（List&lt;Map&lt;String,String&gt;&gt;）重建 LangChain4j 消息列表。
 *
 * <p>messages 是追加式（appender）的完整对话流：user（用户请求/响应）、assistant（决策：tool_calls 或回答）、
 * tool（工具执行结果）。AgentNode 决策时据此重建原生模型消息。
 */
public final class Transcript {

    private static final Logger log = LoggerFactory.getLogger(Transcript.class);

    private Transcript() {
    }

    /**
     * 从 messages 重建 LangChain4j 消息列表（供下一次原生模型调用）。
     *
     * @param messages 标准消息列表，元素格式：
     *                 user:    {role:user, content}
     *                 assistant: {role:assistant, toolCalls:<JSON数组>} 或 {role:assistant, content}
     *                 tool:    {role:tool, toolName, toolCallId, content, isError}
     */
    public static List<ChatMessage> rebuildMessages(List<Map<String, String>> messages) {
        List<ChatMessage> out = new ArrayList<>();
        if (messages == null) return out;
        for (Map<String, String> m : messages) {
            String role = m.get("role");
            try {
                if ("user".equals(role)) {
                    String content = m.get("content");
                    if (content != null && !content.isEmpty()) out.add(UserMessage.from(content));
                } else if ("assistant".equals(role)) {
                    String callsJson = m.get("toolCalls");
                    if (callsJson != null && !callsJson.isBlank() && !"null".equals(callsJson)) {
                        List<ToolExecutionRequest> reqs = parseToolCalls(callsJson);
                        if (!reqs.isEmpty()) out.add(AiMessage.from(reqs.toArray(new ToolExecutionRequest[0])));
                    } else if (m.get("content") != null) {
                        out.add(AiMessage.from(m.get("content")));
                    }
                } else if ("tool".equals(role)) {
                    out.add(ToolExecutionResultMessage.builder()
                            .id(m.getOrDefault("toolCallId", ""))
                            .toolName(m.getOrDefault("toolName", ""))
                            .text(m.getOrDefault("content", ""))
                            .isError(Boolean.parseBoolean(m.getOrDefault("isError", "false")))
                            .build());
                }
            } catch (Exception e) {
                log.warn("rebuildMessages skipped one message: {}", e.getMessage());
            }
        }
        return out;
    }

    public static List<ToolExecutionRequest> parseToolCalls(String json) {
        List<ToolExecutionRequest> out = new ArrayList<>();
        try {
            JSONArray arr = JSONUtil.parseArray(json);
            for (Object o : arr) {
                JSONObject jo = (JSONObject) o;
                out.add(ToolExecutionRequest.builder()
                        .id(jo.getStr("id"))
                        .name(jo.getStr("name"))
                        .arguments(jo.getStr("arguments"))
                        .build());
            }
        } catch (Exception e) {
            log.warn("parseToolCalls failed: {}", json);
        }
        return out;
    }

    /**
     * 从后往前第 n 个真实 user 消息的 index（跳过 [操作未完成]/[结果为空] 注入提醒）；找不到返回 -1。
     * 对齐 LangChain trim_messages(start_on="human")：以真实 user 消息作为轮次边界。
     */
    public static int nthRealUserIdx(List<Map<String, String>> messages, int n) {
        if (messages == null || n <= 0) return -1;
        int count = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            if ("user".equals(m.get("role"))) {
                String c = m.get("content");
                if (c != null && !c.startsWith("[操作未完成]") && !c.startsWith("[结果为空]")) {
                    count++;
                    if (count >= n) return i;
                }
            }
        }
        return -1;
    }

    /**
     * 注入层过滤（对齐 LangChain filter_messages）：就地清理孤儿消息，保证 tool_calls 协议一致。
     * <ul>
     *   <li><b>孤儿 assistant tool_calls</b>：带 tool_calls 但并非每个 tool_call_id 都有对应 tool 结果的
     *       assistant 消息（如写操作/askUserToChoose 挂起遗留、中断未执行的调用）——
     *       OpenAI 要求 tool_calls 后必须逐条 tool 响应，清之，否则 API 400；</li>
     *   <li><b>孤儿 tool 消息</b>：无对应有效 assistant tool_calls 的 tool 结果（借鉴 ClearToolUsesEdit.apply 第一步）；</li>
     *   <li><b>注入提醒 user</b>：[操作未完成]/[结果为空] 等系统注入消息。</li>
     * </ul>
     */
    public static void filterMessages(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return;
        // 1. 收集 tool 结果的 tool_call_id（已执行的调用）
        Set<String> executedToolIds = new HashSet<>();
        for (Map<String, String> m : messages) {
            if ("tool".equals(m.get("role"))) {
                String cid = m.get("toolCallId");
                if (cid != null && !cid.isBlank()) executedToolIds.add(cid);
            }
        }
        // 2. 完全配对的 assistant tool_calls：其全部 tool_call_id 都有 tool 结果 → 该调用有效
        Set<String> validCallIds = new HashSet<>();
        for (Map<String, String> m : messages) {
            if ("assistant".equals(m.get("role"))) {
                String callsJson = m.get("toolCalls");
                if (callsJson != null && !callsJson.isBlank()) {
                    List<ToolExecutionRequest> reqs = parseToolCalls(callsJson);
                    boolean allExecuted = !reqs.isEmpty();
                    for (ToolExecutionRequest r : reqs) {
                        if (r.id() == null || !executedToolIds.contains(r.id())) { allExecuted = false; break; }
                    }
                    if (allExecuted) {
                        for (ToolExecutionRequest r : reqs) validCallIds.add(r.id());
                    }
                }
            }
        }
        // 3. 倒序删除：孤儿 assistant tool_calls、孤儿 tool 消息、注入提醒 user 消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> m = messages.get(i);
            String role = m.get("role");
            if ("tool".equals(role)) {
                String callId = m.get("toolCallId");
                if (callId == null || callId.isBlank() || !validCallIds.contains(callId)) {
                    messages.remove(i); // 孤儿 tool（无有效 assistant 调用）
                }
            } else if ("assistant".equals(role)) {
                String callsJson = m.get("toolCalls");
                if (callsJson != null && !callsJson.isBlank()) {
                    List<ToolExecutionRequest> reqs = parseToolCalls(callsJson);
                    boolean valid = !reqs.isEmpty();
                    for (ToolExecutionRequest r : reqs) {
                        if (r.id() == null || !validCallIds.contains(r.id())) { valid = false; break; }
                    }
                    if (!valid) messages.remove(i); // 孤儿 assistant tool_calls（无对应 tool 结果）
                }
            } else if ("user".equals(role)) {
                String content = m.get("content");
                if (content != null && (content.startsWith("[操作未完成]") || content.startsWith("[结果为空]"))) {
                    messages.remove(i);
                }
            }
        }
    }
}
