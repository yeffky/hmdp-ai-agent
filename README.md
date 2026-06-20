# 黑马点评 (hmdp) — AI 智能客服 with RAG 检索增强生成

基于 Spring Boot + MyBatis-Plus + Redis 的本地生活点评平台，原项目为"黑马点评"课程项目。本项目进行了 **AI Agent 架构升级** 与 **RAG 检索增强生成引擎** 的完整实现。

## 核心亮点

### 1. RAG 检索增强生成引擎

```
文档摄取管线:  Markdown/Text → MarkdownSplitter → EmbeddingService (bge-m3) → QdrantVectorStore
检索生成管线:  User Query → EmbeddingService → Qdrant Top-K 检索 → Context + LLM → Answer
```

| 组件 | 技术 | 说明 |
|------|------|------|
| **向量数据库** | Qdrant 1.9.0 | REST API, Cosine 相似度检索 |
| **Embedding** | Ollama + bge-m3 | 1024 维中文语义向量 |
| **文档切片** | MarkdownSplitter | H2/H3 标题感知递归切分，代码块/表格保护 |
| **检索服务** | RetrievalService | 查询向量化 → Top-K 检索 → 上下文格式化 |
| **摄取管线** | IngestionService | 文档 → 切片 → 向量化 → 入库（支持 Markdown 自动检测） |
| **混合切片** | DocumentSplitter | 段落 → 句子 → 字符 递归切片器（非 Markdown 兜底） |

### 2. 五层 AI Agent 架构（基于 LangChain4j）

```
用户消息 → 感知层 → 决策引导 → 工具层 → 自我反思 → 记忆层 → 返回
```

| 层级 | 职责 | 技术实现 |
|------|------|----------|
| **感知层** | 意图识别（GENERAL_QA / ORDER_QUERY / COMPLAINT） | System Prompt 引导 LLM 分类 |
| **工具层** | 订单查询 + 知识库检索 | `@Tool` 注解，LLM 自动决策调用 |
| **记忆层** | 短期对话窗口 + MySQL 持久化 | 自定义 `ChatMemory`，修复 DeepSeek role=function 兼容 |
| **决策引导** | 角色定义、路由规则、SOP | `@SystemMessage` + AiServices |
| **自我反思** | 输出质量自检、错误修正 | `ReflectionGuard` 后处理 |

### 3. 知识库管理

| 端点 | 方法 | 说明 |
|------|------|------|
| `/kb/ingest` | POST | 文档摄取到向量库 |
| `/kb/stats` | GET | 知识库统计（向量数） |
| `/kb/search?q=&topK=` | GET | 检索测试 |
| `/kb/source/{source}` | DELETE | 按来源删除 |
| `/kb/health` | GET | 健康检查 |

前端管理后台：`/kb-admin.html`（种子数据导入、检索测试、RAG 问答验证）

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.3.12 |
| ORM | MyBatis-Plus 3.4.3 |
| 缓存/分布式锁 | Redis + Redisson 3.13.6 |
| AI Agent 框架 | LangChain4j 0.31.0 |
| LLM | DeepSeek Chat API |
| 向量数据库 | Qdrant 1.9.0 (REST API) |
| Embedding 模型 | Ollama + bge-m3 (1024d) |
| 前端 | Vue.js 2.x + Element UI + Axios |
| 数据库 | MySQL 5.x |
| 反向代理 | Nginx |

## 项目结构

```
hmdp-ai-agent/
├── docker-compose.yml              # Qdrant 一键部署
├── CHANGELOG.md                    # 版本更新记录
├── hm-dianping/                    # 后端 Spring Boot 项目
│   └── src/main/java/com/hmdp/
│       ├── agent/                  # AI Agent 模块
│       │   ├── AgentConfig.java
│       │   ├── CustomerServiceAgent.java
│       │   ├── perception/IntentType.java
│       │   ├── tool/
│       │   │   ├── OrderQueryTool.java        # 订单查询工具
│       │   │   └── KnowledgeRetrievalTool.java # RAG 知识库检索工具
│       │   ├── memory/MySqlChatMemory.java     # 记忆层（含 DeepSeek 兼容修复）
│       │   └── guard/ReflectionGuard.java
│       ├── rag/                    # ★ RAG 引擎模块
│       │   ├── RagConfig.java
│       │   ├── model/              # DocumentChunk / SearchResult / IngestionRequest
│       │   ├── embedding/          # EmbeddingService 接口 + OpenAI 兼容实现
│       │   ├── store/QdrantVectorStore.java    # Qdrant REST 客户端
│       │   ├── splitter/
│       │   │   ├── MarkdownSplitter.java       # Markdown 结构感知切片
│       │   │   └── DocumentSplitter.java       # 通用递归切片
│       │   ├── ingestion/IngestionService.java # 摄取管线
│       │   └── retrieval/RetrievalService.java # 检索管线
│       ├── controller/
│       │   ├── ChatRagController.java          # /chat/rag RAG 增强端点
│       │   ├── ChatController.java             # /chat/send 通用端点
│       │   └── KnowledgeBaseController.java    # /kb/* 知识库管理
│       ├── config/ entity/ mapper/ service/ utils/
│       └── resources/
│           ├── application.example.yaml        # 配置模板
│           └── application.yaml                # 本地配置（gitignore）
├── nginx-1.18.0/html/hmdp/
│   ├── kb-admin.html               # 知识库管理后台
│   ├── rag-demo.html               # RAG vs 无RAG 对比 Demo
│   ├── js/chat.js                  # 客服聊天组件（已接入 RAG）
│   └── css/chat.css
└── .gitignore
```

## 快速开始

### 1. 环境要求

- JDK 8+
- Maven 3.6+
- MySQL 5.7+
- Redis 6+
- Docker + Docker Compose（Qdrant）
- Ollama（Embedding 模型）

### 2. 启动依赖服务

```bash
# 启动 Qdrant
docker compose up -d

# 启动 Ollama 并拉取 Embedding 模型
ollama serve
ollama pull bge-m3
```

### 3. 数据库初始化

执行 `src/main/resources/db/hmdp.sql` 建表并导入种子数据。

### 4. 配置

复制 `application.example.yaml` → `application.yaml`，按实际环境填入配置：

```yaml
# ========== MySQL 数据库 ==========
spring:
  datasource:
    driver-class-name: com.mysql.jdbc.Driver
    url: jdbc:mysql://your-mysql-host:3306/hmdp?useSSL=false&serverTimezone=UTC
    username: root
    password: your-database-password

# ========== Redis ==========
  redis:
    host: your-redis-host
    port: 6379
    password: your-redis-password
    lettuce:
      pool:
        max-active: 10
        max-idle: 10
        min-idle: 1

# ========== AI 大模型 ==========
deepseek:
  api-key: your-deepseek-api-key       # https://platform.deepseek.com/api_keys
  base-url: https://api.deepseek.com/v1
  model: deepseek-chat
  temperature: 0.7
  max-tokens: 2000

# ========== RAG 检索增强 ==========
rag:
  # 向量数据库
  qdrant:
    host: http://localhost              # Qdrant 服务地址
    port: 6333                          # REST API 端口
    collection: hmdp_knowledge          # 集合名称
    vector-size: 1024                   # bge-m3 = 1024, 其他模型按需调整
    distance: Cosine

  # Embedding 模型（OpenAI 兼容格式）
  embedding:
    base-url: http://localhost:11434/v1 # Ollama 默认地址
    api-key: ollama                     # Ollama 不需要真实 key
    model: bge-m3                       # 中文推荐 bge-m3 / bge-large-zh-v1.5

  # 检索参数
  retrieval:
    top-k: 5                            # 每次检索返回的文档片段数
    score-threshold: 0.3                # 最低相似度阈值（0~1）

  # 文档切片
  splitter:
    chunk-size: 500                     # 每个 chunk 的目标字数
    chunk-overlap: 50                   # 相邻 chunk 重叠字数
```

> 💡 所有含密码/密钥的配置项必须填写真实值。`application.yaml` 已加入 `.gitignore`，不会被提交到 Git。

### 5. 启动

```bash
# 后端
cd hm-dianping/hm-dianping
mvn spring-boot:run

# 前端
nginx -c /path/to/nginx-local.conf
```

访问 `http://localhost:8080`

### 6. 初始化知识库

打开 `http://localhost:8080/kb-admin.html`，点击"导入示例问答"即可体验完整 RAG 闭环。

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/rag` | RAG 增强对话（检索 + 生成） |
| POST | `/chat/send` | Agent 智能客服（含工具调用） |
| GET | `/chat/history?sessionId=` | 聊天历史 |
| POST | `/kb/ingest` | 文档摄取 |
| GET | `/kb/stats` | 知识库统计 |
| GET | `/kb/search?q=&topK=` | 向量检索测试 |
| GET | `/kb/health` | 知识库健康检查 |
| DELETE | `/kb/source/{source}` | 按来源删除文档 |

## 对比验证

打开 `/rag-demo.html` 可对比 **RAG 增强模式** vs **普通模式** 的回答质量差异。

## License

本项目仅供学习交流使用。
