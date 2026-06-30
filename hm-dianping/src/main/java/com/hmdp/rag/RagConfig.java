package com.hmdp.rag;

import com.hmdp.rag.document.DocumentConverter;
import com.hmdp.rag.document.DocumentPipeline;
import com.hmdp.rag.document.IngestionStateManager;
import com.hmdp.rag.embedding.EmbeddingService;
import com.hmdp.rag.embedding.OpenAiEmbeddingService;
import com.hmdp.rag.ingestion.IngestionService;
import com.hmdp.rag.retrieval.LLMQueryRewriter;
import com.hmdp.rag.retrieval.RetrievalService;
import com.hmdp.rag.splitter.DocumentSplitter;
import com.hmdp.rag.splitter.MarkdownSplitter;
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
    public DocumentSplitter documentSplitter() {
        return new DocumentSplitter(splitterChunkSize, splitterChunkOverlap);
    }

    @Bean
    public MarkdownSplitter markdownSplitter() {
        return new MarkdownSplitter(splitterChunkSize, splitterChunkOverlap);
    }

    @Bean
    public IngestionService ingestionService(DocumentSplitter simpleSplitter,
                                              MarkdownSplitter markdownSplitter,
                                              EmbeddingService embedding,
                                              QdrantVectorStore store) {
        return new IngestionService(simpleSplitter, markdownSplitter, embedding, store);
    }

    @Bean
    public LLMQueryRewriter llmQueryRewriter(@Lazy OpenAiChatModel model) {
        return new LLMQueryRewriter(model);
    }

    @Bean
    public RetrievalService retrievalService(EmbeddingService embedding,
                                              QdrantVectorStore store,
                                              LLMQueryRewriter rewriter) {
        return new RetrievalService(embedding, store, rewriter,
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
