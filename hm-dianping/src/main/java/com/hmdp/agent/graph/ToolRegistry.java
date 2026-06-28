package com.hmdp.agent.graph;

import com.hmdp.agent.tool.KnowledgeRetrievalTool;
import com.hmdp.agent.tool.OrderQueryTool;
import com.hmdp.agent.tool.graph.DynamicQueryTool;
import com.hmdp.agent.tool.graph.GeoSearchTool;
import com.hmdp.agent.tool.graph.ShopDetailTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolMapBuilder;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.List;

/**
 * 工具注册中心 — 使用 LangGraph4j + LangChain4j 桥接，
 * 自动从 @Tool 注解提取 JSON Schema，生成 LC4jToolService。
 */
@Configuration
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    @Resource
    private KnowledgeRetrievalTool knowledgeRetrievalTool;
    @Resource
    private OrderQueryTool orderQueryTool;
    @Resource
    private DynamicQueryTool dynamicQueryTool;
    @Resource
    private GeoSearchTool geoSearchTool;
    @Resource
    private ShopDetailTool shopDetailTool;

    @Bean
    public LC4jToolService toolService() {
        LC4jToolMapBuilder<?> builder = new LC4jToolMapBuilder<>()
                .toolsFromObject(
                        knowledgeRetrievalTool,
                        orderQueryTool,
                        dynamicQueryTool,
                        geoSearchTool,
                        shopDetailTool
                );

        LC4jToolService service = new LC4jToolService(builder.toolMap());

        List<ToolSpecification> specs = service.toolSpecifications();
        log.info("ToolRegistry initialized with {} tools:", specs.size());
        for (ToolSpecification ts : specs) {
            log.info("  • {} — {}", ts.name(), ts.description());
        }
        return service;
    }
}
