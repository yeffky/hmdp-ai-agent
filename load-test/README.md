# 秒杀并发压测（JMeter）

对 `POST /voucher-order/seckill/{id}` 做高并发压测，验证 Redis Lua + RabbitMQ 异步落单在秒杀高峰下的正确性。

## 文件说明

| 文件 | 作用 |
|------|------|
| `seed_redis.py` | 预置 Redis（库存 + 500 个登录验证码），生成 `phones.csv` |
| `phones.csv` | 500 个测试手机号（与预置验证码一一对应，脚本自动生成） |
| `seckill-load-test.jmx` | JMeter 5.5 测试计划（登录→提取token→并发秒杀→统计） |

## 前置

- 应用已启动：`http://localhost:8081`（`mvn spring-boot:run`，JDK 17）
- 远程 Redis/MySQL/RabbitMQ 可达（连接地址由 `seed_redis.py` 从 application.yaml 自动读取）
- 秒杀券库存已在 Redis（默认测试券 `666`）

## 步骤

### 1. 预置 Redis（每次压测前执行，重置库存与登录码）

```bash
python -X utf8 load-test/seed_redis.py 666 100 500
# 参数: voucherId=666  stock=100  手机号数量=500
```

### 1.5 重置 MySQL 库存（关键！）

`createVoucherOrder` 落单时校验的是 **MySQL** 的 `tb_seckill_voucher.stock`，不是 Redis 库存。只重置 Redis 库存会导致所有下单在 DB 层失败。用 MySQL 客户端/Workbench 执行：

```sql
UPDATE tb_seckill_voucher SET stock = 100 WHERE voucher_id = 666;
```

### 2. 启动 JMeter

**GUI 模式**（查看请求/响应，首次调试推荐）：

```bash
"F:/JMeter/apache-jmeter-5.5/bin/jmeter.bat" -t load-test/seckill-load-test.jmx
```

**命令行模式**（正式压测，出 jtl 结果文件）：

```bash
cd F:/Projects/hmdp-ai-agent
"F:/JMeter/apache-jmeter-5.5/bin/jmeter.bat" -n -t load-test/seckill-load-test.jmx -l load-test/result.jtl
```

### 3. 看结果

- **命令行**：跑完在 jmeter 控制台/日志（`jmeter.log`）看 Teardown 打印的汇总：
  ```
  成功下单: X
  库存不足: Y
  不能重复下单: Z
  异常(登录失败/401/其他): W
  ```
- **GUI**：聚合报告（吞吐/响应时间）+ 查看结果树（逐条看返回 JSON）

## 期望结果（500 并发抢库存=100）

| 分类 | 数量 | 说明 |
|------|------|------|
| `success:true`（成功下单） | 约 100 | 与库存一致 |
| `"库存不足"` | 约 400 | 库存扣完后的并发请求 |
| `"不能重复下单"` | 0（正常） | 每个手机号只请求1次 |
| 异常 | 0 | 登录失败或 401 才计入 |

> 若成功数 ≠ 库存数 或出现重复下单，说明 Lua 原子性/唯一索引有缺陷。

## 常见调整

- **改并发数**：GUI 打开 `秒杀并发压测 (500并发)` 线程组 → 修改 `线程数` / `Ramp-Up`
- **改目标地址**：测试计划顶部 `HOST` / `PORT` 变量
- **改测试券**：`VOUCHER_ID` 变量 + 重新跑 `seed_redis.py` 重置该券库存
- **CSV 路径不对**：`CSV_PATH` 变量改成本机绝对路径（或编辑 CSV Data Set Config 的 filename）

## 一人一单场景（可选）

想测"同一用户重复秒杀只成功1次"：把线程组 `num_threads` 改小（如 20），且 `phones.csv` 只留 1 个手机号（删掉其余行），20 并发同用户 → 期望 1 成功 + 19 个"不能重复下单"。
