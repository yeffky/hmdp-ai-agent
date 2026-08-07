package com.hmdp.agent.graph.error;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 工具错误分类器 —— 将异常映射到 {@link ErrorCategory}。
 *
 * <h3>分类策略（regex-first）</h3>
 * <ol>
 *   <li><b>消息正则优先</b>：按异常消息内容匹配关键词（内容信号最可靠，预编译 Pattern 一次构建）</li>
 *   <li><b>类名兜底</b>：消息无信号时按异常类名匹配（如 TimeoutException、ToolParamException）</li>
 *   <li>整条 cause chain 逐层检查，都不命中返回 FATAL</li>
 * </ol>
 *
 * <p>参照 AutoGPT 的两轴分类（HTTP 4xx=fatal / 5xx=retryable）和 LangChain4j
 * {@code ToolExecutionErrorHandler} 的模式。</p>
 */
public final class ErrorClassifier {

    private ErrorClassifier() {}

    // ======== RETRYABLE ========

    private static final Set<String> RETRYABLE_CLASSES = Set.of(
            "TimeoutException", "SocketTimeoutException", "ConnectException",
            "RedisException", "RedisConnectionFailureException", "QueryTimeoutException"
    );

    private static final Set<String> RETRYABLE_MSGS = Set.of(
            "timeout", "timed_out", "超时", "connection reset", "connection refused",
            "broken pipe", "rate limit", "限流", "too many requests",
            "temporarily unavailable", "暂时不可用", "无法连接"
    );

    // ======== USER_FIXABLE ========

    private static final Set<String> USER_FIXABLE_CLASSES = Set.of(
            "IllegalArgumentException", "ToolParamException"
    );

    private static final Set<String> USER_FIXABLE_MSGS = Set.of(
            "未登录", "login required", "permission denied", "权限不足",
            "required parameter", "missing parameter", "invalid parameter",
            "缺少参数", "缺失参数", "参数缺失", "参数不正确", "参数错误",
            "参数不能为空", "必须提供", "必须指定", "不确定"
    );

    // ======== FATAL ========

    private static final Set<String> FATAL_CLASSES = Set.of(
            "SQLException", "DataAccessException", "DataIntegrityViolationException",
            "AuthenticationException", "OutOfMemoryError"
    );

    private static final Set<String> FATAL_MSGS = Set.of(
            "syntax error", "constraint violation", "unauthorized",
            "OutOfMemory", "column", "does not exist", "schema", "权限"
    );

    // ======== 预编译正则（regex-first 关键：一次构建，逐条匹配） ========

    private static final Pattern USER_FIXABLE_PATTERN = compile(USER_FIXABLE_MSGS);
    private static final Pattern RETRYABLE_PATTERN = compile(RETRYABLE_MSGS);
    private static final Pattern FATAL_PATTERN = compile(FATAL_MSGS);

    private static Pattern compile(Set<String> keywords) {
        String joined = keywords.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile(joined, Pattern.CASE_INSENSITIVE);
    }

    // ================================================================
    // 公开 API
    // ================================================================

    /**
     * 针对未捕获异常（或工具内部的 cause chain）进行分类。
     *
     * @param throwable 原始异常
     * @return 对应的错误分类，无法识别时返回 FATAL
     */
    public static ErrorCategory classifyException(Throwable throwable) {
        if (throwable == null) return ErrorCategory.FATAL;

        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getSimpleName();
            String message = current.getMessage() != null ? current.getMessage() : "";

            // 1. 消息正则优先：内容信号最可靠
            Matcher m = USER_FIXABLE_PATTERN.matcher(message);
            if (m.find()) return ErrorCategory.USER_FIXABLE;
            if (RETRYABLE_PATTERN.matcher(message).find()) return ErrorCategory.RETRYABLE;
            if (FATAL_PATTERN.matcher(message).find()) return ErrorCategory.FATAL;

            // 2. 类名兜底：消息无信号时（如空消息的 TimeoutException）
            for (String retryClass : RETRYABLE_CLASSES) {
                if (className.contains(retryClass)) {
                    return ErrorCategory.RETRYABLE;
                }
            }
            for (String ufClass : USER_FIXABLE_CLASSES) {
                if (className.contains(ufClass)) {
                    // IllegalArgumentException 类名过于通用：仅当消息含参数类关键词时才算 USER_FIXABLE
                    if (className.contains("IllegalArgument")
                            && !(message.contains("parameter") || message.contains("参数")
                                 || message.contains("required") || message.contains("必须"))) {
                        return ErrorCategory.FATAL;
                    }
                    return ErrorCategory.USER_FIXABLE;
                }
            }
            for (String fatalClass : FATAL_CLASSES) {
                if (className.contains(fatalClass)) {
                    return ErrorCategory.FATAL;
                }
            }

            current = current.getCause();
        }

        return ErrorCategory.FATAL;
    }

}
