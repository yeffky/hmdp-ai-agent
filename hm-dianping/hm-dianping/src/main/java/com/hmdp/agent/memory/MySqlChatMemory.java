package com.hmdp.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.ChatMessage;
import com.hmdp.mapper.ChatMessageMapper;
import com.hmdp.utils.UserHolder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom ChatMemory implementation that persists messages to MySQL.
 * Short-term: in-memory message window (configured by load limit).
 * Long-term: all messages written to tb_chat_message for audit and future retrieval.
 */
public class MySqlChatMemory implements ChatMemory {

    private final String sessionId;
    private final Object id;
    private final ChatMessageMapper mapper;
    private final int maxMessages;
    private final List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

    public MySqlChatMemory(String sessionId, ChatMessageMapper mapper, int maxMessages) {
        this.sessionId = sessionId;
        this.id = sessionId;
        this.mapper = mapper;
        this.maxMessages = maxMessages;
        // Load recent history from DB
        loadFromDb();
    }

    @Override
    public Object id() {
        return id;
    }

    private void loadFromDb() {
        List<ChatMessage> dbMessages = mapper.selectList(
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByAsc("create_time")
                        .last("LIMIT " + maxMessages)
        );
        for (ChatMessage m : dbMessages) {
            dev.langchain4j.data.message.ChatMessage lcMsg = convertFromDb(m);
            if (lcMsg != null) {
                messages.add(lcMsg);
            }
        }
    }

    @Override
    public void add(dev.langchain4j.data.message.ChatMessage message) {
        messages.add(message);
        // Trim if exceeds max
        while (messages.size() > maxMessages) {
            messages.remove(0);
        }
        // Persist to DB
        persistToDb(message);
    }

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> messages() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    private void persistToDb(dev.langchain4j.data.message.ChatMessage message) {
        ChatMessage entity = new ChatMessage()
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
                // Record tool call intent
                entity.setToolName("tool_request");
            }
        } else if (message instanceof ToolExecutionResultMessage) {
            entity.setRole("tool");
            entity.setContent(((ToolExecutionResultMessage) message).text());
            entity.setToolName(((ToolExecutionResultMessage) message).toolName());
        }

        if (entity.getContent() != null) {
            mapper.insert(entity);
        }
    }

    private dev.langchain4j.data.message.ChatMessage convertFromDb(ChatMessage db) {
        if (db.getRole() == null) return null;
        switch (db.getRole()) {
            case "user":
                return UserMessage.from(db.getContent());
            case "assistant":
                return AiMessage.from(db.getContent());
            case "tool":
                return new ToolExecutionResultMessage(null, db.getToolName(), db.getContent());
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
