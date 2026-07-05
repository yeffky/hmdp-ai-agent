package com.hmdp.repository;

import com.hmdp.dto.ChatHistoryRound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class ChatHistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryRepository.class);

    @Resource
    @Qualifier("postgresJdbcTemplate")
    private JdbcTemplate pg;

    public void saveRound(Long userId, String userMessage, String assistantMessage) {
        try {
            pg.update(
                "INSERT INTO tb_chat_history (user_id, user_message, assistant_message) VALUES (?, ?, ?)",
                userId, userMessage, assistantMessage
            );
            log.info("Chat history saved: userId={}, msgLen={}, replyLen={}",
                    userId, userMessage.length(), assistantMessage.length());
        } catch (Exception e) {
            log.error("Failed to save chat round for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * @param beforeId null = most recent; non-null = rounds with id < beforeId
     * @param limit    max rows
     * @return rounds ordered by id DESC (newest first)
     */
    public List<ChatHistoryRound> findRounds(Long userId, Long beforeId, int limit) {
        List<ChatHistoryRound> result;
        if (beforeId == null) {
            log.info("Chat history query: userId={}, limit={} (latest)", userId, limit);
            result = pg.query(
                "SELECT id, user_message, assistant_message, create_time FROM tb_chat_history " +
                "WHERE user_id = ? ORDER BY id DESC LIMIT ?",
                this::mapRow, userId, limit
            );
        } else {
            log.info("Chat history query: userId={}, beforeId={}, limit={}", userId, beforeId, limit);
            result = pg.query(
                "SELECT id, user_message, assistant_message, create_time FROM tb_chat_history " +
                "WHERE user_id = ? AND id < ? ORDER BY id DESC LIMIT ?",
                this::mapRow, userId, beforeId, limit
            );
        }
        log.info("Chat history result: userId={}, rows={}, oldestId={}, newestId={}",
                userId, result.size(),
                result.isEmpty() ? null : result.get(result.size() - 1).getId(),
                result.isEmpty() ? null : result.get(0).getId());
        return result;
    }

    public Long getMinId(Long userId) {
        try {
            Long minId = pg.queryForObject(
                "SELECT MIN(id) FROM tb_chat_history WHERE user_id = ?",
                Long.class, userId
            );
            log.info("Chat history minId: userId={}, minId={}", userId, minId);
            return minId;
        } catch (Exception e) {
            log.warn("Failed to get minId for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 按关键词搜索聊天历史 — 供 HistorySearchTool 调用。
     * 关键词以空格/逗号分隔，在 user_message 和 assistant_message 中做 ILIKE 模糊匹配。
     */
    public List<ChatHistoryRound> searchByKeywords(Long userId, String keywords, int limit) {
        String[] words = keywords.split("[\\s,，、]+");
        if (words.length == 0) return List.of();

        StringBuilder sql = new StringBuilder(
            "SELECT id, user_message, assistant_message, create_time FROM tb_chat_history WHERE user_id = ? AND (");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sql.append(" OR ");
            String w = words[i].trim();
            if (w.isEmpty()) continue;
            sql.append("user_message ILIKE ? OR assistant_message ILIKE ?");
        }
        sql.append(") ORDER BY id DESC LIMIT ?");

        // Build params: userId + 2 params per keyword (for user_message and assistant_message) + limit
        Object[] params = new Object[1 + words.length * 2 + 1];
        int idx = 0;
        params[idx++] = userId;
        for (String w : words) {
            String pattern = "%" + w.trim() + "%";
            params[idx++] = pattern;
            params[idx++] = pattern;
        }
        params[idx] = limit;

        try {
            List<ChatHistoryRound> result = pg.query(sql.toString(), this::mapRow, params);
            log.info("History keyword search: userId={}, keywords='{}', rows={}",
                    userId, keywords, result.size());
            return result;
        } catch (Exception e) {
            log.error("History keyword search failed for userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private ChatHistoryRound mapRow(ResultSet rs, int rowNum) throws SQLException {
        ChatHistoryRound r = new ChatHistoryRound();
        r.setId(rs.getLong("id"));
        r.setUserMessage(rs.getString("user_message"));
        r.setAssistantMessage(rs.getString("assistant_message"));
        Timestamp ts = rs.getTimestamp("create_time");
        r.setCreateTime(ts != null ? ts.toLocalDateTime() : null);
        return r;
    }
}
