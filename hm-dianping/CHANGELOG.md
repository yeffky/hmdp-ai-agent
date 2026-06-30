# CHANGELOG

## feature/react-agent 分支 重大变更

### 1. Text2SQL 动态查询系统

**删除了** `DynamicQueryTool.java`，替换为三阶段 Text2SQL 架构：

| 组件 | 文件 | 职责 |
|---|---|---|
| TableSchemaService | `agent/tool/text2sql/TableSchemaService.java` | Redis 缓存表名 → LLM 选表 → information_schema 查列结构 |
| SqlGenerator | `agent/tool/text2sql/SqlGenerator.java` | 表结构 + 用户查询 → LLM 生成 SELECT → 6 层安全校验 |
| Text2SqlTool | `agent/tool/graph/Text2SqlTool.java` | 三阶段编排调用 |

**安全策略**：
- 仅允许 SELECT 语句
- 禁止 DML/DDL 关键词（正则 `\b` 单词边界，避免误匹配 `create_time` 等列名）
- 表名白名单校验
- 禁止敏感列（password 等）
- 禁止多语句（分号注入）
- 店铺名强制模糊匹配 `LIKE '%keyword%'`

### 2. RAG 文档自动摄入系统

**新增文件**：
| 文件 | 职责 |
|---|---|
| `rag/document/DocumentConverter.java` | ProcessBuilder 调用 markitdown CLI，UTF-8 编码，超时控制 |
| `rag/document/DocumentFileWatcher.java` | WatchService 实时监控 + @Scheduled 定时全量扫描，防抖机制 |
| `rag/document/DocumentPipeline.java` | 编排：转换 → SHA-256 哈希对比 → 删除旧向量 → 摄入 |
| `rag/document/IngestionStateManager.java` | JSON 文件状态持久化，避免重复摄入 |

**配置新增** (`rag.document.*`)：
```yaml
rag:
  document:
    enabled: true
    watch-dir: ./rag-documents
    markitdown-command: python -m markitdown
    timeout-seconds: 300
```

**前置依赖**：服务器安装 `pip install "markitdown[all]"`

### 3. MarkdownSplitter 增强

- 新增 **图片保护**：`![alt](url)` 语法不会被句子分隔符拆散
- 新增 **Markdown 检测**：IngestionService 通过正则 `# | ![ | \|...\| | ``` ` 自动判断是否为 Markdown 文档（不再仅检查首字符）

### 4. ReAct Agent 错误分类增强

- **LLM 辅助错误分类**：regex 无法确定的模糊错误（如"服务繁忙"），调用 LLM 进行语义判断
- 配置项 `agent.graph.llm-error-classify: true`
- 仅 regex 返回 FATAL（catch-all）时才触发，性能影响极小

### 5. Planner 节点优化

- 输出格式改为始终包含 `intent`（用户意图分析）
- 工具描述精细化，`searchKnowledge` 限定为仅查平台规则/操作流程
- `text2Sql` 赋予全表查询权限

### 6. 商家类型映射

- 删除 `ShopTypeBootstrap.java`（不再硬编码摄入 RAG）
- 商家类型查询完全走 Text2SQL 动态查库 `tb_shop_type`

### 7. 向量库后台管理

- 新增 `QdrantAdminController` + `qdrant-admin.html`
- 访问 `http://localhost:8081/qdrant-admin.html`
- 功能：集合统计、分页浏览、全文查看

### 8. 秒杀订单代理修复

- `VoucherOrderServiceImpl.proxy` 改为 `@Lazy @Resource` 自注入
- 解决 Redis Stream 消费者线程中 `proxy == null` 问题
- 消除 Spring Boot 2.7 循环依赖错误

### 9. 前端修复

- 折扣显示保留 1 位小数 (`toFixed(1)`) — `shop-detail.html`

### 配置变更速查

| 新增配置项 | 默认值 | 说明 |
|---|---|---|
| `rag.document.enabled` | true | 文档自动摄入开关 |
| `rag.document.watch-dir` | ./rag-documents | 监控目录 |
| `rag.document.markitdown-command` | python -m markitdown | markitdown CLI 命令 |
| `rag.document.timeout-seconds` | 300 | 单文档转换超时 |
| `rag.document.debounce-seconds` | 5 | 文件变更防抖 |
| `agent.graph.llm-error-classify` | true | LLM 错误分类 |
| `agent.postgres.url/username/password` | - | PostgreSQL 连接 |
| `agent.memory.compression.*` | - | 上下文压缩参数 |
