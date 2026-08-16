package com.hmdp.agent.graph.error;

/**
 * 工具层异常 — 用于替代工具返回错误字符串的模式。
 *
 * <p>工具遇到不可恢复的错误时抛出此异常（或其子类），ToolExecutor 的 catch 块
 * 捕获后通过 {@link ErrorClassifier#classifyException} 进行路由。
 * 工具的 return 字符串始终视为正常业务结果。</p>
 */
public class ToolException extends RuntimeException {

    private final String toolName;

    public ToolException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }

    public ToolException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
