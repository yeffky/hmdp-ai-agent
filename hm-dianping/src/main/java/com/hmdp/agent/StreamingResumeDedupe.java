package com.hmdp.agent;

/**
 * 流式续传去重器 — 发送端精确保证"不重复"（符合 SSE 标准精神，替代前端内容启发式去重）。
 *
 * <p>后端断点续传（attempt&gt;1）时，LLM 可能重复已生成文本的末尾几个字（续传 prompt 带来的惯性）。
 * 本器以「续传起点已发送的完整文本（base）」为权威基准，累积续传输出，在 base 末尾（去重窗口）内找
 * 「续传输出的前缀」的最长匹配（重复段可能是 base 末尾的任意一段），只发送匹配之后的增量——
 * 无论 LLM 重复多少、token 如何切分，发出去的永远是纯增量。接收端因此不需要任何内容级去重。
 */
public class StreamingResumeDedupe {

    /** 去重窗口：LLM 续传重复的通常只是 partial 末尾几字到几十字，取末尾窗口即可覆盖（超长重复退化部分去重）。 */
    private static final int REPEAT_CAP = 50;

    /** 续传起点已发送的完整文本（partial 快照），作为去重基准。 */
    private final String base;

    /** base 末尾窗口，去重匹配的搜索范围。 */
    private final String tail;

    /** 续传累积缓冲（可能仍在重复段，未到发送时机）。 */
    private final StringBuilder buf = new StringBuilder();

    public StreamingResumeDedupe(String base) {
        this.base = base;
        this.tail = base.length() > REPEAT_CAP ? base.substring(base.length() - REPEAT_CAP) : base;
    }

    /**
     * 喂入一段续传输出。
     *
     * @return 应发送的增量文本；若输出前缀仍匹配 base 末尾（重复段，无新内容）则返回空串（继续累积）。
     */
    public String feed(String token) {
        if (token == null || token.isEmpty()) return "";
        buf.append(token);
        String s = buf.toString();
        int overlap = maxPrefixMatchInTail(s);
        if (s.length() > overlap) {
            String delta = s.substring(overlap);
            buf.setLength(0);
            return delta;
        }
        return "";
    }

    /**
     * s 的前缀 与 tail 任意位置的最长匹配长度（0 = 无重叠）。
     * 重复段是 base 末尾的一段，token 切分后 s 可能只覆盖其前缀，故须在 tail 全范围搜索，
     * 而非要求 s 是 tail 的完整后缀（base.endsWith(s) 会漏掉切分不完整的重复段）。
     */
    private int maxPrefixMatchInTail(String s) {
        int overlap = 0;
        for (int i = 0; i < tail.length() && overlap < s.length(); i++) {
            int k = 0;
            while (k < s.length() && i + k < tail.length() && tail.charAt(i + k) == s.charAt(k)) k++;
            if (k > overlap) overlap = k;
        }
        return overlap;
    }
}
