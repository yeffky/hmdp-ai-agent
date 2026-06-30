package com.hmdp.agent.graph.error;

/**
 * 工具调用错误的三种分类，对应不同的状态机路由策略。
 *
 * <ul>
 *   <li><b>RETRYABLE</b> — 瞬态故障，自动重试同一工具</li>
 *   <li><b>USER_FIXABLE</b> — 缺参数/需登录，暂停等待用户补充</li>
 *   <li><b>FATAL</b> — 不可恢复的系统错误，优雅降级</li>
 * </ul>
 *
 * <p>参照 LangChain4j {@code ReturnBehavior}（TO_LLM / IMMEDIATE）和 LangGraph
 * {@code errorHandler} 的设计模式。
 */
public enum ErrorCategory {
    RETRYABLE,
    USER_FIXABLE,
    FATAL
}
