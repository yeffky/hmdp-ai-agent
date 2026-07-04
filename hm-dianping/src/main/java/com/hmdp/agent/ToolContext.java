package com.hmdp.agent;

/**
 * 跨线程传递用户 ID 的上下文。
 * Agent 图在异步线程池中执行，ThreadLocal（UserHolder）会丢失。
 * ExecutorNode 在执行工具前将 userId 设置到此上下文，工具通过它读取。
 *
 * <p>不替代 UserHolder —— 直接 HTTP 请求仍走 UserHolder，ToolContext 仅作为异步场景的补充通道。</p>
 */
public final class ToolContext {

    private ToolContext() {}

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
