# 工具结果缓存设计 — Redis 相同参数缓存（避免重复执行）

> 生成日期：2026-08-13
> 范围：hm-dianping `com.hmdp.agent.graph.nodes.ToolExecutor` 工具执行层。目标：相同参数的工具调用结果走 Redis 缓存，避免重复执行外部调用（DB 查询 / 高德 / 评价接口）。
> 前置：注入机制重构（`docs/agent-injection-refactor.md`）后，ReAct 决策循环多次工具调用，相同查询重复执行浪费。
> 状态：已实现并编译通过。

---

## 1. 现状与动机

- ReAct 决策循环中，同一轮/跨轮可能用相同参数重复调用工具（如反复 `searchShops` 相同条件），每次都执行 DB / 外部调用；
- 工具执行集中在 `ToolExecutor.execute`（ToolNode + 写确认恢复共用），是加缓存的**单一入口**。

## 2. 调研结论（决策依据）

### 2.1 LangChain / LangGraph 原生机制（已抓源码验证）

| 框架 | 是否有"工具结果按参数缓存" |
|---|---|
| **LangChain** | ❌ 无。只有 LLM prompt 缓存（`CacheBackedLLM`/`BaseCache`，`langchain_core/caches.py`，key=`prompt+llm_string`）、Anthropic prompt caching（`cache_control`，模型层）、embedding 缓存。工具层需自建。 |
| **LangGraph（Python）** | 🔶 不内置自动缓存，但给扩展点：`tool_call_handler` 官方文档示例就是缓存模式（`get_cache` 命中返回 `ToolMessage`）；配套 `BaseCache`（`langgraph/cache/`，memory/redis/sqlite，带 TTL）。另有 `CachePolicy`（节点级，`key_func`+`ttl`）与 `langgraph_sdk.cache.swr`（服务端 stale-while-revalidate，`fresh_for`+`max_age`）。 |

**hmdp 是 langgraph4j（Java）**：无 `cache_policy` / `swr`，需自建。

### 2.2 yuru-agent 参考

- **POI 工具结果缓存**：`external_poi_cache` 表，key=`provider+city+keywords+poi_type`，TTL 7 天，**空结果不缓存**，命中直接返回；
- **LLM prompt 缓存**：`SHA256(model+messages+temperature+max_tokens)` 作 key，TTL 86400，命中返回 `latency_ms=0`。

### 2.3 结论

自建 **Redis 工具结果缓存**：key=工具名+参数哈希，TTL 30 分钟，只缓存**只读稳定工具**。

## 3. 设计

### 3.1 缓存范围（工具白名单）

| 缓存（只读 + 结果稳定 + 用户无关） | 不缓存 |
|---|---|
| `searchShops`、`searchShop`、`geoSearch`、`recommendShops`、`listShopVouchers`、`queryShopComments`、`queryShopBlogs`、`queryUserBlogs` | 写操作（`takeQueueNumber`/`cancelMyQueue`）、用户私有（`queryMyQueueStatus`/`queryMyOrders`/`searchHistory`）、易变（`queryShopQueueStatus`）、`searchKnowledge`、`query`(Text2Sql) |

### 3.2 缓存 key

```
agent:toolcache:{SHA256(toolName | argsStr)}
```

白名单工具均**用户无关**（店铺/评价/笔记数据跨用户一致），key 不含 userId → 跨用户共享，命中率高。

### 3.3 存储与 TTL

- **Redis**（现有连接），无额外表持久化；
- **TTL = 30 分钟（1800s）**，配置化；
- key 前缀 `agent:toolcache:`，便于批量清理。

### 3.4 命中 / 写入流程（`ToolExecutor.execute` 入口）

```
execute(toolName, args):
  toolName ∈ 白名单?
     redis.get(agent:toolcache:sha256(toolName|args)) 命中 → 直接返回（补 writeLast 保持决策观察）→ 不执行工具
     未命中 → 执行工具 → 成功且结果非空 → redis.set(key, text, 30min)
  非白名单 → 不查缓存，直接执行
```

- **空结果 / 错误标签不缓存**（`[]`/`rows:0`/未匹配，防一直命中空缓存——对齐 yuru-agent 教训）；
- **Redis 异常不阻塞工具执行**：缓存读写失败只打 debug、跳过缓存走正常调用（缓存不是单点故障）。

## 4. 改动清单（已落地）

| 文件 | 改动 |
|---|---|
| 新增 `config/ToolCacheProperties` | `@ConfigurationProperties("agent.tool-cache")`：`enabled`/`ttlSeconds`(1800)/`tools` 白名单 |
| `graph/nodes/ToolExecutor` | 注入 `StringRedisTemplate` + 配置；`execute` 入口缓存命中直接返回（补 `writeLast`）；成功且可缓存结果写缓存；空/错误不缓存；Redis 异常跳过缓存；key=`SHA256(toolName\|args)` |
| `graph/GraphConfig` | `@Autowired` `StringRedisTemplate` + `ToolCacheProperties`，装配给 `ToolExecutor` |
| `resources/application.yaml` | `agent.tool-cache.*`（8 个只读白名单工具） |
| 新增 `test/.../ToolExecutorTest` | 命中不执行工具 / 非白名单不查缓存 |

## 5. 验证

- ✅ `mvn compile`
- ✅ `ToolExecutorTest`（缓存命中不执行工具、非白名单不查缓存）
- ✅ 其他单测无回归
- 手动：同一查询连问两次，第二次日志 `ToolCache 命中: searchShops (N chars)`，且无 `ShopMapper` SQL / 高德调用——命中缓存，响应更快

## 6. 注意点

- `queryShopQueueStatus`（排队叫号）默认**不缓存**，要加可配 `tools` + 短 TTL；
- 参数顺序可能影响 key 命中（同查询 JSON 顺序不同），实测命中率低时可改为"解析后按 key 排序再哈希"（规范化参数）；
- Redis 是缓存层，工具数据源变更（DB 更新）后最长 30 分钟过期，对店铺评分/评论这类低频变化数据可接受。
