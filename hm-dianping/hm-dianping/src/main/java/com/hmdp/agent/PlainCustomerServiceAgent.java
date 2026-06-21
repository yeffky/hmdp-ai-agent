package com.hmdp.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 纯 LLM 客服 Agent —— 不注册任何 @Tool，仅凭内训知识回答。
 * 用于 "普通模式 (无RAG)" 对比测试。
 */
public interface PlainCustomerServiceAgent {

    @SystemMessage({
        "你是黑马点评的AI客服助手小黑。请用友好、专业的语气回复，回复简洁明了。",
        "仅凭你的内置知识回答，不要编造平台特定信息。不确定时请坦诚告知用户。"
    })
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}
