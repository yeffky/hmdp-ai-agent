package com.hmdp.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.mapper.ChatMessageMapper;
import com.hmdp.utils.UserHolder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 带 MySQL 持久化的 ChatMemory。
 * 工具调用由 Controller 层前置处理，Agent 不再注册 @Tool。
 */
public class MySqlChatMemory implements ChatMemory {

    private final String sessionId;
    private final Object id;
    private final ChatMessageMapper mapper;
    private final int maxMessages;
    private final List<ChatMessage> messages = new ArrayList<>();

    public MySqlChatMemory(String sessionId, ChatMessageMapper mapper, int maxMessages) {
        this.sessionId = sessionId;
        this.id = sessionId;
        this.mapper = mapper;
        this.maxMessages = maxMessages;
        loadFromDb();
    }

    @Override
    public Object id() {
        return id;
    }

    private void loadFromDb() {
        List<com.hmdp.entity.ChatMessage> dbMessages = mapper.selectList(
                new QueryWrapper<com.hmdp.entity.ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("create_time")
                        .last("LIMIT " + maxMessages)
        );
        for (com.hmdp.entity.ChatMessage m : dbMessages) {
            ChatMessage lcMsg = convertFromDb(m);
            if (lcMsg != null) {
                messages.add(lcMsg);
            }
        }
    }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        while (messages.size() > maxMessages) {
            messages.remove(0);
        }
        persistToDb(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    private void persistToDb(ChatMessage message) {
        com.hmdp.entity.ChatMessage entity = new com.hmdp.entity.ChatMessage()
                .setSessionId(sessionId)
                .setUserId(getCurrentUserId())
                .setCreateTime(LocalDateTime.now());

        if (message instanceof UserMessage) {
            entity.setRole("user");
            entity.setContent(((UserMessage) message).singleText());
        } else if (message instanceof AiMessage) {
            entity.setRole("assistant");
            AiMessage aiMsg = (AiMessage) message;
            entity.setContent(aiMsg.text() != null ? aiMsg.text() : "");
            if (aiMsg.hasToolExecutionRequests()) {
                entity.setToolName("tool_request");
            }
        } else if (message instanceof SystemMessage) {
            entity.setRole("system");
            entity.setContent(((SystemMessage) message).text());
        }

        if (entity.getContent() != null) {
            mapper.insert(entity);
        }
    }

    private ChatMessage convertFromDb(com.hmdp.entity.ChatMessage db) {
        if (db.getRole() == null) return null;
        switch (db.getRole()) {
            case "user":
                return UserMessage.from(db.getContent());
            case "assistant":
                return AiMessage.from(db.getContent());
            case "system":
                return SystemMessage.from(db.getContent());
            default:
                return null;
        }
    }

    private Long getCurrentUserId() {
        try {
            return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
