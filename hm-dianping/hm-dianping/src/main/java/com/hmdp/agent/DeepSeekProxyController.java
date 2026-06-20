package com.hmdp.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;

/**
 * DeepSeek API 代理 —— 拦截 LangChain4j 发出的请求，将 role=function → role=tool。
 *
 * 背景：LangChain4j 0.31 将 ToolExecutionResultMessage 序列化为 role=function，
 * 但 DeepSeek API 只接受 role=tool。此代理在转发前对 JSON body 做正则替换。
 *
 * 使用方式：配置 deepseek.base-url 指向本代理：
 *   deepseek.base-url = http://localhost:8081/api/deepseek-proxy/v1
 */
@RestController
@RequestMapping("/api/deepseek-proxy")
public class DeepSeekProxyController {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProxyController.class);

    @Value("${deepseek.real-base-url:https://api.deepseek.com/v1}")
    private String realBaseUrl;

    @Value("${deepseek.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("DeepSeek 代理已启动：{} -> {}", "/api/deepseek-proxy", realBaseUrl);
    }

    /** 转发所有 DeepSeek API 请求 */
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> proxy(HttpServletRequest request, @RequestBody(required = false) String body) {
        try {
            // 构造目标 URL
            String path = request.getRequestURI().replace("/api/deepseek-proxy", "");
            String query = request.getQueryString();
            String targetUrl = realBaseUrl + path + (query != null ? "?" + query : "");

            // 准备请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // ★ 核心修复：替换 role=function → role=tool
            if (body != null && body.contains("\"role\":\"function\"")) {
                body = body.replace("\"role\":\"function\"", "\"role\":\"tool\"");
                log.debug("已转换 role=function -> role=tool");
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            HttpMethod method = HttpMethod.resolve(request.getMethod());
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, method, entity, String.class);

            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("代理转发失败: {}", e.getMessage());
            return ResponseEntity.status(502)
                    .body("{\"error\":\"代理转发失败: " + e.getMessage() + "\"}");
        }
    }
}
