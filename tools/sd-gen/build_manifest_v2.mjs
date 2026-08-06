#!/usr/bin/env node
// 生成改进版 SD 清单 manifest_v2.tsv（out_relpath<TAB>prompt）
//  - 笔记图：按店铺名主题（火锅/奶茶/KTV…）生成，不再用通用类目图
//  - 团购图：按套餐标题（冬日双人餐/单人豪华餐…）生成，每套餐一张
// 输入: vouchers.tsv (voucher_id\ttitle\tshop_type\tshop_name)
//       blogs_v2.tsv (blog_id\tshop_type\tshop_name)
import { readFileSync, writeFileSync } from 'node:fs'

const TYPE_BASE = {
  1: 'delicious chinese food restaurant, appetizing dishes',
  2: 'ktv karaoke room, microphone and neon lights',
  3: 'hair salon, haircut and styling',
  4: 'fitness gym, exercise equipment',
  5: 'foot massage and spa room, relaxing',
  6: 'luxury beauty spa treatment room',
  7: 'colorful kids indoor playground',
  8: 'trendy bar, cocktails and neon',
  9: 'party room with friends, ktv and games',
  10: 'nail salon, manicure and nail art'
}

// 店铺名 → 主题（火锅/奶茶/KTV…），命中则覆盖类型基础
function shopTheme(name, type) {
  const n = name
  if (/火锅|涮|烤肉|烤串|烧烤|铁板/.test(n)) return 'chinese hotpot and bbq restaurant, hotpot dishes on table, steam'
  if (/茶餐厅|冰厅|港式|烧腊|冰室/.test(n)) return 'hong kong style tea restaurant, dim sum and roast meat'
  if (/咖啡|星巴克|COSTA/.test(n)) return 'cozy coffee shop, latte art and pastries'
  if (/奶茶|茶饮/.test(n)) return 'milk tea shop, bubble tea cups'
  if (/日料|寿司|居酒屋|日本/.test(n)) return 'japanese restaurant, sushi and sashimi platter'
  if (/川菜|湘菜|重庆|麻辣|辣/.test(n)) return 'spicy sichuan restaurant, chili and peppercorn dishes'
  if (/面馆|面|米粉|小面/.test(n)) return 'chinese noodle shop, hot noodle bowls'
  if (/甜品|蛋糕|烘焙|面包|糖水/.test(n)) return 'dessert cafe, cakes and sweet treats'
  if (/海鲜|蒸汽/.test(n)) return 'seafood restaurant, fresh seafood platter'
  if (/西餐|牛排|意面|披萨/.test(n)) return 'western restaurant, steak and pasta'
  if (/烤鸭|卤味|熟食/.test(n)) return 'chinese roast duck shop'
  if (/KTV|量贩/.test(n)) return 'ktv karaoke room, microphone, neon lighting'
  if (/美发|理发|造型/.test(n)) return 'hair salon, haircut styling'
  if (/健身|游泳|瑜伽|搏击/.test(n)) return 'modern gym, fitness equipment'
  if (/足疗|按摩|足浴|推拿/.test(n)) return 'foot massage spa room, candles and towels'
  if (/SPA|美容|养生/.test(n)) return 'luxury spa treatment room'
  if (/亲子|儿童|乐园|蹦床/.test(n)) return 'colorful kids indoor playground'
  if (/酒吧|精酿|livehouse|音乐/.test(n)) return 'trendy bar, cocktails, neon light'
  if (/轰趴|桌游|剧本杀|聚会/.test(n)) return 'party room with friends, board games'
  if (/美甲|美睫/.test(n)) return 'nail salon, manicure, colorful nail art'
  return TYPE_BASE[type] || 'local business, realistic photo'
}

// 套餐标题 → 主题描述（叠加在类型基础上）
function dealTheme(title, type) {
  const base = TYPE_BASE[type] || 'delicious food'
  const t = title
  const parts = []
  if (/双人/.test(t)) parts.push('double portion, two servings')
  if (/单人/.test(t)) parts.push('single portion')
  if (/冬日/.test(t)) parts.push('warm winter theme')
  if (/豪华/.test(t)) parts.push('premium luxury')
  if (/欢享|经典|精选|招牌/.test(t)) parts.push('signature')
  if (/畅玩|通票|全天/.test(t)) parts.push('all-day')
  if (/小时/.test(t)) parts.push('a few hours session')
  if (/包场/.test(t)) parts.push('private venue rental')
  if (/月卡/.test(t)) parts.push('monthly membership')
  if (/单次|体验/.test(t)) parts.push('single session')
  if (/套餐|餐/.test(t)) parts.push('combo set')
  const suffix = parts.length ? parts.join(', ') : 'product'
  return `${base}, ${suffix}, appetizing, realistic, professional product photo`
}

function readTsv(file) {
  return readFileSync(new URL(file, import.meta.url), 'utf8')
    .split('\n').filter(Boolean).map((l) => l.split('\t'))
}

const lines = []
// 笔记：按店名主题
for (const [id, type, name] of readTsv('./blogs_v2.tsv')) {
  const theme = shopTheme(name, Number(type))
  lines.push(`blogs/sd/${id}.png\treview photo of ${theme}, inviting, realistic food photography`)
}
// 团购：按套餐标题，每套餐一张
for (const [id, title, type] of readTsv('./vouchers.tsv')) {
  const prompt = dealTheme(title, Number(type))
  lines.push(`deals/${id}.png\t${prompt}`)
}

writeFileSync(new URL('./manifest_v2.tsv', import.meta.url), lines.join('\n') + '\n', 'utf8')
console.log(`manifest_v2 生成：${lines.length} 条（${lines.length - 667} 笔记 + 667 团购）`)
