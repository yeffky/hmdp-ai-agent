package com.hmdp.agent.tool;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.ChatHistoryRound;
import com.hmdp.repository.ChatHistoryRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import com.hmdp.agent.graph.error.ToolException;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史对话检索工具 — 按关键词搜索用户历史对话，供"历史回溯"意图调用。
 * 用户在上下文窗口中只有最近 3 轮对话，更早的内容需要此工具按需检索。
 */
@Component
public class HistorySearchTool {

    @Resource
    private ChatHistoryRepository chatHistoryRepo;

    @Tool("按关键词搜索当前用户的历史对话记录。当用户提及之前的对话内容但上下文窗口中没有时使用。keywords 为用户问题中提取的关键词，多个关键词用空格分隔。返回匹配的历史对话轮次列表。")
    public String searchHistory(
            @P("搜索关键词，多个关键词用空格分隔，如：火锅 评价") String keywords) {
        Long userId = getUserId();
        if (userId == null) {
            return "用户未登录，无法检索历史对话。";
        }
        if (keywords == null || keywords.trim().isEmpty()) {
            return "请提供搜索关键词。";
        }

        try {
            List<ChatHistoryRound> rounds = chatHistoryRepo.searchByKeywords(userId, keywords.trim(), 10);
            if (rounds.isEmpty()) {
                return "未找到与「" + keywords + "」相关的历史对话。";
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (ChatHistoryRound r : rounds) {
                Map<String, Object> item = new HashMap<>();
                item.put("时间", r.getCreateTime() != null ? r.getCreateTime().toString() : "未知");
                item.put("用户", r.getUserMessage());
                item.put("助手", r.getAssistantMessage());
                result.add(item);
            }

            return JSONUtil.toJsonPrettyStr(result);
        } catch (Exception e) {
            throw new ToolException("searchHistory", "检索历史对话失败: " + e.getMessage(), e);
        }
    }

    private Long getUserId() {
        Long ctxUserId = com.hmdp.agent.ToolContext.getUserId();
        if (ctxUserId != null && ctxUserId > 0) return ctxUserId;
        return null;
    }
}
