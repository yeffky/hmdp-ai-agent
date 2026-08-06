# O2O 生活服务 AI Agent

基于 Spring Boot + LangGraph4j + LangChain4j + DeepSeek 的 ReAct 模式 AI Agent，集成 RAG 混合检索、Text2SQL 动态查询、增量 Checkpoint 存储、SSE 流式输出与排队取号等功能。

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.18 |
| AI Agent | LangGraph4j 1.8.19 + LangChain4j 1.16.2 |
| LLM | DeepSeek Chat API |
| ORM | MyBatis-Plus 3.4.3 |
| 数据库 | MySQL 8.0 + PostgreSQL (checkpoint) |
| 缓存 / 锁 | Redis + Jedis + Redisson 3.22.0 |
| 消息队列 | RabbitMQ 3.12 (DLQ 死信 + 有限重试) |
| 向量数据库 | Qdrant 1.9.0 (REST API) |
| Embedding | Ollama bge-m3 / SiliconFlow BAAI/bge-large-zh-v1.5 |
| 连接池 | HikariCP |
| 前端 | Vue 3 + Vite + Pinia + Vue Router + Element Plus（号票设计风格，见 `frontend/`） |
| 反向代理 | Nginx |
| 地图 | 高德 JS API（前端地图渲染）+ 高德 Web API（真实商家数据爬取，GCJ-02） |
| AI 图像生成 | ComfyUI + Realistic Vision V5.1（服务器 RTX 4090 批量生成评价图/团购封面） |
| 任务调度 | xxl-job 2.4.1（半接入；本地由 `@Scheduled` 兜底） |

## ReAct Agent 架构

```
用户 → Nginx → Spring Boot ReactStreamController (SSE)
                        ↓
  ┌──────────────────────────────────────────────────────┐
  │  LangGraph4j StateGraph                              │
  │                                                      │
  │  Context → Planner → Executor → Observer → Judge    │
  │              ↑           ↓          ↓         ↓      │
  │              └── replan ←──┴── retryGate ←──┘       │
  │                              ↓                      │
  │                           Answer → SSE 流式输出      │
  └──────────────────────────────────────────────────────┘
                        ↓
              DeltaPostgresSaver (增量 checkpoint)
                        ↓
                   PostgreSQL
```

### 六节点状态机

| 节点 | 职责 |
|------|------|
| Context | 上下文组装（System Prompt + 用户画像 + 滑动窗口压缩 + 对话历史） |
| Planner | 意图识别 + 计划制定（初始规划 / 重规划），支持 `cannot_fulfill` 能力边界拦截 |
| Executor | LLM 选工具 → 执行 → 结果写入 scratchpad，错误分类分流（RETRYABLE / USER_FIXABLE / FATAL） |
| Observer | 提取工具结果、剔除已完成步骤、空结果自动扩大搜索重试、确认恢复 LLM 校验 |
| Judge | 信息充分性判断，决定 answer / replan |
| Answer | 生成最终回答，支持流式（OpenAiStreamingChatModel）与预设回答统一管线 |

### 增量 Checkpoint 存储（DeltaPostgresSaver）

- 仅序列化状态变更（messages 差集 + 变化的标量字段），周期性全量快照作为锚点
- 加载时仅读取最近快照及其后的 delta，回放重建全量状态
- 图初始化耗时由 ~14s 降至 1s 以内，支持跨实例 checkpoint resume

### 人机协同中断恢复

基于 checkpoint 实现确认挂起与恢复：Agent 触发人工确认时序列化当前 ReAct 执行上下文并挂起，用户响应后通过 `updateState` 注入决策数据并恢复状态机执行，绕过 Planner 重跑避免上下文丢失。LLM 语义匹配校验用户回复与待确认问题的相关性，不匹配时触发重规划。

### Text2SQL 动态查询

LLM 选表 → 生成 SQL → 安全校验 → 执行。SQL 自动强制 `LIMIT` + `COUNT` 预检总行数，结构化汇总提示缩小查询范围，防止全表扫描。构建商家类型/地址等行业术语映射 + Redis 缓存表 Schema 提升 NL2SQL 匹配精度。

### RAG 混合检索

```
文档 → 自适应切片器 → Embedding → Qdrant
查询 → LLM 改写 → BM25 + 语义双路召回 → RRF 融合 → LLM 重排序
```

- 自适应切片器：Markdown 标题 + 中文章节标记（第一章 / 一、/ 1.1）多层级降级，代码块/表格保护
- 文档自动摄入：文件监控 → markitdown 转换 → SHA256 去重 → 幂等写入 Qdrant

### 上下文管理

滑动窗口机制（LLM 摘要压缩 + 硬上限裁剪），最近 3 轮全量对话；超出部分通过 PostgreSQL ILIKE 关键词检索实现按需历史回溯。

### 秒杀异步化（Redis Lua + RabbitMQ）

```
用户秒杀请求
   ↓
Redis Lua 脚本（原子：校验库存 + 一人一单 + 扣减库存）
   ↓ 通过后
发布消息到 RabbitMQ (seckill.order.exchange)
   ↓
Consumer 异步消费 → Redisson 分布式锁 → 写库落单
   ↓ 失败
有限重试（2s / 5s / 10s）→ 超过 3 次转入 DLQ
```

- 秒杀入口：Lua 脚本在 Redis 侧原子完成库存校验/扣减与一人一单判定，扛住高并发
- 异步落单：通过 RabbitMQ 解耦，Consumer 手动 ACK + prefetch=1 公平分发，`concurrency=3`
- 可靠投递：消费失败自动重试（指数退避 2s/5s/10s），超过 3 次转入死信队列 `seckill.order.dlq`，避免无限重试
- 幂等兜底：`tb_voucher_order` 唯一索引 `(user_id, voucher_id)` 作为 DB 层最后防线，防重复下单

## O2O 核心业务（仓库扩展）

### 双城市 · 地区

- `tb_city` / `tb_district` 两级：福州·鼓楼 + 杭州·拱墅（含 GCJ-02 GEO 圆心）
- 首页左上角**城市/地区选择器**（默认福州·鼓楼，localStorage 持久化），列表/搜索/地图按当前地区过滤
- 距离排序走 Redis GEO，key 含地区 `shop:geo:{districtId}:{typeId}`，圆心取地区中心

### 地图 tab

- 底部"地图"tab：**高德 JS 地图** + 商家 marker（按店铺类型着色、14px 半透明圆点、hover 显示店名）
- `GET /shop/map?districtId=` 取当前地区全部商家；点 marker 进店铺详情

### 团购商品 + 支付

- `tb_voucher` 作团购商品表（新增 `image` 封面），每商家 2-3 个套餐（1 个限量秒杀 + 其余不限量）
- 下单 → `tb_voucher_order` status=1 待支付；支付 → status=2；15 分钟未付自动取消并回补秒杀库存
- 接口：`POST /voucher-order/buy/{id}`、`PUT /pay/{orderId}`、`PUT /cancel/{orderId}`、`GET /my`、`GET /seckill/status/{id}`
- **雪花订单 ID 以字符串返回**（`@JsonSerialize(ToStringSerializer)` + Map 转 String），避免 JS 精度丢失导致"订单不存在"

### 评论体系

- `tb_shop_comment`（店铺评论，含评分）+ `tb_blog_comments`（笔记评论）
- 店铺详情/笔记详情评论区：默认 5 条 + 加载更多 + 发表（评论时可打星）
- **评论计数与行数对齐**（种子按 COUNT 回填 `tb_shop.comments` / `tb_blog.comments`）；店铺评分由**定时任务按评论均分延迟重算**（避免每条评论触发 AVG 全表扫描）

### 排队开关 / 排序

- `tb_shop.queue_enabled`：部分商家可关闭排队（美食/KTV/酒吧/轰趴=1，其余=0），取号时后端校验
- 店铺列表 `sortBy=comments|score` 服务端 ORDER BY（分页与排序一致）；`distance` 走 geo

### SD 文生图（服务器 GPU 批量生成）

- ComfyUI 部署于服务器（RTX 4090），`Realistic_Vision_V5.1_fp16-no-ema` 模型
- 100 张笔记**按店名主题** + 667 张团购**按套餐标题** 生成评价图/封面，回传 nginx `imgs/`
- 本地工具：`tools/sd-gen/`（gen.py / batch.py / build_manifest_v2.mjs）

### xxl-job 分布式任务调度（半接入）

- 调度中心已部署；执行器由 `xxl.job.enabled` 开关控制（本地默认关，`@Scheduled` 兜底；部署到服务器后开启）
- 任务：店铺评分重算 / 订单超时取消 / 秒杀库存预热（启动异步预热 + Lua nil 兜底）/ 秒杀券到期下架 / Text2SQL 缓存刷新 / RAG 文档扫描

## 项目结构

```
hm-dianping/src/main/java/com/hmdp/
├── agent/
│   ├── graph/                              # LangGraph4j 状态机
│   │   ├── GraphConfig.java                # 图编译 + DeltaPostgresSaver
│   │   ├── ToolRegistry.java               # 10+ 工具注册
│   │   ├── checkpoint/DeltaPostgresSaver.java
│   │   ├── error/                          # ErrorCategory / ToolException
│   │   ├── nodes/                          # 六节点实现
│   │   └── state/                          # ReActAgentState / StateSchema (Delta Channel)
│   ├── memory/context/                     # 滑动窗口 + 上下文压缩
│   └── tool/                               # Agent 工具
│       ├── HistorySearchTool.java          # 历史对话回溯
│       ├── KnowledgeRetrievalTool.java     # RAG 检索
│       ├── OrderQueryTool.java             # 订单查询
│       ├── QueueTicketTool.java            # 排队取号
│       ├── ShopSearchTool.java             # 商铺搜索
│       └── graph/                          # Text2SqlTool / GeoSearchTool
├── rag/                                    # RAG 引擎
│   ├── splitter/AdaptiveSplitter.java
│   ├── retrieval/RetrievalService.java     # 混合检索 + RRF
│   │   ├── LLMQueryRewriter.java
│   │   └── BM25KeywordIndex.java
│   ├── rerank/LLMReranker.java
│   ├── store/QdrantVectorStore.java
│   ├── embedding/OpenAiEmbeddingService.java
│   ├── ingestion/IngestionService.java
│   └── document/DocumentPipeline.java
└── controller/
    ├── ReactStreamController.java          # /chat/react/stream (SSE)
    ├── ChatRagController.java              # /chat/rag /chat/history
    ├── KnowledgeBaseController.java        # /kb/*
    ├── QueueTicketController.java          # /queue-ticket/*
    └── QdrantAdminController.java          # /api/qdrant/admin/*
```

### 前端（Vue 3）

```
frontend/
├── vite.config.js               # 开发代理 → 后端(8081) / 图片(8080)；构建输出到 nginx html/hmdp-app
├── src/
│   ├── main.js / App.vue        # 入口 + 全局 AI 助手挂件
│   ├── router/                  # 12 条路由（history 模式）
│   ├── stores/                  # Pinia：user / chat（chatMachine 纯函数驱动 SSE 流）
│   ├── api/                     # axios 封装 + 全部接口（http.js 统一处理 Result/401）
│   ├── utils/                   # format / sse 解析（纯函数，可单测）
│   ├── styles/                  # 号票设计令牌（tokens / base / components）
│   ├── components/              # AppIcon / AppHeader / AppTabbar / AiChat 等
│   └── views/                   # 消费端 + 用户端 + 管理端页面
```

- 开发：`cd frontend && npm run dev`（`http://localhost:5173`，Vite 代理转发到 8081）
- 构建：`npm run build` → 产物自动输出到 `nginx-1.18.0/html/hmdp-app`
- 测试：`npm test`（Vitest，覆盖格式化 / SSE 解析 / 聊天状态机 / HTTP 拦截器 / chat store）

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/react/stream` | ReAct Agent SSE 流式对话 |
| GET | `/chat/history` | 对话历史（游标分页） |
| POST | `/kb/ingest` | 文档摄取 |
| GET | `/kb/stats` | 知识库统计 |
| GET | `/kb/search` | 检索测试 |
| GET | `/kb/health` | 健康检查 |
| POST | `/queue-ticket/take` | 排队取号 |
| GET | `/queue-ticket/my` | 查询我的排队 |
| GET | `/shop/of/type` | 按类型查询商铺（`sortBy`/`districtId`） |
| GET | `/shop/of/name` | 按名称搜索商铺（`typeId`/`districtId`） |
| GET | `/shop/map` | 地图标注用：地区内全部商家 |
| GET | `/region/list` | 城市/地区列表（含 GEO 圆心） |
| POST | `/voucher-order/seckill/{id}` | 优惠券秒杀 |
| POST | `/voucher-order/buy/{id}` | 普通团购下单（返回字符串订单 id） |
| PUT | `/voucher-order/pay/{orderId}` | 支付订单（余额/支付宝/微信） |
| PUT | `/voucher-order/cancel/{orderId}` | 取消待支付订单 |
| GET | `/voucher-order/my` | 我的订单列表 |
| GET | `/voucher-order/seckill/status/{id}` | 是否已购该秒杀券（置灰按钮） |
| GET | `/shop-comment/list/{shopId}` | 店铺评论（分页，默认 5 条） |
| POST | `/shop-comment` | 发表店铺评论（含评分） |
| GET | `/blog-comments/list/{blogId}` | 笔记评论（分页，默认 5 条） |
| POST | `/blog-comments` | 发表笔记评论 |
| GET | `/blog/of/shop/{shopId}` | 店铺关联探店笔记 |
| PUT | `/user/info` | 更新个人资料 |
| GET | `/user/sign/today` | 今天是否已签到（置灰按钮） |

## 快速开始

### 1. 环境要求

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Java 运行时 |
| Maven | 3.6+ | 构建工具 |
| MySQL | 8.0+ | 业务数据（商铺、订单、用户等） |
| PostgreSQL | 14+ | Agent checkpoint 持久化 + 聊天历史、用户画像 |
| Redis | 6+ | 缓存 / 分布式锁 / Token 存储 / GeoSearch |
| RabbitMQ | 3.12+ | 秒杀订单异步消息队列 |
| Docker | 20+ | Qdrant 向量数据库 |
| Ollama | 最新 | Embedding 模型（可选，也可用 SiliconFlow 云端 API） |
| Nginx | 1.18+ | 前端静态文件 + API 反向代理 |

### 2. 数据库初始化

**MySQL** — 执行 SQL 建表并导入种子数据：

```bash
mysql -u root -p < src/main/resources/db/hmdp.sql
```

`hmdp.sql` 已含全部新表/字段（城市地区、团购封面、排队开关、店铺评论等）。对**已有库**增量升级，按需执行 `db/` 下的幂等迁移：

```
db/city_district.sql        # tb_city / tb_district / tb_shop.district_id
db/shop_queue_enabled.sql   # tb_shop.queue_enabled
db/voucher_image.sql        # tb_voucher.image
db/shop_comment.sql         # tb_shop_comment
```

秒杀幂等兜底需要唯一索引（防止同一用户重复购买同一券）：

```sql
ALTER TABLE tb_voucher_order ADD UNIQUE INDEX uk_user_voucher (user_id, voucher_id);
```

**PostgreSQL** — 创建数据库，表由 `DeltaPostgresSaver` 和 `PostgresConfig` 自动创建：

```bash
psql -U postgres -c "CREATE DATABASE hmdp;"
```

无需手动建表，应用启动时会自动执行 `CREATE TABLE IF NOT EXISTS`。

### 3. 启动依赖服务

```bash
# Redis（如未运行）
redis-server

# 本地开发依赖：Qdrant 向量数据库 + Ollama Embedding
cd hmdp-ai-agent
docker compose up -d

# 服务器部署依赖：PostgreSQL + RabbitMQ（端口 5670/15670，密码用环境变量注入）
docker compose -f docker-compose.server.yml up -d

# Ollama Embedding（本地方案）
ollama serve
ollama pull bge-m3
```

> 也可使用 SiliconFlow 云端 Embedding：在 `application.yaml` 中将 `rag.embedding.base-url` 设为 `https://api.siliconflow.cn/v1`，`api-key` 填入 SiliconFlow API Key，`model` 设为 `BAAI/bge-large-zh-v1.5`。

### 4. 配置

```bash
cp hm-dianping/src/main/resources/application.example.yaml \
   hm-dianping/src/main/resources/application.yaml
```

编辑 `application.yaml`，必填项：

```yaml
# MySQL 业务数据库
spring:
  datasource:
    url: jdbc:mysql://<host>:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: <your-password>

# Redis
  redis:
    host: <host>
    port: 6379
    password: <your-password>

# RabbitMQ（秒杀异步消息队列，注意端口对齐 compose 映射）
  rabbitmq:
    host: <host>
    port: 5672
    username: <user>
    password: <your-password>

# DeepSeek LLM
deepseek:
  api-key: <your-deepseek-api-key>

# PostgreSQL（Agent checkpoint + 聊天历史）
agent:
  postgres:
    url: jdbc:postgresql://<host>:5432/hmdp
    username: postgres
    password: <your-password>

# RAG（Qdrant + Embedding）
rag:
  qdrant:
    host: http://localhost
    port: 6333
  embedding:
    base-url: http://localhost:11434/v1   # Ollama 默认地址
    api-key: ollama
    model: bge-m3
```

### 5. Nginx 配置

**先构建前端**（Vue 3 产物会输出到 `nginx-1.18.0/html/hmdp-app`）：

```bash
cd frontend
npm install
npm run build
```

前端页面通过 Nginx 提供，API 代理到后端。关键配置：

```nginx
upstream backend {
    server 127.0.0.1:8081;   # Spring Boot
}

server {
    listen 8080;

    # Vue3 SPA 前端（history 路由回退）
    location / {
        root html/hmdp-app;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 静态图片（分类图标 / 上传图片，位于 html/hmdp/imgs）
    location /imgs/ {
        root html/hmdp;
        try_files $uri =404;
    }

    # API 代理到后端
    location /api/ {
        proxy_pass http://backend/;
    }

    # SSE 流式接口（需关闭 buffering）
    location /chat/ {
        proxy_pass http://backend/chat/;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }

    # 知识库管理
    location /kb/ {
        proxy_pass http://backend/kb/;
    }
}
```

> Nginx 安装包和前端文件已在 `nginx-1.18.0/` 目录中。启动：`nginx -c /path/to/nginx.conf`

### 6. 启动应用

```bash
cd hm-dianping
mvn spring-boot:run
```

启动日志中应看到：

```
PostgreSQL connected (HikariCP): jdbc:postgresql://...
ToolRegistry initialized with 6 tools:
ReAct Graph compiled with DeltaPostgresSaver
Declaring exchange 'seckill.order.exchange', queue 'seckill.order.queue'...
```

### 7. 验证

| 步骤 | 操作 | 预期 |
|------|------|------|
| 前端访问 | 打开 `http://localhost:8080` | 显示首页（号票风格） |
| 登录 | 手机号 + 验证码登录 | 获取 Token |
| Agent 对话 | 右下角「小优」聊天气泡输入"你好" | ReAct Agent 规划→执行→回答 |
| RAG 检索 | 打开 `/admin/kb` → 导入示例问答 → 问"怎么退款" | 从知识库检索并回答 |
| 排队取号 | 店铺详情页点「立即取号」 | 生成排队号，叫号状态实时更新 |
| 商户叫号 | 打开 `/admin/queue` → 选商铺 → 「叫下一个号」 | 等待队列依次出号 |
| 秒杀下单 | `POST /voucher-order/seckill/{id}` | Lua 校验 → 消息入队 → Consumer 异步落单；RabbitMQ 管理台可查队列与 DLQ |

### 8. 初始化知识库

打开 `http://localhost:8080/admin/kb`：

1. 点击"导入示例问答"加载种子数据
2. 或上传文档（PDF/Word/Markdown 等），由 DocumentPipeline 自动切片 + 向量化

手动通过 API 摄入：

```bash
curl -X POST http://localhost:8081/kb/ingest \
  -H "Content-Type: application/json" \
  -d '{"content":"平台支持在线排队取号...","source":"help","title":"排队指南"}'
```

### 常见问题

| 问题 | 排查 |
|------|------|
| Agent 对话无响应 | 检查 DeepSeek API Key 和网络连通性；确认 `deepseek.base-url` 配置正确 |
| checkpoint 加载慢 | 首次启动后执行 `DELETE FROM lg4jcheckpoint` 清空旧全量数据 |
| Qdrant 连接失败 | `docker compose ps` 确认 Qdrant 运行中；访问 `http://localhost:6333/health` |
| RabbitMQ 连接失败 | `docker compose -f docker-compose.server.yml ps` 确认 RabbitMQ 运行中；检查 `application.yaml` 的 `spring.rabbitmq` 端口（host 映射 5670） |
| 秒杀消息堆积/DLQ 有消息 | 查看 Consumer 日志确认失败原因；DLQ 队列 `seckill.order.dlq` 手动消费后排查 |
| Embedding 失败 | Ollama 是否启动？`ollama list` 确认 `bge-m3` 已下载 |
| SSE 流式不工作 | Nginx `proxy_buffering off` 是否配置？浏览器 Network 面板查看 EventStream |

## License

仅供学习交流使用。
