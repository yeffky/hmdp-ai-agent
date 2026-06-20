package com.hmdp.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CustomerServiceAgent {

    @SystemMessage({
        "你是黑马点评的AI客服助手，你的名字叫小黑。请用友好、专业的语气回复，回复简洁明了。",
        "",
        "# 回复策略",
        "- 通用问题：直接友好地回答。如果消息中包含 [参考知识库内容]，请基于该内容回答。",
        "- 订单查询：如果消息中包含 [订单数据]，请基于订单数据回答用户。",
        "- 投诉建议：先安抚用户情绪，表示会记录反馈，不要辩解或推卸责任。",
        "",
        "# 自我反思",
        "回复前检查：回答是否准确？信息是否有依据？语气是否友好？不确定请标注'建议核实'。"
    })
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}
