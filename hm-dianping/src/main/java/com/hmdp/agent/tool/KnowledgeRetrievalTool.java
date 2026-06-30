package com.hmdp.agent.tool;

import com.hmdp.rag.model.SearchResult;
import com.hmdp.rag.retrieval.RetrievalService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库检索工具 — 让 AI Agent 能从向量库检索相关文档作为回答依据。
 */
@Component
public class KnowledgeRetrievalTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalTool.class);

    @Resource
    private RetrievalService retrievalService;

    @Tool("查询平台规则、操作流程、使用帮助。仅当用户询问「如何/怎么/怎样」类操作问题（如如何领券、怎么退款、如何签到）时才调用。商家信息、优惠券查询、订单查询等数据类问题请用 text2Sql。")
    public String searchKnowledge(@P("用户的查询问题，保留原始表述") String query) {
        List<SearchResult> results = retrievalService.search(query);
        if (results.isEmpty()) {
            return "知识库中未找到相关信息。";
        }
        log.info("searchKnowledge found {} results for query '{}'", results.size(), query);
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String text = r.getChunk().getText();
            log.info("Result {}: score={}, title={}, textLen={}, textPreview={}",
                    i, r.getScore(),
                    r.getChunk().getTitle(),
                    text != null ? text.length() : -1,
                    text != null && text.length() > 100 ? text.substring(0, 100) + "..." : text);
        }
        return results.stream()
                .map(SearchResult::toContextString)
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
