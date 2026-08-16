package com.hmdp.agent;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AgentConfig {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    // 流式模型直连 DeepSeek（绕开本地代理）。代理用 RestTemplate 同步转发会缓冲整个
    // SSE 流，导致 token 一次性到达、前端失去流式效果；而流式调用只含纯文本（无 tool 消息），
    // 不需要代理的 role=function->tool 转换，故可安全直连。
    @Value("${deepseek.streaming-base-url:${deepseek.real-base-url:${deepseek.base-url}}}")
    private String streamingBaseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.temperature:0.7}")
    private Double temperature;

    @Value("${deepseek.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${deepseek.timeout-seconds:60}")
    private Integer timeoutSeconds;

    @Value("${agent.llm-cache-ttl-ms:1800000}")
    private long llmCacheTtlMs; // LLM 缓存 TTL（毫秒，默认 30 分钟）

    @Resource
    private LlmTraceListener llmTraceListener;

    /**
     * Agent 图执行专用线程池（替换默认 ForkJoinPool.commonPool，避免阻塞式图调用占满公共池）。
     * 有界队列 + CallerRuns 拒绝策略：队列满时由调用线程执行，保证不丢请求、不无限堆积。
     */
    @Bean("agentExecutor")
    public ExecutorService agentExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                4, 8, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                r -> {
                    Thread t = new Thread(r, "agent-graph");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /** 流式回答断点续传的指数退避重试调度器（替换每次 new Thread 的裸线程）。 */
    @Bean("agentRetryScheduler")
    public ScheduledExecutorService agentRetryScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "agent-stream-retry");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public ChatModel openAiChatModel() {
        OpenAiChatModel delegate = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .listeners(List.of(llmTraceListener))
                .build();
        // 包装 LLM 缓存：无工具调用的请求命中直接返回，减少重复调用/缓解连接抖动
        return new CachingChatModel(delegate, llmCacheTtlMs);
    }

    @Bean
    public OpenAiStreamingChatModel openAiStreamingChatModel() {
        // 强制 HTTP/1.1：JDK HttpClient 默认走 HTTP/2，流式长连接下 HTTP/2 连接被服务端/中间设备关闭
        // 会抛 Connection reset（日志 Http2Connection.shutdown），是社区已知问题（langchain4j#681）。
        // HTTP/1.1 keep-alive 在流式场景更稳定；connectTimeout 15s、readTimeout 60s（SSE token 间隔保护）。
        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1))
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(timeoutSeconds));
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(streamingBaseUrl)
                .modelName(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .httpClientBuilder(httpClientBuilder)
                .build();
    }
}
