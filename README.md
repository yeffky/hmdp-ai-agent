# hmdp-ai-agent — 基于 LangGraph4j 的 ReAct AI Agent

Spring Boot + LangGraph4j + LangChain4j + DeepSeek 构建的 O2O 生活服务 AI Agent，集成 RAG 混合检索、Text2SQL 动态查询、增量 Checkpoint 存储与流式 SSE 输出。

## 核心架构

```
用户请求 → SSE Controller → LangGraph4j StateGraph
                ↓
  ┌──────────────────────────────────────────────────────┐
  │  Context → Planner → Executor → Observer → Judge    │
  │              ↑           ↓          ↓         ↓      │
  │              └── replan ←──┴── retryGate ←──┘       │
  │                              ↓                      │
  │                           Answer → [SSE 流式输出]     │
  └──────────────────────────────────────────────────────┘
                ↓
  DeltaPostgresSaver (增量 checkpoint 存储) → PostgreSQL
```

## 技术亮点

### ReAct Agent 编排
- **六节点状态机**：LangGraph4j StateGraph + Delta Channel，条件路由支持空结果扩大搜索、错误分流（RETRYABLE/USER_FIXABLE/FATAL）、重规划等多分支流程，10+ 工具调用
- **人机协同中断恢复**：checkpoint 持久化执行上下文，人工确认后 updateState 注入决策恢复状态机，LLM 语义匹配校验相关性
- **工具异常体系**：ToolException 显式抛出替代正则字符串误判，langchain4j `isError()` 检测 re-throw

### 增量 Checkpoint 存储（DeltaPostgresSaver）
- 仅序列化状态变更（messages 差集 + 变化的标量），周期性全量快照锚点，图初始化耗时由 14s 降至 1s 以内
- 跨实例恢复：多实例共享 PostgreSQL 存储，checkpoint resume 无需粘性会话

### Text2SQL 动态查询
- LLM 选表 → 生成 SQL → 安全校验 → 执行，自动强制 LIMIT + COUNT 预检防止全表扫描
- 商家类型/地址等行业术语映射 + Redis 缓存表 Schema，提升 NL2SQL 匹配精度

### RAG 混合检索
```
文档 → 自适应切片器 → Embedding → Qdrant
查询 → LLM 改写 → BM25+语义双路召回 → RRF融合 → LLM重排序
```
- 自适应切片器支持 Markdown 标题 + 中文章节标记（第一章/一、/1.1）多层级降级
- 文档自动摄入：文件监控 → markitdown 转换 → SHA256 去重 → 幂等写入 Qdrant

### 上下文管理
- 滑动窗口压缩（LLM 摘要 + 硬上限裁剪），最近 3 轮全量对话
- PostgreSQL ILIKE 关键词检索实现按需历史回溯

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.3.12 |
| AI Agent | LangGraph4j 1.8.19 + LangChain4j 1.16.2 |
| LLM | DeepSeek Chat / DeepSeek-V4-Pro |
| 数据库 | MySQL 5.x + PostgreSQL (checkpoint) |
| 缓存 | Redis 6+ (Jedis) |
| 向量数据库 | Qdrant 1.9.0 (REST API) |
| Embedding | Ollama + bge-m3 (1024d) |
| 前端 | Vue.js 2.x + Element UI |
| 反向代理 | Nginx |

## 快速开始

### 环境要求
- JDK 8+ / Maven 3.6+
- MySQL 5.7+ / PostgreSQL / Redis 6+
- Docker (Qdrant) / Ollama (Embedding)

### 启动依赖

```bash
docker compose up -d                        # Qdrant
ollama serve && ollama pull bge-m3          # Embedding
```

### 配置

```bash
cp src/main/resources/application.example.yaml src/main/resources/application.yaml
```

编辑 `application.yaml`，填入 MySQL / Redis / PostgreSQL / DeepSeek API Key 等配置。

> `application.yaml` 已加入 `.gitignore`，不会被提交。

### 启动

```bash
cd hm-dianping
mvn spring-boot:run
```

## 项目结构

```
hm-dianping/src/main/java/com/hmdp/
├── agent/
│   ├── graph/                          # LangGraph4j 状态机
│   │   ├── GraphConfig.java            # 图编译 + DeltaPostgresSaver
│   │   ├── ToolRegistry.java           # 工具注册
│   │   ├── checkpoint/DeltaPostgresSaver.java  # 增量 checkpoint
│   │   ├── error/                      # 异常分类 + ToolException
│   │   ├── nodes/                      # 六节点 (Context/Planner/Executor/Observer/Judge/Answer)
│   │   └── state/                      # ReActAgentState + StateSchema
│   ├── memory/context/                 # 滑动窗口 + 上下文管理
│   └── tool/                           # Agent 工具
│       ├── HistorySearchTool.java      # 历史回溯
│       ├── OrderQueryTool.java         # 订单查询
│       ├── QueueTicketTool.java        # 排队取号
│       ├── ShopSearchTool.java         # 商铺搜索
│       ├── KnowledgeRetrievalTool.java # RAG 检索
│       └── graph/                      # Text2SqlTool / GeoSearchTool
├── rag/                                # RAG 引擎
│   ├── splitter/AdaptiveSplitter.java  # 自适应切片
│   ├── retrieval/RetrievalService.java # 混合检索 + RRF
│   ├── store/QdrantVectorStore.java    # Qdrant 客户端
│   ├── ingestion/                      # 文档摄入管线
│   └── embedding/                      # Embedding 服务
└── controller/
    ├── ReactStreamController.java      # /chat/react/stream SSE
    └── ChatRagController.java          # /chat/rag /chat/history
```

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/react/stream` | ReAct Agent SSE 流式对话 |
| GET | `/chat/history` | 对话历史（分页） |
| POST | `/kb/ingest` | RAG 文档摄取 |
| GET | `/kb/stats` | 知识库统计 |
| GET | `/kb/search` | 检索测试 |
| GET | `/kb/health` | 健康检查 |

## License

仅供学习交流使用。
