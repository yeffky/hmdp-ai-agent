#!/usr/bin/env node
// DeepSeek 对美食门店批量细分 → dist/food_categories.json
//
// 用法:
//   1. 先 node crawl.mjs 生成 dist/food_stores.json
//   2. node classify-food.mjs  →  dist/food_categories.json
//      （增量续跑：已存在的分类保留；可手动编辑 food_categories.json 覆盖单个门店后重跑）
//   3. node crawl.mjs --merge  →  合并生成 dist/seed_amap.sql
//
// API key 读取顺序：环境变量 DEEPSEEK_API_KEY → tools/crawl-amap/.env → hm-dianping/application.yaml
import { readFileSync, existsSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

const DEEPSEEK_URL = 'https://api.deepseek.com/chat/completions'
const MODEL = 'deepseek-chat'
const BATCH = 60
const CATEGORIES = ['奶茶咖啡', '快餐小吃', '火锅', '烧烤烤肉', '地方菜系', '异域料理', '自助餐', '海鲜', '面包蛋糕', '食品生鲜']

function readDeepSeekKey() {
  if (process.env.DEEPSEEK_API_KEY) return process.env.DEEPSEEK_API_KEY
  const envFile = resolve(__dirname, '.env')
  if (existsSync(envFile)) {
    const m = readFileSync(envFile, 'utf8').match(/^\s*DEEPSEEK_API_KEY\s*=\s*(.+)\s*$/m)
    if (m) return m[1].trim()
  }
  const yaml = resolve(__dirname, '../../hm-dianping/src/main/resources/application.yaml')
  if (existsSync(yaml)) {
    const m = readFileSync(yaml, 'utf8').match(/deepseek:[\s\S]*?api-key:\s*(\S+)/)
    if (m) return m[1].trim()
  }
  return ''
}

async function classify(batch, apiKey) {
  const prompt = `你是本地生活平台的门店分类专家。下面给出若干门店（编号. 店名（地址）），请把每家归入一个美食细分；若明显不是餐饮（如棋牌室、足道按摩、健身房、网吧、便利店、影院、酒店等混入餐饮搜索结果的门店）则标为 NON_FOOD。

可选细分（只准用这些或 NON_FOOD）：${CATEGORIES.join('、')}

判断要点：
- 主要看店名，地址仅辅助。
- 咖啡/奶茶/茶饮/甜品 → 奶茶咖啡；面包/蛋糕/烘焙 → 面包蛋糕。
- 快餐/小吃/面/粉/早餐/包子/水饺/麻辣烫 → 快餐小吃。
- 川湘粤闽浙等地方菜、家常菜、私房菜 → 地方菜系。
- 日料/韩餐/西餐/东南亚/泰国/印度 → 异域料理。
- 烧烤/烤肉/烤串/烤鱼 → 烧烤烤肉。
- 名字带自助 → 自助餐；海鲜/水产 → 海鲜。
- 生鲜/水果/熟食/卤味/食材 → 食品生鲜。
- 不确定的餐饮店选最接近的类别。

输入：
${batch.map((s, i) => `${i + 1}. ${s.name}（${s.address || '无地址'}）`).join('\n')}

只输出 JSON 对象（键为编号，值为类别），不要任何解释或额外文字，例如：{"1":"火锅","2":"NON_FOOD"}`

  const res = await fetch(DEEPSEEK_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({
      model: MODEL,
      messages: [{ role: 'user', content: prompt }],
      temperature: 0,
      response_format: { type: 'json_object' }
    })
  })
  const json = await res.json()
  if (!res.ok) throw new Error(`DeepSeek ${res.status}: ${JSON.stringify(json).slice(0, 200)}`)
  const text = json.choices?.[0]?.message?.content || ''
  const parsed = JSON.parse(text)
  for (const v of Object.values(parsed)) {
    if (v !== 'NON_FOOD' && !CATEGORIES.includes(v)) throw new Error(`非法类别: ${v}`)
  }
  return parsed
}

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)) }

async function main() {
  const KEY = readDeepSeekKey()
  if (!KEY) { console.error('缺少 DeepSeek API key（环境变量 DEEPSEEK_API_KEY / .env / application.yaml）'); process.exit(1) }
  const inFile = resolve(__dirname, 'dist/food_stores.json')
  if (!existsSync(inFile)) { console.error(`未找到 ${inFile}，请先运行 node crawl.mjs`); process.exit(1) }
  const stores = JSON.parse(readFileSync(inFile, 'utf8'))

  const outFile = resolve(__dirname, 'dist/food_categories.json')
  const result = existsSync(outFile) ? JSON.parse(readFileSync(outFile, 'utf8')) : {}
  const pending = stores.filter((s) => !(s.key in result))
  if (pending.length === 0) { console.log(`全部 ${stores.length} 家已分类，无需调用。`); return }

  console.log(`待分类 ${pending.length} 家（已分类 ${stores.length - pending.length} 家），每批 ${BATCH} 家，共 ${Math.ceil(pending.length / BATCH)} 批`)
  let cursor = 0
  while (cursor < pending.length) {
    const batch = pending.slice(cursor, cursor + BATCH)
    const tag = `[${cursor + 1}-${Math.min(cursor + BATCH, pending.length)}/${pending.length}]`
    let parsed = null
    for (let attempt = 1; attempt <= 3 && !parsed; attempt++) {
      try { parsed = await classify(batch, KEY) }
      catch (e) { console.warn(`${tag} 第 ${attempt} 次失败: ${e.message}`); if (attempt < 3) await sleep(1500) }
    }
    if (!parsed) { console.error(`${tag} 连续失败，跳过该批（可稍后重跑续分类）`); cursor += batch.length; continue }
    batch.forEach((s, i) => { const c = parsed[String(i + 1)]; if (c) result[s.key] = c })
    console.log(`${tag} 完成`)
    await sleep(300)
    cursor += batch.length
  }

  writeFileSync(outFile, JSON.stringify(result, null, 0), 'utf8')
  const cnt = {}
  for (const v of Object.values(result)) cnt[v] = (cnt[v] || 0) + 1
  console.log('\n分类统计:', JSON.stringify(cnt))
  console.log('已写入 dist/food_categories.json')
}

main().catch((e) => { console.error(e); process.exit(1) })
