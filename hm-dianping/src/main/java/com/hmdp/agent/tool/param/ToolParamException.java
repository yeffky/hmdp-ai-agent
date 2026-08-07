package com.hmdp.agent.tool.param;

import com.hmdp.agent.graph.error.ToolException;

/**
 * 工具参数约束异常 — 参数违反约束（坐标 0,0、超范围、非法枚举等）时抛出。
 * ErrorClassifier 将其确定性分类为 USER_FIXABLE，路由到 replan/ask_user 等用户可修正流程。
 */
public class ToolParamException extends ToolException {

    public ToolParamException(String message) {
        super("param-validation", message);
    }

    public ToolParamException(String message, Throwable cause) {
        super("param-validation", message, cause);
    }
}
