#!/usr/bin/env node
// 高德 Web 服务 API 爬取真实商家 → 生成 tb_shop 种子 SQL
//
// 用法:
//   1. 复制 .env.example 为 .env，填入 AMAP_KEY（高德 Web 服务 key）
//   2. node crawl.mjs
//
// 说明:
//   - 用 /v3/place/around 按"地区圆心 + 类目关键词"搜索，radius=5000 与地图 5km 一致
//   - 返回 GCJ-02 坐标，与 tb_shop 现有种子、高德地图 JS 完全一致，零偏移
//   - 店名/地址/坐标为真实商家；评分/人均/评论/销量按品类合理估算（高德不返回）
//   - 图片用高德 photos 优先，无则用按品类生成的占位图
//   - 输出: dist/seed_amap.sql（可重复执行生成）

import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

// ---------- 配置 ----------
const KEY = readKey()
const API = 'https://restapi.amap.com/v3/place/around'
const RADIUS = 5000
const PER_PAGE = 25
const MAX_PAGES = 4          // 每个关键词最多 4 页（100 条）
const CAP_PER_TYPE = 15      // 每城每类最多保留 15 条

// 城市/地区（id 与 city_district.sql 一致）
const DISTRICTS = [
  { id: 1, name: '拱墅区', city: '杭州', center: '120.147,30.325' },
  { id: 2, name: '鼓楼区', city: '福州', center: '119.3026,26.0855' }
]

// tb_shop_type.id → 高德搜索配置（types 为高德 POI 分类码，keywords 为兜底关键词）
const SHOP_TYPES = {
  1:  { types: '050000', keywords: ['火锅', '烧烤', '日料', '川菜', '粤菜', '小吃', '面馆', '家常菜', '海鲜', '西餐', '奶茶', '咖啡'] },
  2:  { types: '080302', keywords: ['KTV'] },
  3:  { types: '080200', keywords: ['美发', '理发'] },
  4:  { types: '080500', keywords: ['健身房', '游泳', '瑜伽'] },
  5:  { types: '080600', keywords: ['足疗', '按摩', '推拿'] },
  6:  { types: '080200', keywords: ['SPA', '美容'] },
  7:  { types: '080400', keywords: ['儿童乐园', '亲子', '蹦床'] },
  8:  { types: '080300', keywords: ['酒吧', '精酿'] },
  9:  { types: '080400', keywords: ['轰趴', '桌游', '剧本杀'] },
  10: { types: '080200', keywords: ['美甲', '美睫'] }
}

// 每类目的估算区间（与真实商家档次匹配）
const METRICS = {
  1:  { price: [25, 180], score: [42, 49], comments: [80, 5000], sold: [200, 50000] },
  2:  { price: [50, 150], score: [40, 48], comments: [50, 3000], sold: [100, 20000] },
  3:  { price: [40, 180], score: [42, 49], comments: [30, 2000], sold: [80, 8000] },
  4:  { price: [80, 400], score: [41, 49], comments: [40, 1500], sold: [50, 3000] },
  5:  { price: [60, 220], score: [42, 49], comments: [60, 4000], sold: [100, 10000] },
  6:  { price: [120, 500], score: [42, 49], comments: [40, 2000], sold: [60, 4000] },
  7:  { price: [40, 160], score: [41, 48], comments: [30, 2500], sold: [80, 6000] },
  8:  { price: [50, 200], score: [40, 47], comments: [30, 1500], sold: [60, 5000] },
  9:  { price: [200, 800], score: [42, 48], comments: [20, 800], sold: [30, 2000] },
  10: { price: [50, 150], score: [42, 49], comments: [30, 2000], sold: [80, 5000] }
}

const OPEN_HOURS = ['10:00-22:00', '11:00-21:30', '10:30-21:00', '11:00-23:30', '09:30-22:00', '10:00-24:00', '12:00-02:00', '11:00-14:00,17:00-21:00']

// 哪些类目默认支持排队取号（部分商家不开放，符合业务实际）
const QUEUE_ENABLED_BY_TYPE = { 1: 1, 2: 1, 8: 1, 9: 1 } // 美食/KTV/酒吧/轰趴 支持，其余不支持
function queueEnabled(typeId) { return QUEUE_ENABLED_BY_TYPE[typeId] ? 1 : 0 }

// 按品类着色的 SVG 占位图（data URI，保证 images 字段非空且可渲染）
function placeholderImage(typeId, name) {
  const colors = { 1: '#E2482D', 2: '#7A4ED0', 3: '#E87A9A', 4: '#2E9E6B', 5: '#C98A3D', 6: '#B05CE0', 7: '#3D8FBF', 8: '#5A5AE0', 9: '#D0862E', 10: '#E05AA0' }
  const c = colors[typeId] || '#888'
  const label = (name || '店铺').slice(0, 4)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="${c}"/><text x="100" y="108" font-size="22" fill="#fff" text-anchor="middle" font-family="sans-serif">${label}</text></svg>`
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
}

// 确定性伪随机（以名称哈希为种子，保证可复现）
function mulberry32(seed) {
  return () => {
    seed |= 0
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
function hash(str) {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619) }
  return h >>> 0
}

async function fetchPois(district, typeId, cfg) {
  const results = []
  const seen = new Set()
  const queries = []
  if (cfg.types) queries.push({ types: cfg.types })
  for (const kw of cfg.keywords) queries.push({ keywords: kw })

  for (const q of queries) {
    for (let page = 1; page <= MAX_PAGES; page++) {
      const params = new URLSearchParams({
        key: KEY,
        location: district.center,
        radius: String(RADIUS),
        offset: String(PER_PAGE),
        page: String(page),
        extensions: 'all'
      })
      if (q.types) params.set('types', q.types)
      if (q.keywords) params.set('keywords', q.keywords)

      let json
      try {
        const res = await fetch(`${API}?${params}`)
        json = await res.json()
      } catch (e) {
        console.warn(`  fetch fail ${district.name} type=${typeId} ${JSON.stringify(q)}: ${e.message}`)
        break
      }
      if (json.status !== '1' || !Array.isArray(json.pois)) {
        console.warn(`  amap error ${district.name} type=${typeId} ${JSON.stringify(q)}: ${json.infocode} ${json.info}`)
        break
      }
      if (json.pois.length === 0) break

      for (const p of json.pois) {
        const dedupeKey = `${p.name}|${p.location}`
        if (seen.has(dedupeKey)) continue
        seen.add(dedupeKey)
        results.push({ poi: p, districtId: district.id, typeId })
        if (results.length >= 200) break
      }
      if (results.length >= 200 || json.pois.length < PER_PAGE) break
      await sleep(120) // 限速，避免触发频率限制
    }
  }
  return results
}

function toRow(item) {
  const { poi, districtId, typeId } = item
  const rand = mulberry32(hash(poi.name + poi.location))
  const [x, y] = String(poi.location).split(',').map(Number)
  const m = METRICS[typeId]
  const price = Math.round(m.price[0] + rand() * (m.price[1] - m.price[0]))
  const score = Math.round(m.score[0] + rand() * (m.score[1] - m.score[0]))
  const comments = Math.round(m.comments[0] + rand() * (m.comments[1] - m.comments[0]))
  const sold = Math.round(m.sold[0] + rand() * (m.sold[1] - m.sold[0]))
  const openHours = OPEN_HOURS[Math.floor(rand() * OPEN_HOURS.length)]
  const area = poi.business_area || poi.adname || districtName(districtId)
  const photos = (poi.photos || []).slice(0, 2).map((ph) => ph.url).filter(Boolean)
  const images = photos.length ? photos.join(',') : placeholderImage(typeId, poi.name)
  const name = String(poi.name).replace(/\\/g, '').replace(/'/g, "''")
  const address = String(poi.address || '').replace(/\\/g, '').replace(/'/g, "''")
  const areaSql = String(area || '').replace(/'/g, "''")
  const openSql = openHours.replace(/'/g, "''")
  return `(NULL, '${name}', ${typeId}, ${districtId}, '${images}', '${areaSql}', '${address}', ${x}, ${y}, ${price}, ${sold}, ${comments}, ${score}, '${openSql}', ${queueEnabled(typeId)}, NOW(), NOW())`
}

function districtName(id) {
  return (DISTRICTS.find((d) => d.id === id) || {}).name || ''
}

async function crawl() {
  const all = []
  for (const d of DISTRICTS) {
    for (const [typeId, cfg] of Object.entries(SHOP_TYPES)) {
      process.stdout.write(`crawl ${d.city}${d.name} type=${typeId}(${cfg.types})... `)
      const items = await fetchPois(d, Number(typeId), cfg)
      const picked = items.slice(0, CAP_PER_TYPE)
      all.push(...picked)
      console.log(`${picked.length} 条`)
    }
  }
  return all
}

function emitSql(rows) {
  const header = `-- 由 tools/crawl-amap/crawl.mjs 生成（高德 Web API 真实商家 + 按品类估算指标）
-- 坐标 GCJ-02；生成时间 ${new Date().toISOString()}
-- 注意: 本脚本可能重复执行，若需覆盖请先 DELETE 对应 district_id 的数据
`
  const cols = '(`id`, `name`, `type_id`, `district_id`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`, `queue_enabled`, `create_time`, `update_time`)'
  const values = rows.map(toRow).join(',\n')
  return `${header}\nINSERT INTO \`tb_shop\` ${cols} VALUES\n${values};\n`
}

function readKey() {
  const envFile = resolve(__dirname, '.env')
  if (existsSync(envFile)) {
    const m = readFileSync(envFile, 'utf8').match(/^\s*AMAP_KEY\s*=\s*(.+)\s*$/m)
    if (m) return m[1].trim()
  }
  return process.env.AMAP_KEY || ''
}

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)) }

async function main() {
  if (!KEY) {
    console.error('缺少 AMAP_KEY：复制 tools/crawl-amap/.env.example 为 .env 并填入高德 Web 服务 key')
    process.exit(1)
  }
  console.log(`开始爬取（key: ${KEY.slice(0, 6)}...）`)
  const rows = await crawl()
  const sql = emitSql(rows)
  const outDir = resolve(__dirname, 'dist')
  mkdirSync(outDir, { recursive: true })
  const outFile = resolve(outDir, 'seed_amap.sql')
  writeFileSync(outFile, sql, 'utf8')
  console.log(`\n完成：共 ${rows.length} 条，已写入 ${outFile}`)
}

main().catch((e) => { console.error(e); process.exit(1) })
