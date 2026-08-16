package com.hmdp.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 流式续传去重器单测 — 发送端精确去重的核心行为。
 */
class StreamingResumeDedupeTest {

    @Test
    void feed_无重叠直接返回增量() {
        StreamingResumeDedupe d = new StreamingResumeDedupe("推荐朱富贵火锅，评分最高");
        assertEquals("适合聚餐", d.feed("适合聚餐"));
    }

    @Test
    void feed_末尾重叠只返回增量() {
        StreamingResumeDedupe d = new StreamingResumeDedupe("推荐朱富贵火锅，评分最高");
        // LLM 续传重复 partial 末尾「评分最高」后继续 → 只发增量
        assertEquals("，适合聚餐", d.feed("评分最高，适合聚餐"));
    }

    @Test
    void feed_重叠跨多个token累积() {
        StreamingResumeDedupe d = new StreamingResumeDedupe("推荐朱富贵火锅，评分最高");
        // token 切分：先重复「评分最」，仍在重复段（无增量）
        assertEquals("", d.feed("评分最"));
        // 继续「高」，仍完全匹配 partial 末尾
        assertEquals("", d.feed("高"));
        // 出现增量 → 只发「，适合聚餐」
        assertEquals("，适合聚餐", d.feed("，适合聚餐"));
    }

    @Test
    void feed_完全重复返回空串() {
        StreamingResumeDedupe d = new StreamingResumeDedupe("推荐朱富贵火锅");
        assertEquals("", d.feed("推荐朱富贵火锅"));
    }

    @Test
    void feed_增量发出后缓冲清空_后续直接增量() {
        StreamingResumeDedupe d = new StreamingResumeDedupe("评分最高");
        assertEquals("", d.feed("评分最高"));        // 重复段，无增量
        assertEquals("，适合聚餐", d.feed("，适合聚餐")); // 增量发出，缓冲已清
        assertEquals("好吃", d.feed("好吃"));          // 后续直接增量，不再误判
    }

    @Test
    void feed_null或空串返回空() {
        StreamingResumeDedupe d = new StreamingResumeDedupe("base");
        assertEquals("", d.feed(null));
        assertEquals("", d.feed(""));
    }

    @Test
    void feed_短文本无重叠() {
        StreamingResumeDedupe d = new StreamingResumeDedupe("推荐朱富贵火锅");
        assertEquals("，评分最高。", d.feed("，评分最高。"));
    }
}
