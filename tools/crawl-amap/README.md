# 高德爬虫 → tb_shop 种子数据

调用高德开放平台 **Web 服务 API**（`/v3/place/around`），按"地区圆心 + 类目关键词"搜索真实商家，
生成 `dist/seed_amap.sql`。

## 准备

1. 在 [高德开放平台](https://console.amap.com) 创建应用，添加 **Web服务** key
2. `cp .env.example .env`，填入 `AMAP_KEY`
3. 需要 Node 18+（内置 `fetch`）

## 运行

```bash
node crawl.mjs
```

## 产出说明

- **真实字段**：店名、地址、坐标（GCJ-02，与高德地图 JS / 现有种子零偏移）、商圈、营业照片（有则用）
- **估算字段**：`avg_price / score / comments / sold` 按品类合理区间生成（高德 API 不返回点评数据）
- **图片**：优先高德 `photos`，缺失时用按品类着色的 SVG data URI 占位图（保证 `images` 非空）
- 每城每类最多保留 15 条，内部按 店名+坐标 去重

## 导入

```bash
mysql -u root -p hmdp < dist/seed_amap.sql
```

重复执行会插入重复数据；如需重灌，先按 `district_id` 清空：

```sql
DELETE FROM tb_shop WHERE id > 18;
```

## 坐标系注意

`tb_district.center_x/center_y`（地图 GEO 圆心）必须与商家坐标同一坐标系（GCJ-02）。
本仓库种子与高德 API 均为 GCJ-02，自建 OSM/WGS-84 地图时需先做坐标系转换。
