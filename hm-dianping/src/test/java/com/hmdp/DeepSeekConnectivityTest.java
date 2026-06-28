package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek API 连通性 & 请求格式测试。
 * 不依赖 Spring 上下文，直接发送 HTTP 请求验证。
 */
public class DeepSeekConnectivityTest {

    // ======== 改成你的实际配置 ========
    private static final String API_KEY = "sk-d1c8bee44c2e438592ef8b19ec8cf4e6";
    private static final String MODEL = "deepseek-chat";

    private final RestTemplate rt = new RestTemplate();

    /** 测试1：直连 DeepSeek */
    @Test
    public void testDirectDeepSeek() {
        String url = "https://api.deepseek.com/chat/completions";

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);
        body.put("messages", List.of(
                Map.of("role", "user", "content", "hi")
        ));
        body.put("max_tokens", 20);
        body.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(API_KEY);

        System.out.println("=== 直连 DeepSeek ===");
        System.out.println("URL: " + url);
        System.out.println("Model: " + MODEL);
        System.out.println("API Key prefix: " + API_KEY.substring(0, Math.min(8, API_KEY.length())) + "...");
        System.out.println("Body: " + body);

        try {
            ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            System.out.println("Status: " + resp.getStatusCodeValue());
            System.out.println("Response: " + resp.getBody());
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            dumpHttpError(e);
        }
    }

    /** 测试2：通过本地代理 */
    @Test
    public void testViaProxy() {
        String url = "http://localhost:8081/api/deepseek-proxy/chat/completions";

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);
        body.put("messages", List.of(
                Map.of("role", "user", "content", "hi")
        ));
        body.put("max_tokens", 20);
        body.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        System.out.println("=== 通过代理 ===");
        System.out.println("URL: " + url);

        try {
            ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            System.out.println("Status: " + resp.getStatusCodeValue());
            System.out.println("Response: " + resp.getBody());
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            dumpHttpError(e);
        }
    }

    /** 测试3：LangChain4j OpenAiChatModel 模拟请求格式 */
    @Test
    public void testOpenAiFormat() {
        String url = "https://api.deepseek.com/chat/completions";

        // 模拟 LangChain4j 发出的请求格式
        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是任务规划器。只输出JSON。"),
                Map.of("role", "user", "content", "分析此查询，判断简单/复杂。\n用户: 你好")
        ));
        body.put("max_tokens", 2000);
        body.put("temperature", 0.7);
        body.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(API_KEY);

        System.out.println("=== 模拟 LangChain4j 请求 ===");
        System.out.println("Body keys: " + body.keySet());

        try {
            ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            System.out.println("Status: " + resp.getStatusCodeValue());
            System.out.println("Response: " + resp.getBody());
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            dumpHttpError(e);
        }
    }

    private void dumpHttpError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.springframework.web.client.HttpStatusCodeException) {
                org.springframework.web.client.HttpStatusCodeException he =
                        (org.springframework.web.client.HttpStatusCodeException) cause;
                System.err.println("  HTTP Status: " + he.getRawStatusCode());
                System.err.println("  Response: " + he.getResponseBodyAsString());
            }
            cause = cause.getCause();
        }
    }
}
