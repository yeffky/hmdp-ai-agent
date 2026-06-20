# Changelog

## v2.0.0 — RAG 检索增强生成引擎 (2026-06-20)

### 🚀 新功能

#### RAG 核心引擎
- **Qdrant 向量数据库**：通过 REST API 接入，支持 collection 管理、向量 upsert、相似检索、按来源删除
- **Embedding 服务**：OpenAI 兼容格式，支持 Ollama (bge-m3) / SiliconCloud (bge-large-zh) 等多种 provider
- **文档摄取管线**：文档 → 切片 → 向量化 → 存入 Qdrant，完整自动化流程
- **检索管线**：用户查询 → 向量化 → Qdrant Top-K 检索 → 格式化上下文 → LLM 生成

#### Markdown 结构感知切片器 (`MarkdownSplitter`)
- 按 H2/H3 标题层级递归切分，保持语义完整性
- 代码块、表格保护——避免结构被截断
- 每个 chunk 携带 heading 上下文路径（如 "帮助中心 > 退款政策"）
- 兜底策略：段落 → 句子 → 字符硬切分
- 自动检测 `#` 开头文档启用 Markdown 模式

#### 知识库管理
- `POST /kb/ingest` — 文档摄取接口
- `GET /kb/stats` — 知识库统计
- `GET /kb/search` — 检索测试
- `DELETE /kb/source/{source}` — 按来源删除
- `GET /kb/health` — 健康检查
- `kb-admin.html` — 知识库管理后台 UI（种子数据导入、检索测试、RAG 问答验证）

#### RAG 增强对话
- `POST /chat/rag` — 检索增强生成端点（检索 → 拼接上下文 → LLM 生成）
- `rag-demo.html` — RAG vs 无RAG 对比 Demo 页面
- `KnowledgeRetrievalTool` — Agent 可从知识库检索信息的工具

### 🔧 修复

- **QdrantVectorStore.countPoints()** — 修复空 JSON body 导致 400 错误
- **RetrievalService** — `List.of()` → `Collections.emptyList()` (Java 8 兼容)
- **MySqlChatMemory** — 修复 DeepSeek 不兼容 `role=function` 的问题（ToolExecutionResultMessage → SystemMessage）
- **MvcConfig** — `/kb/**` 加入认证白名单

### 📝 变更

- **pom.xml** — 回退至 Java 8 + LangChain4j 0.31.0（避免 Spring Boot 2.3.12 ASM 版本冲突）
- **Nginx 配置** — `/chat/` 和 `/kb/` 代理路径精确匹配（避免拦截静态文件）
- **docker-compose.yml** — 移除已废弃的 `version` 字段

### 🏗️ 架构

```
RAG 写入管线:
  Markdown/Text → MarkdownSplitter / DocumentSplitter
    → EmbeddingService (bge-m3, 1024d)
      → QdrantVectorStore (Cosine)

RAG 检索管线:
  User Query → EmbeddingService → Qdrant.search(Top-K)
    → RetrievalService.formatContext() → LLM + Context → Answer
```

### 📦 技术栈

| 层 | 技术 |
|---|------|
| 向量数据库 | Qdrant 1.9.0 |
| Embedding | Ollama + bge-m3 (1024d) |
| LLM | DeepSeek via LangChain4j 0.31.0 |
| 后端框架 | Spring Boot 2.3.12 + MyBatis-Plus |
| Agent 框架 | LangChain4j 0.31.0 (Java 8) |
| 前端 | Vue.js 2 + Element UI + Nginx |

---

## v1.0.0 — AI 智能客服 (初始版本)

- 五层 AI Agent 架构（感知 → 决策 → 工具 → 反思 → 记忆）
- 基于 LangChain4j + DeepSeek
- 订单查询工具（OrderQueryTool）
- MySQL 持久化 ChatMemory
- 前端悬浮客服气泡
