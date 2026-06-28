package com.hmdp.agent.memory.context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户画像 Store — 跨会话持久化的用户记忆，存储于 PostgreSQL。
 *
 * 表结构：tb_user_profile
 *   user_id        BIGINT PRIMARY KEY
 *   profile_json   JSONB    用户画像字段
 *   last_updated   TIMESTAMP
 *   total_sessions INT      累计会话数
 *
 * 写入方式：异步线程池，不阻塞主流程。
 */
@Component
public class UserStore {

    private static final Logger log = LoggerFactory.getLogger(UserStore.class);

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("postgresJdbcTemplate")
    private JdbcTemplate jdbc;

    private final Gson gson = new GsonBuilder().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "userstore-pg-writer");
        t.setDaemon(true);
        return t;
    });

    /**
     * 获取用户画像
     */
    public Profile getProfile(Long userId) {
        Profile profile = new Profile();
        if (jdbc == null) return profile;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT profile_json, last_updated, total_sessions FROM tb_user_profile WHERE user_id = ?",
                    userId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                Object jsonObj = row.get("profile_json");
                if (jsonObj != null) {
                    String json = jsonObj.toString();
                    Map<String, Object> fields = gson.fromJson(json, MAP_TYPE);
                    profile.fields = fields != null ? fields : new LinkedHashMap<>();
                }
                Object ts = row.get("last_updated");
                if (ts != null) {
                    profile.lastUpdated = ts instanceof java.sql.Timestamp
                            ? ((java.sql.Timestamp) ts).getTime() : 0L;
                }
                Object sessions = row.get("total_sessions");
                if (sessions instanceof Number) {
                    profile.totalSessions = ((Number) sessions).intValue();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get profile for userId={}", userId, e);
        }
        return profile;
    }

    /**
     * 异步写入/更新用户画像（从压缩摘要中提取的字段）
     */
    public void updateProfileAsync(Long userId, Map<String, Object> updates) {
        if (userId == null || updates == null || updates.isEmpty() || jdbc == null) return;

        executor.submit(() -> {
            try {
                // 读取现有画像，合并新字段
                Profile existing = getProfile(userId);
                existing.fields.putAll(updates);
                existing.lastUpdated = System.currentTimeMillis();
                existing.totalSessions++;

                String json = gson.toJson(existing.fields);

                jdbc.update(
                    "INSERT INTO tb_user_profile (user_id, profile_json, last_updated, total_sessions) " +
                    "VALUES (?, ?::jsonb, ?, ?) " +
                    "ON CONFLICT (user_id) DO UPDATE SET " +
                    "  profile_json = EXCLUDED.profile_json, " +
                    "  last_updated = EXCLUDED.last_updated, " +
                    "  total_sessions = tb_user_profile.total_sessions + 1",
                    userId, json,
                    new java.sql.Timestamp(existing.lastUpdated),
                    existing.totalSessions
                );

                log.debug("UserStore updated for userId={}: {} fields", userId, updates.size());
            } catch (Exception e) {
                log.error("Failed to update UserStore for userId={}", userId, e);
            }
        });
    }

    /**
     * 将画像注入 prompt（用于 PlannerNode / AnswerNode）
     */
    public String toPromptContext(Long userId) {
        Profile profile = getProfile(userId);
        if (profile.fields.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 用户画像\n");
        for (Map.Entry<String, Object> e : profile.fields.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    // ======== 数据类 ========

    public static class Profile {
        private Map<String, Object> fields = new LinkedHashMap<>();
        private long lastUpdated;
        private int totalSessions;

        public Map<String, Object> getFields() { return fields; }
        public void setFields(Map<String, Object> fields) { this.fields = fields; }
        public long getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
        public int getTotalSessions() { return totalSessions; }
        public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }
    }
}
