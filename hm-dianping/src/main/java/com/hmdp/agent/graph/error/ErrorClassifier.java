package com.hmdp.agent.graph.error;

import java.util.Set;

/**
 * 工具错误分类器 —— 将异常/字符串结果映射到 {@link ErrorCategory}。
 *
 * <h3>分类策略</h3>
 * <ul>
 *   <li><b>RETRYABLE</b>：网络超时、连接重置、Redis 不可用、查询超时</li>
 *   <li><b>USER_FIXABLE</b>：缺少必填参数、需登录授权</li>
 *   <li><b>FATAL</b>：SQL 错误、schema 不匹配、认证失败、未知异常</li>
 * </ul>
 *
 * <p>参照 AutoGPT 的两轴分类（HTTP 4xx=fatal / 5xx=retryable）和 LangChain4j
 * {@code ToolExecutionErrorHandler} 的模式。</p>
 */
public final class ErrorClassifier {

    private ErrorClassifier() {}

    // ======== RETRYABLE 关键词 ========

    private static final Set<String> RETRYABLE_CLASSES = Set.of(
            "TimeoutException", "SocketTimeoutException", "ConnectException",
            "RedisException", "RedisConnectionFailureException", "QueryTimeoutException"
    );

    private static final Set<String> RETRYABLE_MSGS = Set.of(
            "timeout", "timed_out", "超时", "connection reset", "connection refused",
            "broken pipe", "rate limit", "限流", "too many requests",
            "temporarily unavailable", "暂时不可用", "无法连接"
    );

    // ======== USER_FIXABLE 关键词 ========

    private static final Set<String> USER_FIXABLE_CLASSES = Set.of(
            "IllegalArgumentException"
    );

    private static final Set<String> USER_FIXABLE_MSGS = Set.of(
            "未登录", "login required", "permission denied", "权限不足",
            "required parameter", "missing parameter", "invalid parameter",
            "缺少参数", "缺失参数", "参数缺失", "参数不正确", "参数错误",
            "参数不能为空", "必须提供", "必须指定", "不确定"
    );

    // ======== FATAL 关键词 ========

    private static final Set<String> FATAL_CLASSES = Set.of(
            "SQLException", "DataAccessException", "DataIntegrityViolationException",
            "AuthenticationException", "OutOfMemoryError"
    );

    private static final Set<String> FATAL_MSGS = Set.of(
            "syntax error", "constraint violation", "unauthorized",
            "OutOfMemory", "column", "does not exist", "schema", "权限"
    );

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
            String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";

            // 1. 按异常类名匹配
            for (String retryClass : RETRYABLE_CLASSES) {
                if (className.contains(retryClass)) {
                    return ErrorCategory.RETRYABLE;
                }
            }
            for (String ufClass : USER_FIXABLE_CLASSES) {
                if (className.contains(ufClass)) {
                    // IllegalArgumentException 需结合消息判断：参数相关 → USER_FIXABLE，其他 → FATAL
                    if (className.contains("IllegalArgument")) {
                        if (message.contains("parameter") || message.contains("参数")
                                || message.contains("required") || message.contains("必须")) {
                            return ErrorCategory.USER_FIXABLE;
                        }
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

            // 2. 按消息关键词匹配
            // USER_FIXABLE 优先（参数缺失常伴着 timeout/connection 等词，但本质是用户能修正的）
            for (String kw : USER_FIXABLE_MSGS) {
                if (message.contains(kw)) {
                    return ErrorCategory.USER_FIXABLE;
                }
            }
            for (String kw : RETRYABLE_MSGS) {
                if (message.contains(kw)) {
                    return ErrorCategory.RETRYABLE;
                }
            }
            for (String kw : FATAL_MSGS) {
                if (message.contains(kw)) {
                    return ErrorCategory.FATAL;
                }
            }

            current = current.getCause();
        }

        return ErrorCategory.FATAL;
    }

    /**
     * 针对工具自行 catch 并返回的字符串结果进行分类。
     *
     * @param result 工具返回的字符串结果
     * @return 错误分类，如果结果不是错误（正常结果/空结果）则返回 null
     */
    public static ErrorCategory classifyToolResult(String result) {
        if (result == null || result.isEmpty()) return null;

        // 先判断是否真的是错误结果
        boolean isError = result.contains("失败") || result.contains("异常")
                || result.contains("错误") || result.toLowerCase().contains("error:");

        if (!isError) return null;

        String lower = result.toLowerCase();

        // RETRYABLE 模式
        for (String kw : RETRYABLE_MSGS) {
            if (lower.contains(kw)) return ErrorCategory.RETRYABLE;
        }
        if (lower.contains("redis") || lower.contains("连接")) {
            return ErrorCategory.RETRYABLE;
        }

        // USER_FIXABLE 模式（登录 / 权限 / 参数缺失）
        for (String kw : USER_FIXABLE_MSGS) {
            if (lower.contains(kw)) return ErrorCategory.USER_FIXABLE;
        }
        if (result.contains("未登录") || result.contains("登录") || result.contains("不确定")) {
            return ErrorCategory.USER_FIXABLE;
        }
        // 参数相关的兜底：错误结果中包含"参数"关键词
        if (result.contains("参数")) {
            return ErrorCategory.USER_FIXABLE;
        }

        // FATAL 模式
        for (String kw : FATAL_MSGS) {
            if (lower.contains(kw)) return ErrorCategory.FATAL;
        }
        if (lower.contains("sql") || lower.contains("database")) {
            return ErrorCategory.FATAL;
        }

        // 默认保守策略：未知错误 → FATAL
        return ErrorCategory.FATAL;
    }
}
