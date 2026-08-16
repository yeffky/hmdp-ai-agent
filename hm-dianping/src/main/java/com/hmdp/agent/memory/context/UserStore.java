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
 * <p>每个字段携带时间戳，合并时遵循"近期优先"原则：
 * 新值的时间戳更新则覆盖，旧值的时间戳更新则保留。
 *
 * <p>存储格式（JSONB）：
 * <pre>
 * {
 *   "偏好口味": {"v": "辣", "t": 1719000000},
 *   "常用地址": {"v": "北京市", "t": 1719000100}
 * }
 * </pre>
 * 向后兼容旧格式（plain value 视为 t=0）。
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

    /** 字段值 + 时间戳 */
    public static class FieldEntry {
        public Object v;
        public long t;

        public FieldEntry() {}
        public FieldEntry(Object v, long t) { this.v = v; this.t = t; }

        static FieldEntry fromRaw(Object raw) {
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                Object v = m.get("v");
                Object t = m.get("t");
                return new FieldEntry(v, t instanceof Number ? ((Number) t).longValue() : 0L);
            }
            // 旧格式：plain value，时间戳默认为 0
            return new FieldEntry(raw, 0L);
        }

        Map<String, Object> toRaw() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("v", v);
            m.put("t", t);
            return m;
        }
    }

    /**
     * 用户画像固定字段 schema：key → 中文取值说明。
     * 画像提取 LLM 只能输出这些 key（同名覆盖，避免自由 key 导致画像无限膨胀）；
     * 只保留稳定偏好，动态事实（订单/排队/近期查询）走对话历史/摘要，不长期入库。
     */
    public static final Map<String, String> PROFILE_SCHEMA = new LinkedHashMap<>();
    static {
        PROFILE_SCHEMA.put("taste", "口味偏好（如 辣味/清淡/甜口）");
        PROFILE_SCHEMA.put("categories", "兴趣品类（数组，如 [\"火锅\",\"川菜\",\"KTV\"]）");
        PROFILE_SCHEMA.put("budget", "预算/性价比倾向（如 性价比高、人均50以内）");
        PROFILE_SCHEMA.put("scorePref", "评分偏好（如 高评分≥4.5）");
        PROFILE_SCHEMA.put("services", "关注服务（数组，如 [\"宠物友好\",\"停车\",\"儿童友好\"]）");
        PROFILE_SCHEMA.put("partySize", "常用用餐人数（数字）");
        PROFILE_SCHEMA.put("diningTime", "常用用餐时段（如 晚上8点多）");
        PROFILE_SCHEMA.put("diningScene", "常用用餐场景（如 请朋友聚餐、二人约会）");
        PROFILE_SCHEMA.put("location", "常用位置/区域（如 福州鼓楼区东街口）");
        PROFILE_SCHEMA.put("hasPet", "是否可能携带宠物（true/false）");
        PROFILE_SCHEMA.put("membership", "会员等级（未知则不填）");
        PROFILE_SCHEMA.put("avoid", "用户想避免的内容（数组，如 [\"排队\",\"太辣\"]）");
    }

    // ======== 公开 API ========

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
                    Map<String, Object> raw = gson.fromJson(json, MAP_TYPE);
                    if (raw != null) {
                        for (Map.Entry<String, Object> e : raw.entrySet()) {
                            profile.fields.put(e.getKey(), FieldEntry.fromRaw(e.getValue()));
                        }
                    }
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
     * 异步合并用户画像字段。遵循"近期优先"：仅当新字段时间戳 > 已有时间戳时才覆盖。
     */
    public void updateProfileAsync(Long userId, Map<String, Object> updates) {
        if (userId == null || updates == null || updates.isEmpty() || jdbc == null) return;

        executor.submit(() -> {
            try {
                long now = System.currentTimeMillis();
                Profile existing = getProfile(userId);

                for (Map.Entry<String, Object> e : updates.entrySet()) {
                    String key = e.getKey();
                    FieldEntry newEntry = FieldEntry.fromRaw(e.getValue());
                    if (newEntry.t == 0L) newEntry.t = now; // 无时间戳则用当前时间

                    FieldEntry oldEntry = existing.fields.get(key);
                    if (oldEntry == null || newEntry.t >= oldEntry.t) {
                        existing.fields.put(key, newEntry);
                    }
                    // 旧值更新 → 保留旧值（近期优先）
                }

                existing.lastUpdated = now;
                existing.totalSessions++;

                // 序列化为 JSONB 兼容格式；只保留固定 schema 字段，逐步清理旧的自由 key 画像
                Map<String, Object> toStore = new LinkedHashMap<>();
                for (Map.Entry<String, FieldEntry> e : existing.fields.entrySet()) {
                    if (PROFILE_SCHEMA.containsKey(e.getKey())) {
                        toStore.put(e.getKey(), e.getValue().toRaw());
                    }
                }
                String json = gson.toJson(toStore);

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

    public String toPromptContext(Long userId) {
        Profile profile = getProfile(userId);
        if (profile.fields.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 用户画像（历史偏好，仅供参考；若与用户当前表述冲突，一律以当前表述为准）\n");
        // 只输出固定 schema 字段（updateProfileAsync 也只存 schema 字段，旧自由 key 已清理）
        for (String key : PROFILE_SCHEMA.keySet()) {
            FieldEntry e = profile.fields.get(key);
            if (e != null && e.v != null && !e.v.toString().isBlank()) {
                sb.append("- ").append(key).append(": ").append(e.v).append("\n");
            }
        }
        return sb.toString();
    }

    // ======== 数据类 ========

    public static class Profile {
        public Map<String, FieldEntry> fields = new LinkedHashMap<>();
        public long lastUpdated;
        public int totalSessions;
    }
}
