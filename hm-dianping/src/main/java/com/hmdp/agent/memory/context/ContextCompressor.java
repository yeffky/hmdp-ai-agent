package com.hmdp.agent.memory.context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hmdp.agent.graph.nodes.Transcript;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 驱动的对话历史压缩器。
 * 压缩旧消息为摘要，同时从中提取用户画像信息并异步持久化到 UserStore。
 */
@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    // 匹配 [画像] {...} JSON 块
    private static final Pattern PROFILE_PATTERN =
            Pattern.compile("\\[画像\\]\\s*\\n?\\s*(\\{[\\s\\S]*?\\})\\s*(?:\\n|$)", Pattern.MULTILINE);

    @Resource
    private ChatModel model;

    @Resource
    private CompressionConfig config;

    @Resource
    private UserStore userStore;

    /**
     * 压缩结果
     */
    public static class CompressionResult {
        private final String summary;
        private final List<Map<String, String>> keptMessages;

        public CompressionResult(String summary, List<Map<String, String>> keptMessages) {
            this.summary = summary;
            this.keptMessages = keptMessages != null ? keptMessages : new ArrayList<>();
        }

        public String getSummary() { return summary; }
        public List<Map<String, String>> getKeptMessages() { return keptMessages; }
    }

    /**
     * 检查消息是否需要压缩，如需要则执行压缩。
     * 同时检测用户画像信息，异步持久化到 UserStore。
     *
     * @param messages        完整消息列表（从旧到新）
     * @param previousSummary 之前已有的压缩摘要
     * @param userId          用户 ID（用于画像存储，可为 null）
     */
    public CompressionResult compressIfNeeded(List<Map<String, String>> messages,
                                               String previousSummary, Long userId) {
        if (messages == null || messages.isEmpty()) {
            return new CompressionResult(previousSummary, messages);
        }

        int totalTokens = TokenCounter.countFromMap(messages);
        if (totalTokens <= config.getCompressThreshold() || !config.isEnabled()) {
            // 不需要压缩，但检查硬上限
            if (messages.size() > config.getMaxUncompressedMessages()) {
                List<Map<String, String>> trimmed = new ArrayList<>(
                        messages.subList(messages.size() - config.getMaxUncompressedMessages(), messages.size()));
                return new CompressionResult(previousSummary, trimmed);
            }
            return new CompressionResult(previousSummary, messages);
        }

        // 保留区按「真实轮次」对齐：从末尾往前第 keepRecentRounds 个真实 user 为分界，
        // 其后的完整轨迹（含 tool 结果）保留（供跨轮复用已获取信息），更早轮次压缩。
        int cutoffIndex = Transcript.nthRealUserIdx(messages, config.getKeepRecentRounds());
        if (cutoffIndex <= 0) {
            return new CompressionResult(previousSummary, messages);
        }

        List<Map<String, String>> toCompress = messages.subList(0, cutoffIndex);
        List<Map<String, String>> toKeep = new ArrayList<>(
                messages.subList(cutoffIndex, messages.size()));

        log.info("Compressing {} messages ({} to keep), threshold={} tokens",
                toCompress.size(), toKeep.size(), config.getCompressThreshold());

        // 调用 LLM 压缩 + 画像提取
        CompressAndProfileResult result = compressWithLLM(toCompress, previousSummary);

        // 如果检测到画像信息，异步写入 UserStore
        if (result.profileFields != null && !result.profileFields.isEmpty() && userId != null) {
            final Long uid = userId;
            final Map<String, Object> fields = result.profileFields;
            CompletableFuture.runAsync(() -> userStore.updateProfileAsync(uid, fields));
            log.info("Extracted {} profile fields for userId={}, persisting async", fields.size(), uid);
        }

        return new CompressionResult(result.summary, toKeep);
    }

    /**
     * 调用 LLM 压缩旧消息同时提取用户画像。
     * prompt 要求输出两部分：[摘要] 和 [画像]
     */
    private CompressAndProfileResult compressWithLLM(List<Map<String, String>> toCompress,
                                                      String previousSummary) {
        // 构建待压缩的对话文本
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : toCompress) {
            String role = msg.getOrDefault("role", "unknown");
            String content = msg.getOrDefault("content", "");
            if (content.isEmpty()) continue;

            switch (role) {
                case "user":
                    sb.append("用户: ").append(content).append("\n");
                    break;
                case "assistant":
                    sb.append("助手: ").append(content).append("\n");
                    break;
                // tool 消息（工具执行结果 JSON）不进对话摘要——执行细节压缩后无意义，且会污染意图理解
            }
        }

        String prompt = "请完成两项任务：\n\n"
                + "## 任务1：压缩对话历史\n"
                + "将以下对话历史压缩为一段简洁的摘要（不超过" + config.getMaxSummaryTokens() + " tokens）。\n"
                + "必须保留：用户意图、用户明确提供的事实信息（地址、偏好、身份、订单号等）、已完成的操作、重要结果。\n"
                + "冲突处理：如果新旧信息矛盾，以用户最近的表述为准，丢弃旧的矛盾信息。\n"
                + "去重：重复信息只保留一次。\n\n"
                + "## 任务2：提取用户画像\n"
                + "从对话中提取用户的**稳定偏好**，按以下**固定字段**输出 JSON。只输出有依据的字段，key 严格用给定的，**禁止新增其它字段**：\n"
                + buildProfileSchemaPrompt()
                + "不提取会话内的一次性事实（如订单号、排队号、本次查询到的店铺），那些留在摘要里即可。\n"
                + "冲突处理：如果新旧信息矛盾，以用户最近的表述为准，丢弃旧的矛盾信息。\n\n"
                + "## 输出格式\n"
                + "[摘要]\n"
                + "<压缩后的对话摘要>\n\n"
                + "[画像]\n"
                + "<JSON，仅包含上述固定字段，无画像则输出 {}>\n\n";

        if (previousSummary != null && !previousSummary.isEmpty()) {
            prompt += "## 之前的摘要\n" + previousSummary + "\n\n";
        }
        prompt += "## 待压缩对话\n" + sb.toString();

        try {
            ChatResponse resp = model.chat(List.of(
                    SystemMessage.from("你是对话历史压缩器和用户画像提取器。严格按照 [摘要]/[画像] 格式输出。"),
                    UserMessage.from(prompt)
            ));
            String raw = resp.aiMessage().text();
            log.debug("Compression output ({} chars)", raw.length());

            return parseCompressionOutput(raw, previousSummary);
        } catch (Exception e) {
            log.error("Compression LLM call failed, keeping previous summary", e);
            return new CompressAndProfileResult(
                    previousSummary != null ? previousSummary : "", null);
        }
    }

    /** 从固定 schema 生成画像字段提示（约束 LLM 只输出这些 key，避免自由 key 导致画像膨胀）。 */
    private String buildProfileSchemaPrompt() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : UserStore.PROFILE_SCHEMA.entrySet()) {
            sb.append("- \"").append(e.getKey()).append("\": ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 输出，分离 [摘要] 和 [画像]
     */
    private CompressAndProfileResult parseCompressionOutput(String raw, String fallbackSummary) {
        String summary = fallbackSummary != null ? fallbackSummary : "";
        Map<String, Object> profileFields = null;

        // 提取 [画像] JSON
        Matcher profileMatcher = PROFILE_PATTERN.matcher(raw);
        if (profileMatcher.find()) {
            String jsonStr = profileMatcher.group(1).trim();
            try {
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                profileFields = new Gson().fromJson(jsonStr, type);
                if (profileFields == null) profileFields = Collections.emptyMap();
            } catch (Exception e) {
                log.debug("Failed to parse profile JSON: {}", jsonStr);
                profileFields = Collections.emptyMap();
            }
        }

        // 提取 [摘要] 文本（[摘要] 到 [画像] 之间的内容）
        int summaryStart = raw.indexOf("[摘要]");
        int profileStart = raw.indexOf("[画像]");

        if (summaryStart >= 0) {
            int begin = summaryStart + "[摘要]".length();
            int end = profileStart > summaryStart ? profileStart : raw.length();
            String extracted = raw.substring(begin, end).trim();
            if (!extracted.isEmpty()) {
                summary = extracted;
            }
        } else {
            // 没有 [摘要] 标记，整个输出作为摘要
            summary = raw.replaceAll("\\[画像\\]\\s*\\{[\\s\\S]*?\\}", "").trim();
            if (summary.isEmpty() && fallbackSummary != null) {
                summary = fallbackSummary;
            }
        }

        return new CompressAndProfileResult(summary, profileFields);
    }

    // ======== 内部类 ========

    private static class CompressAndProfileResult {
        final String summary;
        final Map<String, Object> profileFields;

        CompressAndProfileResult(String summary, Map<String, Object> profileFields) {
            this.summary = summary;
            this.profileFields = profileFields;
        }
    }
}
