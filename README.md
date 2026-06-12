# 黑马点评 (hmdp) — AI 智能客服增强版

基于 Spring Boot + MyBatis-Plus + Redis 的本地生活点评平台，原项目为"黑马点评"课程项目。本项目在此基础上进行了 **AI Agent 架构升级**。

## 创新点

### 1. 五层 AI Agent 架构（基于 LangChain4j）

```
用户消息 → 感知层 → 决策引导 → 工具层 → 自我反思 → 记忆层 → 返回
```

| 层级 | 职责 | 技术实现 |
|------|------|----------|
| **感知层** | 意图识别（GENERAL_QA / ORDER_QUERY / COMPLAINT） | System Prompt 引导 LLM 分类 |
| **工具层** | 订单查询等外部能力 | `@Tool` 注解，LLM 自动决策调用 |
| **记忆层** | 短期对话窗口 + MySQL 持久化 | 自定义 `ChatMemory` 实现 |
| **决策引导** | 角色定义、路由规则、SOP | `@SystemMessage` + AiServices |
| **自我反思** | 输出质量自检、错误修正 | `ReflectionGuard` 后处理 |

### 2. 规范化的 Agent 开发范式

- 接口与实现分离：`CustomerServiceAgent` 接口定义 Agent 契约
- 工具可插拔：`@Tool` 注解的工具类可独立扩展
- 记忆可配置：`ChatMemory` 支持窗口大小、持久化策略灵活配置
- 安全护栏：`ReflectionGuard` 对 AI 输出进行质量校验

### 3. 前后端分离 + AI 集成

- 前端：Vue.js + Element UI + Axios，右下角悬浮客服气泡
- 后端：Spring Boot 2.3 + MyBatis-Plus + Redis
- AI 引擎：DeepSeek API（兼容 OpenAI 格式）
- 反向代理：Nginx 统一入口

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.3.12 |
| ORM | MyBatis-Plus 3.4.3 |
| 缓存/分布式锁 | Redis + Redisson 3.13.6 |
| AI 框架 | LangChain4j 0.31.0 |
| LLM | DeepSeek Chat API |
| 前端 | Vue.js 2.x + Element UI + Axios |
| 数据库 | MySQL 5.x |

## 项目结构

```
hmdp/
├── hm-dianping/               # 后端 Spring Boot 项目
│   └── src/main/java/com/hmdp/
│       ├── agent/             # AI Agent 模块（新增）
│       │   ├── AgentConfig.java          # Agent Bean 装配
│       │   ├── CustomerServiceAgent.java # Agent 接口（五层核心）
│       │   ├── perception/
│       │   │   └── IntentType.java       # 感知层：意图枚举
│       │   ├── tool/
│       │   │   └── OrderQueryTool.java   # 工具层：订单查询
│       │   ├── memory/
│       │   │   └── MySqlChatMemory.java  # 记忆层：DB 持久化
│       │   └── guard/
│       │       └── ReflectionGuard.java  # 自我反思层
│       ├── config/            # 配置类
│       ├── controller/        # REST 控制器
│       ├── entity/            # 实体类
│       ├── mapper/            # MyBatis Mapper
│       ├── service/           # 业务服务
│       └── utils/             # 工具类
├── nginx-1.18.0/              # Nginx + 前端静态文件
│   └── html/hmdp/
│       ├── css/chat.css       # 客服气泡样式（新增）
│       └── js/chat.js         # 客服聊天组件（新增）
└── .gitignore
```

## 快速开始

### 1. 环境要求

- JDK 8+
- Maven 3.6+
- MySQL 5.7+
- Redis 6+
- Nginx 1.18+

### 2. 数据库初始化

执行 `src/main/resources/db/hmdp.sql` 建表并导入种子数据。

### 3. 配置

修改 `src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://your-mysql-host:3306/hmdp
    username: root
    password: your-password
  redis:
    host: your-redis-host
    port: 6379
    password: your-redis-password

deepseek:
  api-key: your-deepseek-api-key
  base-url: https://api.deepseek.com/v1
  model: deepseek-chat
```

### 4. 启动

```bash
# 后端
cd hm-dianping/hm-dianping
mvn spring-boot:run

# 前端（Nginx）
cd nginx-1.18.0
nginx -c conf/nginx.conf
```

访问 `http://localhost:8080`

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/send` | 发送消息给 AI 客服 |
| GET | `/api/chat/history?sessionId=` | 获取聊天历史 |

## License

本项目仅供学习交流使用。
