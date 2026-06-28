package com.hmdp.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI 兼容格式的 Embedding 实现。
 * 支持：Ollama / SiliconCloud / OpenAI / 任意兼容服务。
 */
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String url;
    private final String apiKey;
    private final String model;
    private int dimension;

    public OpenAiEmbeddingService(RestTemplate restTemplate, String baseUrl,
                                  String apiKey, String model, int defaultDimension) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.url = baseUrl.replaceAll("/+$", "") + "/embeddings";
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = defaultDimension;
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(Collections.singletonList(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.setBearerAuth(apiKey);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", texts);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                List<float[]> embeddings = new ArrayList<>();

                if (data != null && data.isArray()) {
                    for (JsonNode item : data) {
                        JsonNode embedding = item.get("embedding");
                        if (embedding != null && embedding.isArray()) {
                            float[] vec = new float[embedding.size()];
                            for (int i = 0; i < embedding.size(); i++) {
                                vec[i] = (float) embedding.get(i).asDouble();
                            }
                            embeddings.add(vec);
                            // 动态探测实际维度
                            if (dimension <= 0) {
                                dimension = vec.length;
                            }
                        }
                    }
                }
                return embeddings;
            }
        } catch (Exception e) {
            log.error("Embedding API 调用失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
