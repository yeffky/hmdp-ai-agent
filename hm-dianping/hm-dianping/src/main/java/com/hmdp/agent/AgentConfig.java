package com.hmdp.agent;

import com.hmdp.agent.memory.MySqlChatMemory;
import com.hmdp.agent.tool.KnowledgeRetrievalTool;
import com.hmdp.agent.tool.OrderQueryTool;
import com.hmdp.mapper.ChatMessageMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.time.Duration;

@Configuration
public class AgentConfig {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.temperature:0.7}")
    private Double temperature;

    @Value("${deepseek.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${deepseek.timeout-seconds:60}")
    private Integer timeoutSeconds;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private OrderQueryTool orderQueryTool;

    @Resource
    private KnowledgeRetrievalTool knowledgeRetrievalTool;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                // 指向本地代理，代理会将 role=function → role=tool 后转发到 DeepSeek
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Bean
    public CustomerServiceAgent customerServiceAgent(OpenAiChatModel model) {
        return AiServices.builder(CustomerServiceAgent.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(sessionId -> new MySqlChatMemory(
                        sessionId.toString(), chatMessageMapper, 20))
                .tools(orderQueryTool, knowledgeRetrievalTool)
                .build();
    }
}
