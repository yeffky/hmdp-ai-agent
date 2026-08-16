package com.hmdp.agent.memory.reflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reflexion 教训存储 —— 基于 MySQL（hmdp 主库）的 agent_reflection 表。
 *
 * <p>读路径：按用户查询关键词 LIKE 匹配 keywords 列，取最近若干条。
 * <p>写路径：保存一条教训（domain/lesson/keywords）。
 */
@Component
public class ReflectionStore {

    private static final Logger log = LoggerFactory.getLogger(ReflectionStore.class);

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @PostConstruct
    void init() {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 按用户查询关键词检索相关教训（任一关键词命中即返回，按时间倒序）。 */
    public List<Reflection> findRelevant(String query, int limit) {
        List<String> kws = extractKeywords(query);
        if (kws.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder(
                "SELECT id, session_id, domain, user_query, lesson, keywords, create_time FROM agent_reflection WHERE ");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < kws.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("keywords LIKE ?");
            args.add("%" + kws.get(i) + "%");
        }
        sql.append(" ORDER BY create_time DESC LIMIT ?");
        args.add(limit);
        try {
            return jdbc.query(sql.toString(), (rs, rn) -> {
                Reflection r = new Reflection();
                r.setId(rs.getLong("id"));
                r.setSessionId(rs.getString("session_id"));
                r.setDomain(rs.getString("domain"));
                r.setUserQuery(rs.getString("user_query"));
                r.setLesson(rs.getString("lesson"));
                r.setKeywords(rs.getString("keywords"));
                r.setCreateTime(rs.getTimestamp("create_time"));
                return r;
            }, args.toArray());
        } catch (Exception e) {
            log.warn("findRelevant failed（agent_reflection 表可能未创建）: {}", e.getMessage());
            return List.of();
        }
    }

    /** 保存一条教训。 */
    public void save(String sessionId, String domain, String userQuery, String lesson, String keywords) {
        try {
            jdbc.update("INSERT INTO agent_reflection (session_id, domain, user_query, lesson, keywords) VALUES (?,?,?,?,?)",
                    sessionId, domain, userQuery, lesson, keywords);
        } catch (Exception e) {
            log.warn("Reflection save failed: {}", e.getMessage());
        }
    }

    // ======== 中文关键词提取（读路径匹配用，轻量规则，不调 LLM） ========

    private static final Set<String> STOPS = Set.of(
            "的", "了", "吗", "呢", "我", "你", "他", "她", "它", "我们", "你们", "他们",
            "请问", "帮我", "想要", "想", "要", "有没有", "怎么", "什么", "哪里", "附近",
            "推荐", "一下", "个", "这家", "那家", "哪个", "多少", "怎么样", "好吃", "好喝",
            "贵", "便宜", "方便", "看看", "查一下", "给");

    private static final String[] BIZ_KEYWORDS = {
            "火锅", "烧烤", "烤肉", "奶茶", "咖啡", "日料", "西餐", "川菜", "湘菜", "粤菜",
            "海鲜", "自助", "面包", "蛋糕", "甜品", "小吃", "快餐", "面", "粉", "美发", "美甲",
            "健身", "按摩", "SPA", "亲子", "酒吧", "轰趴", "KTV", "排队", "取号", "团购", "评价",
            "订单", "拱墅", "鼓楼", "杭州", "福州", "宠物", "停车"
    };

    List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        // 按标点/空白拆分连续片段
        String[] parts = query.split("[\\s，。,.！？!?、；;：:\"'()（）\\[\\]{}]+");
        for (String p : parts) {
            if (p.length() >= 2 && !STOPS.contains(p)) out.add(p);
        }
        // 补充已知业务词（保证领域匹配）
        for (String kw : BIZ_KEYWORDS) {
            if (query.contains(kw) && !out.contains(kw)) out.add(kw);
        }
        // 最多取 5 个，避免过度匹配
        if (out.size() > 5) out = new ArrayList<>(out.subList(0, 5));
        return out;
    }
}
