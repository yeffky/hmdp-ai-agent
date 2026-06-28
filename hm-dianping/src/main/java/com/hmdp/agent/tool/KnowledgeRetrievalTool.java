package com.hmdp.agent.tool;

import com.hmdp.rag.model.SearchResult;
import com.hmdp.rag.retrieval.RetrievalService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库检索工具 — 让 AI Agent 能从向量库检索相关文档作为回答依据。
 */
@Component
public class KnowledgeRetrievalTool {

    @Resource
    private RetrievalService retrievalService;

    @Tool("从黑马点评平台知识库中检索相关信息。当用户询问平台规则、使用帮助、商家信息等通用问题时调用此工具。返回最相关的文档片段。")
    public String searchKnowledge(@P("用户的查询问题，保留原始表述") String query) {
        List<SearchResult> results = retrievalService.search(query);
        if (results.isEmpty()) {
            return "知识库中未找到相关信息。";
        }
        return results.stream()
                .map(SearchResult::toContextString)
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
