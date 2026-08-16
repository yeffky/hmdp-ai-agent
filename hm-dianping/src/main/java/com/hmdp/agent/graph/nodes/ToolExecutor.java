package com.hmdp.agent.graph.nodes;

import com.hmdp.agent.ToolContext;
import com.hmdp.agent.config.ToolCacheProperties;
import com.hmdp.agent.graph.error.ErrorCategory;
import com.hmdp.agent.graph.error.ErrorClassifier;
import com.hmdp.agent.graph.state.ReActAgentState;
import com.hmdp.agent.graph.state.StateKeys;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行器 —— 执行单个工具调用，写 scratchpad 的 SP_LAST_*。
 * 被 AgentNode（写操作确认恢复）与 ToolNode（正常执行）共享；异常分类后加
 * <code>[retryable]/[user_fixable]/[fatal]</code> 标签回传，由 LLM 自纠。
 */
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private final LC4jToolService toolService;
    private final StringRedisTemplate redis;
    private final ToolCacheProperties cacheProps;

    public ToolExecutor(LC4jToolService toolService, StringRedisTemplate redis, ToolCacheProperties cacheProps) {
        this.toolService = toolService;
        this.redis = redis;
        this.cacheProps = cacheProps;
    }

    /**
     * 执行工具并返回原生 tool 结果消息（含错误标签）。
     * 成功/异常都返回 ToolExecutionResultMessage；异常被分类后加 [retryable]/[user_fixable]/[fatal] 标签。
     */
    public ToolExecutionResultMessage execute(ReActAgentState state, String toolName,
                                              String argsStr, String toolCallId) {
        Map<String, Object> scratchpad = state.scratchpad();
        // 工具结果缓存（只读白名单工具 + 相同参数）：命中直接返回，不执行工具
        String cacheKey = cacheProps.isEnabled() && cacheProps.getTools().contains(toolName)
                ? cacheKey(toolName, argsStr) : null;
        if (cacheKey != null) {
            try {
                String cached = redis.opsForValue().get(cacheKey);
                if (cached != null && !cached.isBlank()) {
                    log.info("ToolCache 命中: {} ({} chars)", toolName, cached.length());
                    writeLast(scratchpad, toolName, argsStr, cached);
                    return ToolExecutionResultMessage.builder()
                            .id(toolCallId).toolName(toolName).text(cached).build();
                }
            } catch (Exception e) {
                // Redis 不可用不应阻塞工具执行：跳过缓存走正常调用
                log.debug("ToolCache 读取失败，跳过缓存: {}", e.getMessage());
            }
        }
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName).arguments(argsStr).build();
        Long uid = state.userId();
        ToolContext.setUserId(uid);
        ToolContext.setLocation(state.userLocation());
        ToolContext.setDistrictId(state.userDistrictId());
        try {
            var execResult = toolService.execute(
                    List.of(request),
                    InvocationContext.builder().build(),
                    "toolResults").get();
            Object val = execResult != null && execResult.update() != null
                    ? execResult.update().get("toolResults") : null;
            if (val instanceof List<?> list && !list.isEmpty()) {
                Object last = list.get(list.size() - 1);
                if (last instanceof ToolExecutionResultMessage m) {
                    String text = m.text() != null ? m.text() : "工具执行完成";
                    boolean isError = Boolean.TRUE.equals(m.isError());
                    if (isError) text = ensureLabel(text, classifyText(text));
                    writeLast(scratchpad, toolName, argsStr, text);
                    if (!isError) putCache(cacheKey, text);
                    return ToolExecutionResultMessage.builder()
                            .id(toolCallId).toolName(toolName).text(text).isError(isError).build();
                }
            }
            writeLast(scratchpad, toolName, argsStr, "工具执行完成");
            putCache(cacheKey, "工具执行完成");
            return ToolExecutionResultMessage.builder()
                    .id(toolCallId).toolName(toolName).text("工具执行完成").build();
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            ErrorCategory cat = ErrorClassifier.classifyException(e);
            String labeled = ensureLabel(detail, cat);
            writeLast(scratchpad, toolName, argsStr, labeled);
            return ToolExecutionResultMessage.builder()
                    .id(toolCallId).toolName(toolName).text(labeled).isError(true).build();
        } finally {
            ToolContext.clear();
        }
    }

    /** 写入工具结果缓存（仅当结果值得缓存：非空、非错误标签、非空结果标记）。 */
    private void putCache(String cacheKey, String text) {
        if (cacheKey == null || !isCacheableResult(text)) return;
        try {
            redis.opsForValue().set(cacheKey, text, cacheProps.getTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("ToolCache 写入失败: {}", e.getMessage());
        }
    }

    /** 结果是否值得缓存：非空、非错误标签、非空结果标记（[]/rows:0/未匹配）。 */
    private boolean isCacheableResult(String text) {
        if (text == null || text.isBlank()) return false;
        if (text.startsWith("[retryable]") || text.startsWith("[user_fixable]") || text.startsWith("[fatal]")) return false;
        return !(text.equals("[]")
                || text.contains("\"rows\": 0")
                || text.contains("未匹配到数据")
                || text.contains("未找到满足条件的店铺")
                || text.contains("【结果】[]")
                || text.contains("0 rows returned"));
    }

    /** 缓存 key：agent:toolcache:{SHA256(toolName|args)}——相同参数命中，跨用户共享（白名单工具均用户无关）。 */
    private String cacheKey(String toolName, String argsStr) {
        return "agent:toolcache:" + sha256(toolName + "|" + (argsStr != null ? argsStr : ""));
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** 最新一次工具结果是否为空（[]/rows:0/未匹配到数据）。 */
    public boolean latestResultEmpty(ReActAgentState state, Map<String, Object> scratchpad) {
        Object last = scratchpad.get(StateKeys.SP_LAST_RESULT);
        if (last == null) return false;
        String v = last.toString().trim();
        return v.isEmpty() || v.equals("[]") || v.contains("\"rows\": 0")
                || v.contains("未匹配到数据") || v.contains("未找到满足条件的店铺")
                || v.contains("【结果】[]") || v.contains("0 rows returned");
    }

    private void writeLast(Map<String, Object> scratchpad, String toolName, String argsStr, String text) {
        scratchpad.put(StateKeys.SP_LAST_TOOL, toolName);
        scratchpad.put(StateKeys.SP_LAST_ARGS, argsStr);
        scratchpad.put(StateKeys.SP_LAST_RESULT, truncate(text, 500));
        scratchpad.put(StateKeys.SP_LAST_RESULT_FULL, text);
    }

    /** 给错误文本补 [category] 标签（已带则保留）。 */
    private static String ensureLabel(String text, ErrorCategory cat) {
        if (text == null) text = "工具执行失败";
        if (text.startsWith("[retryable]") || text.startsWith("[user_fixable]") || text.startsWith("[fatal]")) {
            return text;
        }
        return "[" + (cat != null ? cat.name().toLowerCase() : "fatal") + "] " + text;
    }

    /** 从错误文本推断类别（用于工具返回 isError 但无异常对象时）。 */
    private static ErrorCategory classifyText(String text) {
        String t = text != null ? text.toLowerCase() : "";
        if (t.contains("timeout") || t.contains("timed out") || t.contains("连接")
                || t.contains("connection") || t.contains("refused") || t.contains("socket")) {
            return ErrorCategory.RETRYABLE;
        }
        if (t.contains("缺少") || t.contains("参数") || t.contains("必填")
                || t.contains("invalid") || t.contains("required") || t.contains("未登录")
                || t.contains("login") || t.contains("没有登录")) {
            return ErrorCategory.USER_FIXABLE;
        }
        return ErrorCategory.FATAL;
    }

    public static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
