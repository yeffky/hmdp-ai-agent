package com.hmdp.rag.splitter;

import java.util.*;

/**
 * 递归字符切片器 — 优先按段落/句子边界切分，保持语义完整性。
 *
 * 切分优先级：段落(\n\n) → 句子(中文标点) → 固定字符数(兜底)
 * 支持 overlap 确保上下文连贯。
 *
 * 示例：
 * <pre>
 * DocumentSplitter splitter = new DocumentSplitter(500, 50);
 * List<String> chunks = splitter.split("很长的文档内容...");
 * </pre>
 */
public class DocumentSplitter {

    private final int chunkSize;       // 每个 chunk 的目标字符数
    private final int chunkOverlap;    // chunk 之间的重叠字符数

    // 按优先级排列的分隔符
    private static final String[] SEPARATORS = {
            "\n\n",     // 段落
            "\n",       // 换行
            "。",       // 中文句号
            "！",       // 中文感叹号
            "？",       // 中文问号
            "；",       // 中文分号
            "，",       // 中文逗号（最后选择）
    };

    public DocumentSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 将文本切分为多个 chunk。
     */
    public List<String> split(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> chunks = recursiveSplit(text.trim());
        // 添加重叠
        return addOverlap(chunks);
    }

    /**
     * 对多个文本批量切分，并标记 chunk 序号。
     */
    public List<ChunkWithIndex> splitWithIndex(String text, String source, String title) {
        List<String> texts = split(text);
        List<ChunkWithIndex> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            result.add(new ChunkWithIndex(texts.get(i), source, title, i));
        }
        return result;
    }

    /** 递归切分核心 */
    private List<String> recursiveSplit(String text) {
        if (text.length() <= chunkSize) {
            return Collections.singletonList(text);
        }

        // 按优先级尝试分隔符
        for (String sep : SEPARATORS) {
            List<String> segments = splitBy(text, sep);
            if (segments.size() > 1) {
                // 递归处理每个片段
                List<String> result = new ArrayList<>();
                StringBuilder buffer = new StringBuilder();
                for (String seg : segments) {
                    if (buffer.length() + seg.length() > chunkSize && buffer.length() > 0) {
                        // 当前 buffer 满了，递归处理
                        result.addAll(recursiveSplit(buffer.toString()));
                        buffer.setLength(0);
                    }
                    if (seg.length() > chunkSize) {
                        // 单个片段过长，递归处理
                        if (buffer.length() > 0) {
                            result.addAll(recursiveSplit(buffer.toString()));
                            buffer.setLength(0);
                        }
                        result.addAll(recursiveSplit(seg));
                    } else {
                        buffer.append(buffer.length() > 0 ? sep : "").append(seg);
                    }
                }
                if (buffer.length() > 0) {
                    result.addAll(recursiveSplit(buffer.toString()));
                }
                return result;
            }
        }

        // 兜底：硬切分（按字符数）
        return hardSplit(text);
    }

    /** 按指定分隔符切分，保留分隔符语义 */
    private List<String> splitBy(String text, String separator) {
        List<String> result = new ArrayList<>();
        String[] parts = text.split(java.util.regex.Pattern.quote(separator), -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.isEmpty()) {
                // 保留分隔符在原位（非最后一个片段加回分隔符）
                if (i < parts.length - 1 && !separator.equals("\n\n") && !separator.equals("\n")) {
                    result.add(part + separator);
                } else {
                    result.add(part);
                }
            }
        }
        return result;
    }

    /** 硬切分（兜底策略） */
    private List<String> hardSplit(String text) {
        List<String> chunks = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + chunkSize, text.length());
            chunks.add(text.substring(pos, end));
            pos = end;
        }
        return chunks;
    }

    /** 为相邻 chunk 添加重叠上下文 */
    private List<String> addOverlap(List<String> chunks) {
        if (chunks.size() <= 1 || chunkOverlap <= 0) {
            return chunks;
        }
        List<String> result = new ArrayList<>();
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String curr = chunks.get(i);
            if (prev.length() > chunkOverlap) {
                // 从前一个 chunk 尾部取 overlap 字符拼到当前 chunk 开头
                String overlapText = prev.substring(prev.length() - chunkOverlap);
                curr = overlapText + "\n...\n" + curr;
            }
            result.add(curr);
        }
        return result;
    }

    // ========== 内部类 ==========

    public static class ChunkWithIndex {
        private final String text;
        private final String source;
        private final String title;
        private final int index;

        public ChunkWithIndex(String text, String source, String title, int index) {
            this.text = text;
            this.source = source;
            this.title = title;
            this.index = index;
        }

        public String getText() { return text; }
        public String getSource() { return source; }
        public String getTitle() { return title; }
        public int getIndex() { return index; }
    }
}
