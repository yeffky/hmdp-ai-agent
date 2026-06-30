package com.hmdp.rag.store;

import com.hmdp.rag.model.DocumentChunk;
import com.hmdp.rag.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Qdrant 向量数据库客户端 — 通过 REST API 操作。
 * 核心操作：建库 → 写入向量 → 相似检索。
 */
public class QdrantVectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String collection;
    private final int vectorSize;
    private final String distance;

    public QdrantVectorStore(RestTemplate restTemplate, String host, int port,
                             String collection, int vectorSize, String distance) {
        this.restTemplate = restTemplate;
        this.baseUrl = host.replaceAll("/+$", "") + ":" + port;
        this.collection = collection;
        this.vectorSize = vectorSize;
        this.distance = distance;
    }

    // ========== 集合管理 ==========

    /** 创建集合（幂等：已存在则跳过） */
    public boolean ensureCollection() {
        try {
            // 先检查是否已存在
            HttpEntity<Void> getReq = new HttpEntity<>(new HttpHeaders());
            ResponseEntity<String> getResp = restTemplate.exchange(
                    baseUrl + "/collections/" + collection,
                    HttpMethod.GET, getReq, String.class);
            if (getResp.getStatusCode().is2xxSuccessful()) {
                log.info("Qdrant 集合 {} 已存在，跳过创建", collection);
                return true;
            }
        } catch (Exception ignored) {}

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> vectors = new HashMap<>();
            vectors.put("size", vectorSize);
            vectors.put("distance", distance);

            Map<String, Object> body = new HashMap<>();
            body.put("vectors", vectors);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(baseUrl + "/collections/" + collection,
                    HttpMethod.PUT, request, String.class);
            log.info("Qdrant 集合 {} 创建成功 (dim={}, distance={})", collection, vectorSize, distance);
            return true;
        } catch (Exception e) {
            log.error("创建 Qdrant 集合失败: {}", e.getMessage());
            return false;
        }
    }

    /** 删除集合 */
    public boolean deleteCollection() {
        try {
            restTemplate.delete(baseUrl + "/collections/" + collection);
            log.info("Qdrant 集合 {} 已删除", collection);
            return true;
        } catch (Exception e) {
            log.error("删除集合失败: {}", e.getMessage());
            return false;
        }
    }

    /** 获取集合信息（用于统计） */
    public Map<String, Object> getCollectionInfo() {
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    baseUrl + "/collections/" + collection, Map.class);
            if (resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) resp.getBody().get("result");
                return result;
            }
        } catch (Exception e) {
            log.error("获取集合信息失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /** 获取 points 数量 */
    public long countPoints() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>("{}", headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/collections/" + collection + "/points/count",
                    HttpMethod.POST, req, Map.class);
            if (resp.getBody() != null) {
                Object result = resp.getBody().get("result");
                if (result instanceof Map) {
                    Object count = ((Map<?, ?>) result).get("count");
                    return count instanceof Number ? ((Number) count).longValue() : 0;
                }
            }
        } catch (Exception e) {
            log.error("统计 points 失败: {}", e.getMessage());
        }
        return 0;
    }

    // ========== 向量写入 ==========

    /** 批量写入向量（upsert） */
    public boolean upsert(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return true;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            List<Map<String, Object>> points = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                Map<String, Object> point = new HashMap<>();
                point.put("id", chunk.getId() != null ? chunk.getId() : UUID.randomUUID().toString());
                point.put("vector", floatsToList(chunk.getVector()));

                Map<String, Object> payload = new HashMap<>();
                payload.put("text", chunk.getText());
                payload.put("source", chunk.getSource());
                payload.put("title", chunk.getTitle());
                payload.put("chunk_index", chunk.getChunkIndex());
                if (chunk.getMetadata() != null) {
                    payload.put("metadata", chunk.getMetadata());
                }
                point.put("payload", payload);
                points.add(point);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("points", points);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(baseUrl + "/collections/" + collection + "/points?wait=true",
                    HttpMethod.PUT, request, String.class);
            log.info("成功写入 {} 个向量到 Qdrant", chunks.size());
            return true;
        } catch (Exception e) {
            log.error("写入 Qdrant 失败: {}", e.getMessage());
            return false;
        }
    }

    // ========== 相似检索 ==========

    /**
     * 相似检索 — 输入查询向量，返回 Top-K 相似文档片段。
     */
    public List<SearchResult> search(float[] queryVector, int topK, double scoreThreshold) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("vector", floatsToList(queryVector));
            body.put("limit", topK);
            body.put("with_payload", true);
            body.put("score_threshold", scoreThreshold);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/collections/" + collection + "/points/search",
                    HttpMethod.POST, request, Map.class);

            List<SearchResult> results = new ArrayList<>();
            if (response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> resultList =
                        (List<Map<String, Object>>) response.getBody().get("result");
                if (resultList != null) {
                    log.info("Qdrant search returned {} raw results", resultList.size());
                    for (Map<String, Object> item : resultList) {
                        String id = (String) item.get("id");
                        Double score = ((Number) item.get("score")).doubleValue();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) item.get("payload");

                        String text = payload != null ? (String) payload.get("text") : null;
                        String title = payload != null ? (String) payload.get("title") : null;
                        String source = payload != null ? (String) payload.get("source") : null;
                        log.info("Qdrant result: id={}, score={}, title={}, textLen={}, payloadKeys={}",
                                id, String.format("%.4f", score), title,
                                text != null ? text.length() : -1,
                                payload != null ? payload.keySet() : "null");

                        DocumentChunk chunk = new DocumentChunk();
                        chunk.setId(id);
                        chunk.setText(text);
                        chunk.setSource(source);
                        chunk.setTitle(title);
                        chunk.setChunkIndex(payload != null && payload.get("chunk_index") != null
                                ? ((Number) payload.get("chunk_index")).intValue() : 0);

                        results.add(new SearchResult(chunk, score));
                    }
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Qdrant 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 删除操作 ==========

    /** 按来源删除 */
    public boolean deleteBySource(String source) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> filter = new HashMap<>();
            Map<String, Object> must = new HashMap<>();
            must.put("key", "source");
            Map<String, Object> match = new HashMap<>();
            match.put("value", source);
            must.put("match", match);
            filter.put("must", Collections.singletonList(must));

            Map<String, Object> body = new HashMap<>();
            body.put("filter", filter);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(baseUrl + "/collections/" + collection + "/points/delete",
                    HttpMethod.POST, request, String.class);
            log.info("已删除 source={} 的向量", source);
            return true;
        } catch (Exception e) {
            log.error("删除向量失败: {}", e.getMessage());
            return false;
        }
    }

    // ========== 工具方法 ==========

    private List<Float> floatsToList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }
}
