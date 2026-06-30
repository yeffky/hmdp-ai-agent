package com.hmdp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;

/**
 * 向量库后台管理 — 提供 Qdrant 集合查看/浏览的 REST 接口。
 */
@RestController
@RequestMapping("/api/qdrant/admin")
public class QdrantAdminController {

    private static final Logger log = LoggerFactory.getLogger(QdrantAdminController.class);

    @Resource
    private RestTemplate restTemplate;

    @Value("${rag.qdrant.host}")
    private String qdrantHost;
    @Value("${rag.qdrant.port}")
    private int qdrantPort;
    @Value("${rag.qdrant.collection}")
    private String collection;

    private String baseUrl() {
        return qdrantHost.replaceAll("/+$", "") + ":" + qdrantPort;
    }

    /** 集合基本信息 + 点数 */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collection", collection);
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    baseUrl() + "/collections/" + collection, Map.class);
            if (resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) resp.getBody().get("result");
                result.put("config", body);
            }
        } catch (Exception e) {
            result.put("configError", e.getMessage());
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>("{\"exact\": true}", headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/collections/" + collection + "/points/count",
                    HttpMethod.POST, req, Map.class);
            if (resp.getBody() != null) {
                result.put("pointsCount", resp.getBody().get("result"));
            }
        } catch (Exception e) {
            result.put("countError", e.getMessage());
        }
        return result;
    }

    /** 分页浏览 points */
    @GetMapping("/points")
    public Map<String, Object> points(@RequestParam(defaultValue = "0") int offset,
                                       @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("offset", offset);
            body.put("limit", Math.min(limit, 50));
            body.put("with_payload", true);
            body.put("with_vector", false);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/collections/" + collection + "/points/scroll",
                    HttpMethod.POST, request, Map.class);

            if (resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) resp.getBody().get("result");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pts = (List<Map<String, Object>>) r.get("points");
                @SuppressWarnings("unchecked")
                Object nextOffset = r.get("next_page_offset");

                List<Map<String, Object>> items = new ArrayList<>();
                if (pts != null) {
                    for (Map<String, Object> p : pts) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", p.get("id"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pl = (Map<String, Object>) p.get("payload");
                        if (pl != null) {
                            item.put("text", trimText(pl.get("text")));
                            item.put("source", pl.get("source"));
                            item.put("title", pl.get("title"));
                            item.put("chunkIndex", pl.get("chunk_index"));
                            item.put("textLen", pl.get("text") != null ? pl.get("text").toString().length() : 0);
                        }
                        items.add(item);
                    }
                }
                result.put("points", items);
                result.put("nextOffset", nextOffset);
            }
        } catch (Exception e) {
            log.error("Failed to scroll Qdrant", e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private static String trimText(Object text) {
        if (text == null) return "";
        String s = text.toString();
        if (s.length() > 200) s = s.substring(0, 200) + "...";
        return s;
    }
}
