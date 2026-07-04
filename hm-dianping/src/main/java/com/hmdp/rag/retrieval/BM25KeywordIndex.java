package com.hmdp.rag.retrieval;

import com.hmdp.rag.model.DocumentChunk;
import com.hmdp.rag.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BM25 关键词检索索引 — 标准 BM25 算法实现，内存索引。
 *
 * <p>公式：Score(d, q) = Σ IDF(q_i) * TF(q_i, d)
 * <ul>
 *   <li>IDF(q_i) = log((N - n(q_i) + 0.5) / (n(q_i) + 0.5) + 1)</li>
 *   <li>TF(q_i, d) = ((k1 + 1) * tf) / (k1 * ((1 - b) + b * (dl / avgdl)) + tf)</li>
 * </ul>
 * k1 = 1.5, b = 0.75
 *
 * <p>中文分词：字符 bi-gram + 停用词过滤。
 */
public class BM25KeywordIndex {

    private static final Logger log = LoggerFactory.getLogger(BM25KeywordIndex.class);

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
            "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
            "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "些",
            "什么", "怎么", "如何", "为什么", "可以", "这个", "那个", "还是",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "and", "but", "or",
            "nor", "not", "so", "yet", "both", "either", "neither", "each", "every",
            "all", "any", "few", "more", "most", "other", "some", "such", "only",
            "own", "same", "than", "too", "very", "just", "because", "about"
    );

    /** 内部文档表示 */
    private static class Bm25Doc {
        final DocumentChunk chunk;
        final Map<String, Integer> termFreq; // term → in-doc frequency
        final int length;                     // token count

        Bm25Doc(DocumentChunk chunk, Map<String, Integer> termFreq, int length) {
            this.chunk = chunk;
            this.termFreq = termFreq;
            this.length = length;
        }
    }

    private volatile List<Bm25Doc> docs = List.of();
    private volatile Map<String, Integer> docFreq = Map.of(); // term → how many docs
    private volatile double avgdl = 1.0;
    private volatile int totalDocs = 0;

    /** 从 Qdrant chunks 全量重建索引 */
    public synchronized void rebuild(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("BM25 rebuild: empty chunks, keeping existing index");
            return;
        }

        // 1. 对每个 chunk 分词 + 词频统计
        Map<String, Integer> df = new HashMap<>();
        List<Bm25Doc> newDocs = new ArrayList<>();
        long totalLen = 0;

        for (DocumentChunk chunk : chunks) {
            if (chunk.getText() == null || chunk.getText().isBlank()) continue;
            List<String> tokens = tokenize(chunk.getText());
            if (tokens.isEmpty()) continue;

            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokens) {
                tf.merge(t, 1, Integer::sum);
            }
            // 每个 unique term 在文档中只计一次 df
            for (String t : tf.keySet()) {
                df.merge(t, 1, Integer::sum);
            }

            newDocs.add(new Bm25Doc(chunk, tf, tokens.size()));
            totalLen += tokens.size();
        }

        this.docs = newDocs;
        this.docFreq = df;
        this.totalDocs = newDocs.size();
        this.avgdl = newDocs.isEmpty() ? 1.0 : (double) totalLen / newDocs.size();

        log.info("BM25 index rebuilt: {} docs, avgdl={:.1f}, vocab={}",
                totalDocs, avgdl, docFreq.size());
    }

    /** 索引中文档数量 */
    public int size() { return totalDocs; }

    /** BM25 检索 → 返回 topK SearchResult */
    public List<SearchResult> search(String query, int topK) {
        if (docs.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return List.of();

        // 计算每个 doc 的 BM25 分数
        List<ScoredDoc> scored = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Bm25Doc d = docs.get(i);
            double score = bm25Score(d, queryTokens);
            if (score > 0) {
                scored.add(new ScoredDoc(i, score));
            }
        }

        // 按分数降序 → topK
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        int limit = Math.min(topK, scored.size());

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ScoredDoc sd = scored.get(i);
            Bm25Doc d = docs.get(sd.idx);
            results.add(new SearchResult(d.chunk, sd.score));
        }
        return results;
    }

    private double bm25Score(Bm25Doc doc, List<String> queryTokens) {
        double score = 0;
        for (String term : queryTokens) {
            int nQi = docFreq.getOrDefault(term, 0);
            if (nQi == 0) continue;
            double idf = Math.log((totalDocs - nQi + 0.5) / (nQi + 0.5) + 1.0);
            int tf = doc.termFreq.getOrDefault(term, 0);
            if (tf == 0) continue;
            double numerator = (K1 + 1) * tf;
            double denominator = K1 * ((1 - B) + B * (doc.length / avgdl)) + tf;
            score += idf * (numerator / denominator);
        }
        return score;
    }

    /** 中文 bi-gram + 停用词过滤 */
    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        // 去标点、转小写
        String cleaned = text.replaceAll("[\\p{P}\\p{S}\\s]+", "").toLowerCase();
        if (cleaned.isEmpty()) return List.of();

        List<String> tokens = new ArrayList<>();

        // 英文单词（连续字母）
        StringBuilder alpha = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c >= 'a' && c <= 'z') {
                alpha.append(c);
            } else {
                if (alpha.length() > 1) {
                    String word = alpha.toString();
                    if (!STOP_WORDS.contains(word)) tokens.add(word);
                }
                alpha.setLength(0);
                // 中文字符 bi-gram
                if (i + 1 < cleaned.length() && isCJK(cleaned.charAt(i + 1))) {
                    String bg = "" + c + cleaned.charAt(i + 1);
                    if (!STOP_WORDS.contains(bg)) tokens.add(bg);
                }
            }
        }
        if (alpha.length() > 1) {
            String word = alpha.toString();
            if (!STOP_WORDS.contains(word)) tokens.add(word);
        }

        return tokens.isEmpty() ? List.of() : tokens;
    }

    private static boolean isCJK(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    // ======== 内部类 ========

    private static class ScoredDoc {
        final int idx;
        final double score;
        ScoredDoc(int idx, double score) { this.idx = idx; this.score = score; }
    }
}
