package com.hmdp.rag.splitter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构感知切片器 —— 尊重文档层级结构进行切分。
 *
 * 切分策略（由粗到细递归）：
 *   1. H2 (## )  → 按大章节切分
 *   2. H3 (### ) → 按小节切分
 *   3. 段落 (\n\n) → 按段落切分
 *   4. 句子（中英文标点）→ 按句子切分
 *   5. 硬截断 → 兜底
 *
 * 保护规则：
 *   - 代码块 (```) 保持完整
 *   - 表格 (|...|) 保持完整
 *   - 列表项尽量不拆散
 *   - 每个 chunk 携带 heading 上下文路径
 *
 * 示例：
 * <pre>
 * MarkdownSplitter splitter = new MarkdownSplitter(500, 50);
 * List<ChunkWithHeadings> chunks = splitter.split("# 帮助中心\n\n## 退款\n...");
 * // chunk.text = "退款相关的内容..."
 * // chunk.headingPath = "帮助中心 > 退款"
 * </pre>
 */
public class MarkdownSplitter {

    private final int chunkSize;
    private final int chunkOverlap;

    // Markdown 结构正则
    private static final Pattern H2_PATTERN = Pattern.compile("(?m)^## (.+)$");
    private static final Pattern H3_PATTERN = Pattern.compile("(?m)^### (.+)$");
    private static final Pattern H1_PATTERN = Pattern.compile("(?m)^# (.+)$");
    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    // 完整表格块：表头行 + 分隔行(|-|) + 数据行
    private static final Pattern TABLE_BLOCK = Pattern.compile(
            "(^\\|.+\\|\\s*$\\n^\\|[\\s:-]+\\|\\s*$(\\n^\\|.+\\|\\s*$)*)",
            Pattern.MULTILINE);
    // 图片语法 ![alt](url)
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^]]*\\]\\([^)]+\\)");

    // 句子分隔符（按优先级）
    private static final String[] SENTENCE_SEPS = { "\n\n", "\n", "。", "！", "？", "；", "，" };

    public MarkdownSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    // ========== 公开 API ==========

    /** 切分 markdown 文本，返回带标题路径的切片 */
    public List<ChunkWithHeadings> split(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String text = markdown.trim();

        // 1. 提取 H1 标题作为文档标题
        String docTitle = "";
        Matcher h1m = H1_PATTERN.matcher(text);
        if (h1m.find()) {
            docTitle = h1m.group(1).trim();
        }

        // 2. 保护特殊块（代码块 + 表格）：占位符替换，切分后还原
        Map<String, String> protectedBlocks = new LinkedHashMap<>();
        text = protectSpecialBlocks(text, protectedBlocks);

        // 3. 递归切分
        List<Section> sections = splitByH2(text, docTitle);

        // 4. 展开为 ChunkWithHeadings 列表
        List<ChunkWithHeadings> result = new ArrayList<>();
        for (Section sec : sections) {
            result.addAll(flattenSection(sec));
        }

        // 5. 还原特殊块
        for (ChunkWithHeadings c : result) {
            c.text = restoreBlocks(c.text, protectedBlocks);
        }

        return result;
    }

    /** 切分为纯文本列表（兼容旧接口） */
    public List<String> splitPlain(String markdown) {
        List<ChunkWithHeadings> chunks = split(markdown);
        List<String> result = new ArrayList<>();
        for (ChunkWithHeadings c : chunks) {
            result.add(c.text);
        }
        return result;
    }

    /** 切分并带来源信息（兼容 IngestionService 接口） */
    public List<DocumentSplitter.ChunkWithIndex> splitWithIndex(String markdown, String source, String title) {
        List<String> texts = splitPlain(markdown);
        List<DocumentSplitter.ChunkWithIndex> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            result.add(new DocumentSplitter.ChunkWithIndex(texts.get(i), source, title, i));
        }
        return result;
    }

    // ========== 核心递归切分 ==========

    /** 按 H2 切分 */
    private List<Section> splitByH2(String text, String docTitle) {
        List<Section> sections = new ArrayList<>();
        String[] parts = text.split("(?m)(?=^## )", -1);

        for (String part : parts) {
            if (part.trim().isEmpty()) continue;

            // 提取 H2 标题
            Matcher m = H2_PATTERN.matcher(part);
            String h2Title = "";
            String content = part;
            if (m.find()) {
                h2Title = m.group(1).trim();
                // 去掉 H2 行本身，保留内容
                content = part.substring(m.end()).trim();
            }

            if (content.isEmpty()) continue;

            // 按 H3 进一步切分
            List<Section> subSections = splitByH3(content, h2Title, docTitle);
            if (subSections.isEmpty()) {
                // 无 H3，整个作为一节
                sections.add(new Section(content, docTitle, h2Title, ""));
            } else {
                sections.addAll(subSections);
            }
        }
        return sections;
    }

    /** 按 H3 切分 */
    private List<Section> splitByH3(String text, String h2Title, String docTitle) {
        List<Section> sections = new ArrayList<>();
        String[] parts = text.split("(?m)(?=^### )", -1);

        for (String part : parts) {
            if (part.trim().isEmpty()) continue;

            Matcher m = H3_PATTERN.matcher(part);
            String h3Title = "";
            String content = part;
            if (m.find()) {
                h3Title = m.group(1).trim();
                content = part.substring(m.end()).trim();
            }

            if (content.isEmpty()) continue;

            sections.add(new Section(content, docTitle, h2Title, h3Title));
        }
        return sections;
    }

    /** 将 Section 展开为不超过 chunkSize 的 ChunkWithHeadings */
    private List<ChunkWithHeadings> flattenSection(Section sec) {
        List<ChunkWithHeadings> result = new ArrayList<>();
        List<String> paragraphs = splitRecursive(sec.content);
        for (int i = 0; i < paragraphs.size(); i++) {
            String headingPath = buildHeadingPath(sec.docTitle, sec.h2Title, sec.h3Title);
            result.add(new ChunkWithHeadings(paragraphs.get(i), headingPath, i));
        }
        return result;
    }

    /** 递归段落/句子切分 */
    private List<String> splitRecursive(String text) {
        if (text.length() <= chunkSize) {
            return Collections.singletonList(text);
        }

        // 尝试各级分隔符
        for (String sep : SENTENCE_SEPS) {
            List<String> segments = splitBy(text, sep);
            if (segments.size() > 1) {
                // 合并短片段直到接近 chunkSize
                List<String> merged = mergeSegments(segments, sep);
                if (merged.size() > 1) {
                    // 递归处理每个合并后的片段
                    List<String> result = new ArrayList<>();
                    for (String mg : merged) {
                        if (mg.length() > chunkSize) {
                            result.addAll(splitRecursive(mg));
                        } else {
                            result.add(mg);
                        }
                    }
                    return result;
                }
            }
        }

        // 兜底：硬切分
        return hardSplit(text);
    }

    /** 合并短片段 */
    private List<String> mergeSegments(List<String> segments, String separator) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String seg : segments) {
            String trimmed = seg.trim();
            if (trimmed.isEmpty()) continue;

            String candidate = buffer.length() > 0
                    ? buffer.toString() + separator + trimmed
                    : trimmed;

            if (candidate.length() <= chunkSize) {
                // 还能装下，继续合并
                if (buffer.length() > 0) {
                    buffer.append(separator);
                }
                buffer.append(trimmed);
            } else {
                // 装不下了，保存当前 buffer
                if (buffer.length() > 0) {
                    result.add(buffer.toString());
                    buffer.setLength(0);
                }
                // 单独的片段如果太长，直接放进去（下一层递归会处理）
                if (trimmed.length() <= chunkSize) {
                    buffer.append(trimmed);
                } else {
                    result.add(trimmed);
                }
            }
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString());
        }
        return result;
    }

    /** 按分隔符切分 */
    private List<String> splitBy(String text, String separator) {
        List<String> result = new ArrayList<>();
        String[] parts = text.split(Pattern.quote(separator), -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    /** 硬切分（兜底） */
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

    // ========== 特殊块保护（代码块 + 表格 + 图片） ==========

    /**
     * 将代码块、表格块、图片替换为占位符，切分完成后统一还原。
     * 代码块优先（避免表格/图片正则误匹配代码块内的 | 和 ![ 字符）。
     */
    private String protectSpecialBlocks(String text, Map<String, String> blocks) {
        int idx = 0;
        // 1. 保护代码块
        Matcher cm = CODE_BLOCK.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (cm.find()) {
            String placeholder = "{{CODEBLOCK_" + idx + "}}";
            blocks.put(placeholder, cm.group());
            cm.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            idx++;
        }
        cm.appendTail(sb);
        text = sb.toString();

        // 2. 保护表格块（表头 + 分隔行 + 数据行）
        Matcher tm = TABLE_BLOCK.matcher(text);
        sb = new StringBuffer();
        while (tm.find()) {
            String placeholder = "{{TABLEBLOCK_" + idx + "}}";
            blocks.put(placeholder, tm.group());
            tm.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            idx++;
        }
        tm.appendTail(sb);
        text = sb.toString();

        // 3. 保护图片 ![alt](url) — 防止 alt 文本中的标点导致图片语法被拆散
        Matcher im = IMAGE_PATTERN.matcher(text);
        sb = new StringBuffer();
        while (im.find()) {
            String placeholder = "{{IMAGE_" + idx + "}}";
            blocks.put(placeholder, im.group());
            im.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            idx++;
        }
        im.appendTail(sb);
        return sb.toString();
    }

    private String restoreBlocks(String text, Map<String, String> blocks) {
        for (Map.Entry<String, String> e : blocks.entrySet()) {
            text = text.replace(e.getKey(), "\n" + e.getValue() + "\n");
        }
        return text;
    }

    // ========== 工具方法 ==========

    private String buildHeadingPath(String doc, String h2, String h3) {
        StringBuilder sb = new StringBuilder();
        if (doc != null && !doc.isEmpty()) sb.append(doc);
        if (h2 != null && !h2.isEmpty()) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(h2);
        }
        if (h3 != null && !h3.isEmpty()) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(h3);
        }
        return sb.toString();
    }

    // ========== 内部类 ==========

    /** 文档的一个逻辑节 */
    private static class Section {
        final String content;
        final String docTitle;
        final String h2Title;
        final String h3Title;

        Section(String c, String d, String h2, String h3) {
            this.content = c;
            this.docTitle = d;
            this.h2Title = h2;
            this.h3Title = h3;
        }
    }

    /** 带标题路径的切片 */
    public static class ChunkWithHeadings {
        public String text;
        public String headingPath;
        public int index;

        public ChunkWithHeadings(String text, String headingPath, int index) {
            this.text = text;
            this.headingPath = headingPath;
            this.index = index;
        }

        public String getText() { return text; }
        public String getHeadingPath() { return headingPath; }
        public int getIndex() { return index; }

        @Override
        public String toString() {
            return "[" + headingPath + "] " + (text.length() > 60 ? text.substring(0, 60) + "..." : text);
        }
    }
}
