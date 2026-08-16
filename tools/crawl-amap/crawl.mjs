#!/usr/bin/env node
// 高德 Web 服务 API 爬取真实商家 → 生成 tb_shop 种子 SQL
//
// 用法:
//   1. 复制 .env.example 为 .env，填入 AMAP_KEY（高德 Web 服务 key）
//   2. node crawl.mjs
//
// 说明:
//   - 用 /v3/place/around 按"地区圆心 + 类目"搜索，radius=5000 与地图 5km 一致
//   - 美食按 050000 深翻页抓全量，细分由 classify-food.mjs 用 LLM 分类
//     （高德 around 同时传 types+keywords 时 keywords 基本不生效，故不再按细分关键词爬）
//   - 返回 GCJ-02 坐标，与 tb_shop 现有种子、高德地图 JS 完全一致，零偏移
//   - 店名/地址/坐标为真实商家；评分/人均/评论/销量按品类合理估算（高德不返回）
//   - 图片用高德 photos 优先，无则用按品类生成的占位图
//   - 输出: dist/seed_amap.sql（--merge 模式，合并 LLM 分类后生成）

import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

// ---------- 配置 ----------
const KEY = readKey()
const API = 'https://restapi.amap.com/v3/place/around'
const RADIUS = 5000
const PER_PAGE = 25
const MAX_PAGES = 4             // 非美食每个关键词最多 4 页（100 条）
const CAP_PER_TYPE = 15         // 非美食每城每类最多保留 15 条
const FOOD_MAX_PAGES = 20       // 美食深翻页：每城最多 20 页（500 条候选）
const FOOD_MAX_PER_DISTRICT = 250 // 每城美食最多保留 250 家唯一店（供 LLM 细分）
const REQUEST_DELAY = 500       // 请求间隔（ms），免费 key QPS 上限 3/s，留足余量防 10021 限流

// 城市/地区（id 与 city_district.sql 一致）
const DISTRICTS = [
  { id: 1, name: '拱墅区', city: '杭州', center: '120.147,30.325' },
  { id: 2, name: '鼓楼区', city: '福州', center: '119.3026,26.0855' }
]

// 全局去重：key = 名称|坐标，跨类型/跨细分共享（同一店只入库一次）
const SEEN = new Set()

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

// 美食细分（typeId=1）：10 类，每类对应高德关键词
const FOOD_CATEGORIES = [
  { name: '奶茶咖啡', keywords: ['奶茶', '咖啡', '茶饮'] },
  { name: '快餐小吃', keywords: ['快餐', '小吃', '粉面', '麻辣烫'] },
  { name: '火锅', keywords: ['火锅', '涮锅'] },
  { name: '烧烤烤肉', keywords: ['烧烤', '烤肉', '烤肉串'] },
  { name: '地方菜系', keywords: ['川菜', '湘菜', '粤菜', '闽菜', '江浙菜', '家常菜'] },
  { name: '异域料理', keywords: ['日料', '韩餐', '西餐', '东南亚', '泰国菜', '印度菜'] },
  { name: '自助餐', keywords: ['自助餐'] },
  { name: '海鲜', keywords: ['海鲜', '水产'] },
  { name: '面包蛋糕', keywords: ['面包', '蛋糕', '烘焙'] },
  { name: '食品生鲜', keywords: ['生鲜', '水果', '熟食', '卤味'] }
]

// 每细分的菜品/环境描述模板（规则生成，高德 POI 无此字段）
const FOOD_DESC = {
  '奶茶咖啡': { dishes: ['现制茶饮', '手冲咖啡', '鲜果茶', '奶盖茶', '甜品小食'], env: ['ins风装修，适合打卡', '简约明亮，适合小憩', '街角暖色调，氛围惬意'] },
  '快餐小吃': { dishes: ['招牌粉面', '盖浇饭', '卤味小吃', '炸物小食'], env: ['快捷出餐，翻台快', '干净整洁，快餐氛围', '明档现做，烟火气足'] },
  '火锅': { dishes: ['招牌毛肚', '鲜切牛肉', '锅底涮菜', '虾滑丸子'], env: ['川渝风味装修，氛围热闹', '复古怀旧风，暖灯低桌', '宽敞明堂，朋友聚会首选'] },
  '烧烤烤肉': { dishes: ['烤串拼盘', '秘制烤肉', '海鲜烧烤', '烤蔬菜'], env: ['烟火气夜宵氛围', '炭火现烤，香气四溢', '露台/街边，适合夏夜啤酒'] },
  '地方菜系': { dishes: ['招牌家常菜', '时令小炒', '地方名菜', '手工点心'], env: ['家常温馨，适合聚餐', '地方风味装饰', '家庭式服务，亲切'] },
  '异域料理': { dishes: ['招牌寿司/生鱼片', '韩式烤肉', '意面披萨', '泰式冬阴功'], env: ['异域风情装修', '异国元素，氛围独特', '吧台/卡座，格调雅致'] },
  '自助餐': { dishes: ['海鲜自助', '烤肉自助', '火锅自助', '甜点饮品畅享'], env: ['品种丰富，取餐自由', '大厅开阔，家庭聚餐', '自助取餐，畅吃不限量'] },
  '海鲜': { dishes: ['清蒸海鲜', '蒜蓉粉丝虾', '椒盐皮皮虾', '海鲜粥'], env: ['海鲜现捞现做', '滨海风格，食材新鲜', '鲜活明档，现点现做'] },
  '面包蛋糕': { dishes: ['现烤面包', '慕斯蛋糕', '欧包', '牛角包'], env: ['面包香气，现烤出炉', '甜点柜精美陈列', 'ins风下午茶'] },
  '食品生鲜': { dishes: ['时令鲜果', '生鲜熟食', '卤味凉菜', '粮油干货'], env: ['生鲜直供，新鲜直达', '货架整齐，自选方便', '社区便民店'] }
}

// 生成菜品/环境/服务描述 + 服务属性（停车/儿童/宠物/最大桌数），确定性伪随机
function buildFoodMeta(category, name) {
  const t = FOOD_DESC[category]
  const rand = mulberry32(hash(name + category))
  const dish = t ? t.dishes[Math.floor(rand() * t.dishes.length)] : '招牌菜品'
  const env = t ? t.env[Math.floor(rand() * t.env.length)] : '干净舒适'
  const parking = rand() < 0.6 ? 1 : 0
  const child = rand() < 0.4 ? 1 : 0
  const pet = rand() < 0.2 ? 1 : 0
  const seats = (Math.floor(rand() * 9) + 2) * 2 // 4~20 偶数
  const desc = `主打${dish}，${env}。`
  return { foodCategory: category, desc, parking, child, pet, seats }
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

async function fetchPois(district, typeId, cfg, opts = {}) {
  const results = []
  const maxPages = opts.maxPages || MAX_PAGES
  const maxResults = opts.maxResults || 200
  const queries = []
  if (cfg.types) queries.push({ types: cfg.types })
  for (const kw of (cfg.keywords || [])) queries.push({ keywords: kw })

  for (const q of queries) {
    for (let page = 1; page <= maxPages; page++) {
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

      let json = null
      for (let attempt = 1; attempt <= 4; attempt++) {
        try {
          const res = await fetch(`${API}?${params}`)
          json = await res.json()
        } catch (e) {
          console.warn(`  fetch fail ${district.name} type=${typeId} ${JSON.stringify(q)} p${page}: ${e.message}`)
          json = null
        }
        if (json && json.status === '1') break
        if (json && json.infocode === '10021') {
          // QPS 限流：退避重试，避免整页数据丢失
          const wait = 800 * attempt
          console.warn(`  QPS 限流 ${district.name} type=${typeId} ${JSON.stringify(q)} p${page}，${wait}ms 后重试`)
          await sleep(wait)
          json = null
          continue
        }
        if (json) console.warn(`  amap error ${district.name} type=${typeId} ${JSON.stringify(q)} p${page}: ${json.infocode} ${json.info}`)
        break
      }
      if (!json || !Array.isArray(json.pois)) break
      if (json.pois.length === 0) break

      for (const p of json.pois) {
        const dedupeKey = `${p.name}|${p.location}`
        if (SEEN.has(dedupeKey)) continue
        SEEN.add(dedupeKey)
        results.push({ poi: p, districtId: district.id, typeId, foodCategory: null })
        if (results.length >= maxResults) break
      }
      if (results.length >= maxResults || json.pois.length < PER_PAGE) break
      await sleep(REQUEST_DELAY) // 限速，避免触发频率限制
    }
  }
  return results
}

function toRow(item) {
  const { poi, districtId, typeId, foodCategory } = item
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
  // 美食：细分 + 描述 + 服务属性
  const meta = foodCategory ? buildFoodMeta(foodCategory, poi.name) : null
  const foodCatSql = meta ? `'${meta.foodCategory}'` : 'NULL'
  const descSql = meta ? `'${meta.desc.replace(/'/g, "''")}'` : 'NULL'
  const parking = meta ? meta.parking : 0
  const child = meta ? meta.child : 0
  const pet = meta ? meta.pet : 0
  const seats = meta ? meta.seats : 4
  return `(NULL, '${name}', ${typeId}, ${districtId}, ${foodCatSql}, '${images}', '${areaSql}', '${address}', ${x}, ${y}, ${price}, ${sold}, ${comments}, ${score}, '${openSql}', ${queueEnabled(typeId)}, ${descSql}, ${parking}, ${child}, ${pet}, ${seats}, NOW(), NOW())`
}

function districtName(id) {
  return (DISTRICTS.find((d) => d.id === id) || {}).name || ''
}

async function crawl() {
  const all = []
  for (const d of DISTRICTS) {
    for (const [typeId, cfg] of Object.entries(SHOP_TYPES)) {
      if (Number(typeId) === 1) {
        // 美食：按 050000 深翻页收集全量餐饮 POI，细分交由 classify-food.mjs 用 LLM 分类
        process.stdout.write(`crawl ${d.city}${d.name} 美食(050000, cap=${FOOD_MAX_PER_DISTRICT})... `)
        const items = await fetchPois(d, 1, { types: '050000' }, { maxPages: FOOD_MAX_PAGES, maxResults: FOOD_MAX_PER_DISTRICT })
        all.push(...items)
        console.log(`${items.length} 条`)
      } else {
        process.stdout.write(`crawl ${d.city}${d.name} type=${typeId}(${cfg.types})... `)
        const items = await fetchPois(d, Number(typeId), cfg)
        const picked = items.slice(0, CAP_PER_TYPE)
        all.push(...picked)
        console.log(`${picked.length} 条`)
      }
    }
  }
  // 兜底：同一店同时出现在美食/非美食时，保留非美食（类型更具体），避免错贴美食细分
  const final = new Map()
  for (const it of all) {
    const key = `${it.poi.name}|${it.poi.location}`
    const prev = final.get(key)
    if (!prev) { final.set(key, it); continue }
    if (prev.typeId === 1 && it.typeId !== 1) final.set(key, it)
  }
  return [...final.values()]
}

// 合并 LLM 分类：美食行按分类填充细分/描述，NON_FOOD 丢弃，未分类保留但无细分
function applyClassification(raw, catMap) {
  const out = []
  let nonFood = 0, missing = 0
  for (const it of raw) {
    if (it.typeId === 1) {
      const cat = catMap[`${it.poi.name}|${it.poi.location}`]
      if (!cat) { missing++; out.push(it); continue }
      if (cat === 'NON_FOOD') { nonFood++; continue }
      it.foodCategory = cat
      out.push(it)
    } else {
      out.push(it)
    }
  }
  if (nonFood) console.log(`  LLM 判定非美食，已丢弃：${nonFood} 家`)
  if (missing) console.warn(`  未分类（保留但无细分）：${missing} 家`)
  return out
}

function emitSql(rows) {
  const header = `-- 由 tools/crawl-amap/crawl.mjs 生成（高德 Web API 真实商家 + 按品类估算指标）
-- 坐标 GCJ-02；生成时间 ${new Date().toISOString()}
-- 注意: 本脚本可能重复执行，若需覆盖请先 DELETE 对应 district_id 的数据
`
  const cols = '(`id`, `name`, `type_id`, `district_id`, `food_category`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`, `queue_enabled`, `description`, `has_parking`, `child_friendly`, `pet_friendly`, `max_seats`, `create_time`, `update_time`)'
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
  const outDir = resolve(__dirname, 'dist')
  mkdirSync(outDir, { recursive: true })

  // 合并模式：读取上次爬取缓存 + LLM 分类结果，生成最终 SQL（无需重新爬取）
  if (process.argv.includes('--merge')) {
    const rawFile = resolve(outDir, 'raw_rows.json')
    const catFile = resolve(outDir, 'food_categories.json')
    if (!existsSync(rawFile) || !existsSync(catFile)) {
      console.error('缺少 dist/raw_rows.json 或 dist/food_categories.json，请先运行 node crawl.mjs 与 node classify-food.mjs')
      process.exit(1)
    }
    const rows = JSON.parse(readFileSync(rawFile, 'utf8'))
    const catMap = JSON.parse(readFileSync(catFile, 'utf8'))
    const merged = applyClassification(rows, catMap)
    const sql = emitSql(merged)
    writeFileSync(resolve(outDir, 'seed_amap.sql'), sql, 'utf8')
    // 汇报分类分布供审阅
    const dist = {}
    for (const it of merged) if (it.typeId === 1) dist[it.foodCategory] = (dist[it.foodCategory] || 0) + 1
    console.log('美食细分分布:', JSON.stringify(dist))
    console.log(`\n完成：共 ${merged.length} 条（已合并 LLM 分类），已写入 dist/seed_amap.sql`)
    return
  }

  if (!KEY) {
    console.error('缺少 AMAP_KEY：复制 tools/crawl-amap/.env.example 为 .env 并填入高德 Web 服务 key')
    process.exit(1)
  }
  console.log(`开始爬取（key: ${KEY.slice(0, 6)}...）`)
  const rows = await crawl()
  const foodStores = rows
    .filter((it) => it.typeId === 1)
    .map((it) => ({
      key: `${it.poi.name}|${it.poi.location}`,
      name: it.poi.name,
      address: it.poi.address || '',
      x: Number(String(it.poi.location).split(',')[0]),
      y: Number(String(it.poi.location).split(',')[1]),
      districtId: it.districtId,
      districtName: districtName(it.districtId)
    }))
  writeFileSync(resolve(outDir, 'raw_rows.json'), JSON.stringify(rows), 'utf8')
  writeFileSync(resolve(outDir, 'food_stores.json'), JSON.stringify(foodStores, null, 2), 'utf8')
  console.log(`\n完成：共 ${rows.length} 条唯一店（其中美食 ${foodStores.length} 家待 LLM 分类）`)
  console.log('已缓存 dist/raw_rows.json + dist/food_stores.json')
  console.log('下一步：node classify-food.mjs   →   node crawl.mjs --merge')
}

main().catch((e) => { console.error(e); process.exit(1) })
