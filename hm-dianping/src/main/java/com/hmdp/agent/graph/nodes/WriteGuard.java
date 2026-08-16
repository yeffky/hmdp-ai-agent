package com.hmdp.agent.graph.nodes;

import java.util.Set;

/**
 * 写操作程序级确认：识别写操作工具。
 *
 * <p>将「写操作必须人工确认」从 prompt 规则升级为代码强制（P0 安全）。
 * 用户确认挂起时的「确认/取消/修改参数」意图判断由 LLM 完成（见 {@link AgentNode#resolveWriteIntent}），
 * 确认卡片的展示规范（字段/按钮）由对应 skill 的 references 定义（见 queue skill），此处不做文案硬编码。
 */
public final class WriteGuard {

    /** 写操作工具（会修改业务数据的） */
    private static final Set<String> WRITE_TOOLS = Set.of("takeQueueNumber", "cancelMyQueue");

    private WriteGuard() {
    }

    public static boolean isWriteOperation(String toolName) {
        return toolName != null && WRITE_TOOLS.contains(toolName);
    }
}
