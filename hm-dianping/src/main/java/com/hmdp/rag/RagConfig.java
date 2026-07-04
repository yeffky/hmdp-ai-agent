package com.hmdp.rag;

import com.hmdp.rag.document.DocumentConverter;
import com.hmdp.rag.document.DocumentPipeline;
import com.hmdp.rag.document.IngestionStateManager;
import com.hmdp.rag.embedding.EmbeddingService;
import com.hmdp.rag.embedding.OpenAiEmbeddingService;
import com.hmdp.rag.ingestion.IngestionService;
import com.hmdp.rag.rerank.LLMReranker;
import com.hmdp.rag.rerank.Reranker;
import com.hmdp.rag.retrieval.BM25KeywordIndex;
import com.hmdp.rag.retrieval.LLMQueryRewriter;
import com.hmdp.rag.retrieval.RetrievalService;
import com.hmdp.rag.splitter.AdaptiveSplitter;
import com.hmdp.rag.store.QdrantVectorStore;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.Set;

/**
 * RAG 模块 Spring 配置 — 装配 Embedding / VectorStore / Ingestion / Retrieval 全部 Bean。
 */
@Configuration
public class RagConfig {

    // ========== Qdrant ==========
    @Value("${rag.qdrant.host}")
    private String qdrantHost;
    @Value("${rag.qdrant.port}")
    private int qdrantPort;
    @Value("${rag.qdrant.collection}")
    private String qdrantCollection;
    @Value("${rag.qdrant.vector-size}")
    private int qdrantVectorSize;
    @Value("${rag.qdrant.distance}")
    private String qdrantDistance;

    // ========== Embedding ==========
    @Value("${rag.embedding.base-url}")
    private String embedBaseUrl;
    @Value("${rag.embedding.api-key}")
    private String embedApiKey;
    @Value("${rag.embedding.model}")
    private String embedModel;

    // ========== Retrieval ==========
    @Value("${rag.retrieval.top-k}")
    private int retrievalTopK;
    @Value("${rag.retrieval.score-threshold}")
    private double retrievalScoreThreshold;

    // ========== Splitter ==========
    @Value("${rag.splitter.chunk-size}")
    private int splitterChunkSize;
    @Value("${rag.splitter.chunk-overlap}")
    private int splitterChunkOverlap;

    // ========== Document Ingestion ==========
    @Value("${rag.document.state-file:./rag-doc-state.json}")
    private String stateFilePath;
    @Value("${rag.document.markitdown-command:markitdown}")
    private String markitdownCommand;
    @Value("${rag.document.timeout-seconds:120}")
    private int markitdownTimeout;
    @Value("${rag.document.watch-file-extensions:pdf,docx,doc,pptx,ppt,xlsx,xls,html,htm,csv,json,xml,epub,md,txt}")
    private String watchExtensionsStr;

    // ========== Bean 定义 ==========

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public EmbeddingService embeddingService(RestTemplate restTemplate) {
        return new OpenAiEmbeddingService(restTemplate, embedBaseUrl, embedApiKey,
                embedModel, qdrantVectorSize);
    }

    @Bean
    public QdrantVectorStore qdrantVectorStore(RestTemplate restTemplate) {
        QdrantVectorStore store = new QdrantVectorStore(restTemplate, qdrantHost,
                qdrantPort, qdrantCollection, qdrantVectorSize, qdrantDistance);
        // 启动时自动确保集合存在
        store.ensureCollection();
        return store;
    }

    @Bean
    public AdaptiveSplitter adaptiveSplitter() {
        return new AdaptiveSplitter(splitterChunkSize, splitterChunkOverlap);
    }

    @Bean
    public BM25KeywordIndex bm25KeywordIndex() {
        return new BM25KeywordIndex();
    }

    @Bean
    public IngestionService ingestionService(AdaptiveSplitter adaptiveSplitter,
                                              EmbeddingService embedding,
                                              QdrantVectorStore store,
                                              BM25KeywordIndex bm25Index) {
        return new IngestionService(adaptiveSplitter, embedding, store, bm25Index);
    }

    @Bean
    public LLMQueryRewriter llmQueryRewriter(@Lazy OpenAiChatModel model) {
        return new LLMQueryRewriter(model);
    }

    @Bean
    public Reranker llmReranker(@Lazy OpenAiChatModel model) {
        return new LLMReranker(model);
    }

    @Bean
    public RetrievalService retrievalService(EmbeddingService embedding,
                                              QdrantVectorStore store,
                                              LLMQueryRewriter rewriter,
                                              BM25KeywordIndex bm25Index,
                                              Reranker reranker) {
        return new RetrievalService(embedding, store, rewriter, bm25Index, reranker,
                retrievalTopK, retrievalScoreThreshold);
    }

    // ========== Document Ingestion Beans ==========

    @Bean
    public DocumentConverter documentConverter() {
        return new DocumentConverter(markitdownCommand, markitdownTimeout);
    }

    @Bean
    public IngestionStateManager ingestionStateManager() {
        return new IngestionStateManager(Path.of(stateFilePath).toAbsolutePath());
    }

    @Bean
    public DocumentPipeline documentPipeline(DocumentConverter converter,
                                              IngestionService ingestionService,
                                              QdrantVectorStore vectorStore,
                                              IngestionStateManager stateManager) {
        Set<String> extensions = DocumentPipeline.parseExtensions(watchExtensionsStr);
        return new DocumentPipeline(converter, ingestionService, vectorStore,
                stateManager, extensions);
    }
}
