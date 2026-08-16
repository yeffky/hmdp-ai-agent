# hmdp-ai-agent — 项目说明

黑马点评强化版：本地生活服务（店铺/团购/排队/探店/AI Agent/地图）。

## 技术栈与结构

| 模块 | 说明 |
|---|---|
| `hm-dianping/` | Spring Boot 后端（JDK 17），端口 8081 |
| `frontend/` | Vue3 + Vite SPA，dev 端口 5173，代理 `/api`→8081 |
| `nginx-1.18.0/` | 静态托管（`html/hmdp` 旧版 + `html/hmdp-app` 新 SPA 构建产物） |
| `tools/` | 数据脚本：Amap 爬虫 / 团购种子 / 笔记种子 / SD 生成 |
| 远程服务 | 主机 `<server-host>`（docker-compose 共享，真实地址见 gitignore 的 application.yaml）：MySQL `:3307`（库 `hmdp`，root）、Redis `:6390`、RabbitMQ `:5670`、PostgreSQL16 `:5438`（Agent checkpoint）、xxl-job admin `:8080`；qdrant 现跑本地 |

前端构建产物输出到 `nginx-1.18.0/html/hmdp-app`（nginx root 直接服务）。

## 构建 / 运行 / 测试

**本机环境（已持久化）**：
- JDK 17：`C:\Users\Administrator\.jdks\ms-17.0.19`（本机唯一 JDK，必须用它）
- IDEA：`D:\software\IntelliJ IDEA 2024.1`（后端在 IDEA 里启动，端口 8081）
- Maven：无独立安装，用 IDEA 内置 `D:\software\IntelliJ IDEA 2024.1\plugins\maven\lib\maven3`（mvn.cmd 在沙箱下会 Access denied，用下面的 java 启动器方式）

```bash
# 后端编译（本机无独立 mvn；用 IDEA 内置 maven + java 启动器绕过 .cmd 沙箱限制）
export JAVA_HOME="C:\Users\Administrator\.jdks\ms-17.0.19"
export PATH="$JAVA_HOME/bin:$PATH"
MAVEN_HOME="D:\software\IntelliJ IDEA 2024.1\plugins\maven\lib\maven3"
CW=$(ls "$MAVEN_HOME"/boot/plexus-classworlds-*.jar)
java -classpath "$CW" "-Dclassworlds.conf=$MAVEN_HOME/bin/m2.conf" "-Dmaven.home=$MAVEN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$PWD/hm-dianping" \
  org.codehaus.plexus.classworlds.launcher.Launcher -q compile -f hm-dianping/pom.xml

# 前端
cd frontend && npm test && npm run build   # build 落到 nginx html/hmdp-app
```

- 后端在 **IDEA 里启动**（端口 8081）；改完代码需 IDEA Stop→Run。
- 前端 dev：`npm run dev`（HMR）；或访问 nginx 构建产物（需 `npm run build`）。
- 连远程 MySQL 查库：本机无 mysql 客户端，用 `tools/db-check/CheckVoucherOrderIndex.java`（JDBC，密码从 application.yaml 解析后走环境变量）。

## 核心数据模型（MySQL `hmdp`）

- `tb_shop`：314 家（可追加 Amap 爬虫种子）；含 `district_id`（地区）、`queue_enabled`（1=支持排队，按品类 美食/KTV/酒吧/轰趴=1，其余=0）、美食细分/服务字段 `food_category`（10 类：奶茶咖啡/快餐小吃/火锅/烧烤烤肉/地方菜系/异域料理/自助餐/海鲜/面包蛋糕/食品生鲜）、`description`、`has_parking`、`child_friendly`、`pet_friendly`、`max_seats`（food_category 由 classify-food.mjs 用 DeepSeek 分类填充，索引 `idx_food_category`）
- `tb_city` / `tb_district`：福州鼓楼 + 杭州拱墅两级；`center_x/y` 为 GCJ-02 GEO 圆心
- `tb_shop_type`：10 类目（美食/KTV/美发/健身/按摩/SPA/亲子/酒吧/轰趴/美甲）
- `tb_voucher`：团购商品（加 `image` 字段）；`type` 0普通不限量 / 1秒杀限量；秒杀库存+有效期在 `tb_seckill_voucher`
- `tb_voucher_order`：订单；`status` 1待支付/2已支付/3已核销/4已取消/5退款中/6已退款；`pay_type` 1余额/2支付宝/3微信
- `tb_blog`：探店笔记（108 条，`shop_id` 关联店铺，`user_id` 关联达人）
- `tb_blog_comments` / `tb_shop_comment`：笔记评论 / 店铺评论（种子已灌，`tb_blog.comments` / `tb_shop.comments` 与行数对齐）
- `tb_user`：含 12 个虚拟达人（id ≥ 2000000）

## 关键业务逻辑

- **店铺列表排序**：`/shop/of/type`、`/shop/of/name` 支持 `sortBy=comments|score`（服务端 ORDER BY）+ `districtId` 过滤；距离走 Redis GEO（`shop:geo:{districtId}:{typeId}`，地区圆心 5km）
- **搜索**：搜索词用 `q` 参数（非 `name`），可限定在分类/地区内
- **团购下单**：普通券 `POST /voucher-order/buy/{id}`；秒杀 `POST /voucher-order/seckill/{id}`（Lua+MQ 异步建单）
- **支付**：`PUT /voucher-order/pay/{orderId}?payType=`（模拟支付）；**15 分钟未付自动取消** `OrderTimeoutScheduler`（回补秒杀 MySQL+Redis 库存）
- **排队**：`/queue-ticket/*`；`queue_enabled=0` 的店铺取号被拒
- **地图**：`/shop/map?districtId=` 取全部商家；前端底部"地图"tab（高德 JS 地图 + 按类型着色 14px marker）
- **相关探店**：`/blog/of/shop/{shopId}`
- **Agent**：Text2SQL 从 information_schema 动态发现表，新增表+注释即可被识别

## 数据生成脚本（tools/）

| 脚本 | 产物 |
|---|---|
| `crawl-amap/crawl.mjs` | 高德 Web API 爬真实商家（美食 050000 深翻页 + 全局去重）→ 缓存 `dist/raw_rows.json`/`food_stores.json`；`classify-food.mjs` 用 DeepSeek 对美食细分（→`dist/food_categories.json`）；`crawl.mjs --merge` 合并生成 `dist/seed_amap.sql`（需 `.env` 的 AMAP_KEY，DeepSeek key 读 application.yaml） |
| `seed-groupbuy/gen.mjs` | 每商家 2-3 团购（1 秒杀）→ `dist/seed_groupbuy.sql` |
| `seed-blog/gen.mjs` | 12 达人 + 100 笔记 → `dist/seed_blogs.sql`（读 `seed-groupbuy/shops.tsv`） |
| `sd-gen/gen.py` | ComfyUI 文生图单张封装 |

DB 迁移文件：`hm-dianping/src/main/resources/db/*.sql`（city_district / shop_queue_enabled / voucher_image / food_category，幂等）。

## 密钥配置（均 gitignore，勿提交）

- `hm-dianping/src/main/resources/application.yaml`：真实 DB/Redis 密码（远程库，示例见 application.example.yaml）
- `frontend/.env`：`VITE_AMAP_JS_KEY`（高德 JS 地图 key）
- `tools/crawl-amap/.env`：`AMAP_KEY`（高德 Web 服务 key）
- `.claude/settings.local.json`：本机 mysql/ssh/docker/pscp 权限放行

## xxl-job 分布式任务调度（半接入）

- **调度中心**：已部署于 `<server-host>:8080/xxl-job-admin`（Docker，`--network host`，连接本机 MySQL `xxl_job` 库，admin/123456）
- **执行器**：`XxlJobConfig` + `XxlJobHandlers`（6 个 `@XxlJob`：shopScoreRecalc/orderTimeoutCancel/seckillStockWarmup/seckillVoucherExpire/tableCacheRefresh/ragDocFullScan），由 `xxl.job.enabled` 开关控制
- **当前状态**：**本地开发 `xxl.job.enabled=false`**（执行器关闭，`@Scheduled` 兜底）。因 xxl-job 靠调度中心**回调执行器**触发，后端在本地内网时 admin 连不到，故暂不删 @Scheduled
- **将来上服务器**：部署后端 jar 到服务器 → `xxl.job.enabled=true` → 执行器注册 `hmdp-executor`（admin 已建任务 id 2-7）→ 删 `@Scheduled` 与 `@EnableScheduling`

## 当前进度 / 遗留

已完成：双城市+地区选择器、地图 tab、团购下单/支付/我的订单、排队开关、相关探店、314 商家+692 团购+108 笔记种子、脱敏规范（提交前 `git grep` 敏感项）。

**Agent 子系统近期优化**（设计文档：`docs/agent-injection-refactor.md` / `docs/agent-tool-cache.md`）
- 注入机制：filter/trim/summarize 三层组合拳 + 本轮封装（历史走摘要），解决跨轮污染与重复回答
- 会话：孤儿 tool_calls 修复、按轮次滑动窗口压缩（`keep-recent-rounds=5`，保留含 tool 结果可跨轮复用）、步骤推进改 agent 按 plan 顺序
- skill：能力边界清理（`tools.md` 只含本技能工具）、Planner 跨 skill 依赖识别（查评价/取号需一并选 `shop`）、Agent 能力不足 replan 兜底
- 写确认：复合意图（取消+改人数）执行后回 agent 继续决策
- **工具结果缓存**：Redis 相同参数缓存（`agent.tool-cache.*`，TTL 30min，只读白名单工具）

**已上线：SD 文生图（服务器 GPU 批量生成）**
- 服务器 `10.10.74.166`（zjk 用户，RTX 4090），conda 环境 `zjkpy39`（**Python 3.10**，曾从 3.9 升级以适配 ComfyUI；torch 2.8.0+cu126，与驱动 CUDA 12.7 匹配）
- ComfyUI 装于服务器 `/home/zjk/ComfyUI`，端口 8188；模型 `Realistic_Vision_V5.1_fp16-no-ema.safetensors` 在 `models/checkpoints/`
- 启动：`cd /home/zjk/ComfyUI && nohup /home/zjk/.conda/envs/zjkpy39/bin/python main.py --port 8188 > /home/zjk/sd-gen/comfyui.log 2>&1 &`
- 批量脚本在 `/home/zjk/sd-gen/`（gen.py / batch.py / manifest_v2.tsv）：767 张（100 笔记按**店名主题** + 667 团购按**套餐标题**）GPU 约 15 分钟跑完
- 产物已回传本地 `nginx html/hmdp/imgs/blogs/sd/`（100 张）与 `imgs/deals/{voucher_id}.png`（667 张）；DB 已更新：`tb_blog.images`、`tb_voucher.image` 指向新图
- 注意：`VoucherMapper.xml` 的 `queryVoucherOfShop` 必须含 `v.image` 列，否则店铺详情团购接口不返回 image
- 连接服务器：本地 `tools/ssh/plink.exe`（密码 SSH），命令例：`plink -batch -hostkey <fp> -ssh -pw <pwd> zjk@10.10.74.166 "..."`；传文件用 `pscp -r`
- 注意：ComfyUI 0.30 需 Python 3.10+；GPU 驱动是 CUDA 12.7，torch 必须用 cu126（不能装 2.13 cu130）

## 注意

- 坐标全部 GCJ-02（与高德一致）；自建 OSM 地图需先转 WGS-84
- 默认定位**福州·鼓楼**；没跑爬虫前福州列表为空
- 达人/笔记/团购 id 用大基数（2000000/3000000/1000000）避免与既有数据冲突
