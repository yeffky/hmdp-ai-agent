package com.hmdp.rag.splitter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自适应文档切片器 —— 优先按文档结构标记切分，无结构时递归降级，所有 chunk 统一施加滑动窗口重叠。
 *
 * <h3>结构检测优先级</h3>
 * <table>
 *   <tr><th>层级</th><th>Markdown</th><th>中文 PDF/Word</th></tr>
 *   <tr><td>L1 章</td><td>{@code # Title}</td><td>第一章 / 第1章 / 前言 / 绪论 / 摘要</td></tr>
 *   <tr><td>L2 节</td><td>{@code ## Title}</td><td>第一节 / （一） / (一)</td></tr>
 *   <tr><td>L3 小节</td><td>{@code ### Title}</td><td>一、 / 1. / 1.1</td></tr>
 * </table>
 *
 * <h3>算法</h3>
 * <ol>
 *   <li>保护特殊块（代码块/表格/图片）→ 占位符</li>
 *   <li>行级扫描结构标记 → 构建层级树</li>
 *   <li>按结构边界切分：内容 ≤ chunkSize 直接输出，否则递归切分</li>
 *   <li>统一施加滑动窗口重叠</li>
 *   <li>还原占位符</li>
 * </ol>
 */
public class AdaptiveSplitter {

    private final int chunkSize;
    private final int chunkOverlap;

    // ---- 结构标记模式（均锚定行首） ----

    /** L1: Markdown H1 / 第X章 / 第X部分 / 前言 绪论 摘要 结论 参考文献 */
    private static final Pattern L1_PATTERN = Pattern.compile(
            "(?m)^(?:#\\s+(.+)" +
            "|第[一二三四五六七八九十百千零\\d]+章\\s*(.+)" +
            "|第[一二三四五六七八九十百千零\\d]+部分\\s*(.+)" +
            "|(前言|绪论|引言|摘要|结论|结语|参考文献))\\s*$");

    /** L2: Markdown H2 / 第X节 / （一） */
    private static final Pattern L2_PATTERN = Pattern.compile(
            "(?m)^(?:#{2}\\s+(.+)" +
            "|第[一二三四五六七八九十百千零\\d]+节\\s*(.+)" +
            "|[（(][一二三四五六七八九十]+[）)]\\s*(.+))\\s*$");

    /** L3: Markdown H3 / 一、 / 1. / 1.1 */
    private static final Pattern L3_PATTERN = Pattern.compile(
            "(?m)^(?:#{3}\\s+(.+)" +
            "|[一二三四五六七八九十]+、\\s*(.+)" +
            "|\\d+\\.\\d+\\s+(.+)" +
            "|\\d+\\.\\s+(.+))\\s*$");

    // ---- 特殊块保护 ----
    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern TABLE_BLOCK = Pattern.compile(
            "(^\\|.+\\|\\s*$\\n^\\|[\\s:-]+\\|\\s*$(\\n^\\|.+\\|\\s*$)*)",
            Pattern.MULTILINE);
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^]]*\\]\\([^)]+\\)");

    // ---- 递归降级分隔符 ----
    private static final String[] SENTENCE_SEPS = {"\n\n", "\n", "。", "！", "？", "；", "，"};

    public AdaptiveSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    // ========== 公开 API ==========

    /** 切分文本为带标题路径的切片 */
    public List<ChunkResult> split(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String text = content.trim();

        // 1. 保护特殊块
        Map<String, String> blocks = new LinkedHashMap<>();
        text = protectSpecialBlocks(text, blocks);

        // 2. 扫描结构标记
        List<Mark> marks = scanMarks(text);
        List<ChunkResult> chunks;

        if (marks.isEmpty()) {
            // 无结构 → 递归降级
            List<String> parts = recursiveSplit(text);
            chunks = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                chunks.add(new ChunkResult(parts.get(i), "", i));
            }
        } else {
            // 有结构 → 按结构边界切分
            chunks = splitByStructure(text, marks);
        }

        // 3. 统一重叠
        chunks = applyOverlap(chunks);

        // 4. 还原特殊块
        for (ChunkResult c : chunks) {
            c.text = restoreBlocks(c.text, blocks);
        }

        // 5. 重新编号
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).index = i;
        }

        return chunks;
    }

    /** 仅返回纯文本列表 */
    public List<String> splitPlain(String content) {
        List<ChunkResult> chunks = split(content);
        List<String> result = new ArrayList<>();
        for (ChunkResult c : chunks) result.add(c.text);
        return result;
    }

    // ========== 结构扫描 ==========

    /** 行级扫描，收集所有结构标记 */
    private List<Mark> scanMarks(String text) {
        List<Mark> marks = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int pos = 0;

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];
            String trimmed = line.trim();

            // L1
            Matcher m1 = L1_PATTERN.matcher(trimmed);
            if (m1.matches()) {
                String title = extractGroup(m1, 1, 2, 3, 4);
                if (title != null && !title.isEmpty()) {
                    marks.add(new Mark(1, pos, title, line));
                }
            } else {
                // L2
                Matcher m2 = L2_PATTERN.matcher(trimmed);
                if (m2.matches()) {
                    String title = extractGroup(m2, 1, 2, 3);
                    if (title != null && !title.isEmpty()) {
                        marks.add(new Mark(2, pos, title, line));
                    }
                } else {
                    // L3
                    Matcher m3 = L3_PATTERN.matcher(trimmed);
                    if (m3.matches()) {
                        String title = extractGroup(m3, 1, 2, 3, 4);
                        if (title != null && !title.isEmpty()) {
                            marks.add(new Mark(3, pos, title, line));
                        }
                    }
                }
            }

            pos += line.length() + 1; // +1 for the \n we split by
        }

        return marks;
    }

    /** 从多个可选组中提取非空值 */
    private static String extractGroup(Matcher m, int... groups) {
        for (int g : groups) {
            String v = m.group(g);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    // ========== 结构切分 ==========

    /**
     * 按标记边界切分文本。内容位于相邻标记之间的间隙（不重叠）。
     * 每块内容继承当前位置的层级上下文作为 headingPath。
     */
    private List<ChunkResult> splitByStructure(String text, List<Mark> marks) {
        List<ChunkResult> chunks = new ArrayList<>();
        String curL1 = "", curL2 = "", curL3 = "";

        // 处理第一个标记之前的前导内容
        int prevEnd = 0;
        if (marks.get(0).pos > 0) {
            String prefix = text.substring(0, marks.get(0).pos).trim();
            if (!prefix.isEmpty()) {
                addChunk(chunks, prefix, "");
            }
        }

        for (int i = 0; i < marks.size(); i++) {
            Mark m = marks.get(i);

            // 内容 = 从上一个标记结束到当前标记开始（相邻标记之间的间隙）
            int gapStart = prevEnd;
            int gapEnd = m.pos;
            if (gapStart < gapEnd) {
                String content = text.substring(gapStart, gapEnd).trim();
                if (!content.isEmpty()) {
                    String headingPath = buildHeadingPath(curL1, curL2, curL3);
                    addChunk(chunks, content, headingPath);
                }
            }

            // 更新层级上下文（准备给下一段内容用）
            if (m.level == 1) { curL1 = m.title; curL2 = ""; curL3 = ""; }
            else if (m.level == 2) { curL2 = m.title; curL3 = ""; }
            else { curL3 = m.title; }

            // 当前标记行结束位置
            prevEnd = m.pos + m.rawLine.length();
            // 跳过标记行后的换行符
            if (prevEnd < text.length() && text.charAt(prevEnd) == '\n') prevEnd++;
            else if (prevEnd + 1 < text.length() && text.charAt(prevEnd) == '\r' && text.charAt(prevEnd + 1) == '\n') prevEnd += 2;
        }

        // 处理最后一个标记之后的尾部内容
        if (prevEnd < text.length()) {
            String tail = text.substring(prevEnd).trim();
            if (!tail.isEmpty()) {
                String headingPath = buildHeadingPath(curL1, curL2, curL3);
                addChunk(chunks, tail, headingPath);
            }
        }

        return chunks;
    }

    /** 将文本加入 chunks，超过 chunkSize 则递归切分 */
    private void addChunk(List<ChunkResult> chunks, String content, String headingPath) {
        if (content.length() > chunkSize) {
            List<String> parts = recursiveSplit(content);
            for (String part : parts) {
                chunks.add(new ChunkResult(part, headingPath, 0));
            }
        } else {
            chunks.add(new ChunkResult(content, headingPath, 0));
        }
    }

    // ========== 递归降级切分 ==========

    /** 递归切分：段落 → 句子 → 硬截断 */
    private List<String> recursiveSplit(String text) {
        if (text.length() <= chunkSize) {
            return Collections.singletonList(text);
        }

        for (String sep : SENTENCE_SEPS) {
            List<String> segments = splitBy(text, sep);
            if (segments.size() > 1) {
                List<String> merged = mergeSegments(segments, sep);
                if (merged.size() > 1) {
                    List<String> result = new ArrayList<>();
                    for (String mg : merged) {
                        if (mg.length() > chunkSize) {
                            result.addAll(recursiveSplit(mg));
                        } else {
                            result.add(mg);
                        }
                    }
                    return result;
                }
            }
        }

        return hardSplit(text);
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

    /** 合并短片段直到接近 chunkSize */
    private List<String> mergeSegments(List<String> segments, String separator) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String seg : segments) {
            String trimmed = seg.trim();
            if (trimmed.isEmpty()) continue;

            String candidate = buffer.length() > 0
                    ? buffer + separator + trimmed
                    : trimmed;

            if (candidate.length() <= chunkSize) {
                if (buffer.length() > 0) buffer.append(separator);
                buffer.append(trimmed);
            } else {
                if (buffer.length() > 0) {
                    result.add(buffer.toString());
                    buffer.setLength(0);
                }
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

    /** 硬截断 */
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

    // ========== 滑动窗口重叠 ==========

    private List<ChunkResult> applyOverlap(List<ChunkResult> chunks) {
        if (chunks.size() <= 1 || chunkOverlap <= 0) return chunks;

        for (int i = 1; i < chunks.size(); i++) {
            String prevText = chunks.get(i - 1).text;
            if (prevText.length() > chunkOverlap) {
                String overlapText = prevText.substring(prevText.length() - chunkOverlap);
                chunks.get(i).text = overlapText + "\n...\n" + chunks.get(i).text;
            }
        }
        return chunks;
    }

    // ========== 特殊块保护 ==========

    private String protectSpecialBlocks(String text, Map<String, String> blocks) {
        int idx = 0;

        Matcher cm = CODE_BLOCK.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (cm.find()) {
            String placeholder = "{{CODE_" + idx + "}}";
            blocks.put(placeholder, cm.group());
            cm.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            idx++;
        }
        cm.appendTail(sb);
        text = sb.toString();

        Matcher tm = TABLE_BLOCK.matcher(text);
        sb = new StringBuffer();
        while (tm.find()) {
            String placeholder = "{{TABLE_" + idx + "}}";
            blocks.put(placeholder, tm.group());
            tm.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            idx++;
        }
        tm.appendTail(sb);
        text = sb.toString();

        Matcher im = IMAGE_PATTERN.matcher(text);
        sb = new StringBuffer();
        while (im.find()) {
            String placeholder = "{{IMG_" + idx + "}}";
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

    private static String buildHeadingPath(String l1, String l2, String l3) {
        StringBuilder sb = new StringBuilder();
        appendPath(sb, l1);
        appendPath(sb, l2);
        appendPath(sb, l3);
        return sb.toString();
    }

    private static void appendPath(StringBuilder sb, String title) {
        if (title != null && !title.isEmpty()) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(title);
        }
    }

    // ========== 内部类 ==========

    /** 结构标记 */
    private static class Mark {
        final int level;       // 1/2/3
        final int pos;         // 在文本中的字节偏移
        final String title;    // 标题文本
        final String rawLine;  // 原始行（用于计算内容起点偏移）

        Mark(int level, int pos, String title, String rawLine) {
            this.level = level;
            this.pos = pos;
            this.title = title;
            this.rawLine = rawLine;
        }
    }

    /** 切片结果 */
    public static class ChunkResult {
        public String text;
        public String headingPath;
        public int index;

        public ChunkResult(String text, String headingPath, int index) {
            this.text = text;
            this.headingPath = headingPath;
            this.index = index;
        }

        public String getText() { return text; }
        public String getHeadingPath() { return headingPath; }
        public int getIndex() { return index; }

        @Override
        public String toString() {
            String path = headingPath != null && !headingPath.isEmpty() ? "[" + headingPath + "] " : "";
            return path + (text.length() > 80 ? text.substring(0, 80) + "..." : text);
        }
    }
}
