#!/usr/bin/env node
// 生成团购商品种子 SQL（每个商家 2-3 个套餐：1 个限量秒杀 + 其余不限量）
// 输入: shops.tsv (id\ttype_id\tname\t首图)  由 mysql 导出
// 输出: dist/seed_groupbuy.sql
//
// 用法:
//   1. 导出商家:  mysql -h <host> -P 3307 -u root -p hmdp -B -N \
//        -e "SELECT id,type_id,name,SUBSTRING_INDEX(images,',',1) FROM tb_shop" > shops.tsv
//   2. node gen.mjs
//   3. 导入: mysql ... hmdp < dist/seed_groupbuy.sql

import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

// 团购套餐模板：每类 2-3 个 [标题, 副标题, 团购价(分), 原价(分)]
const TEMPLATES = {
  1: [
    ['冬日双人餐', '精选双人套餐 · 适合2-3人', 12800, 18800],
    ['单人豪华餐', '单人精选 · 主食+饮品', 6800, 9800],
    ['双人欢享套餐', '招牌菜双人份 · 到店即用', 15800, 22800]
  ],
  2: [
    ['欢唱3小时套餐', '含果盘 · 非节假日可用', 9900, 16800],
    ['欢唱4小时豪华套餐', '含果盘+饮品 · 提前预约', 13900, 22800]
  ],
  3: [
    ['洗剪吹套餐', '含洗头+剪发+造型', 5800, 9800],
    ['烫发+护理套餐', '含洗剪吹 · 需预约', 16800, 28800]
  ],
  4: [
    ['单次体验卡', '器械+团课任选一次', 2900, 5800],
    ['健身月卡', '30天不限次 · 含团课', 19900, 39900]
  ],
  5: [
    ['足疗60分钟', '传统足疗 · 赠茶点', 6900, 12800],
    ['全身按摩套餐', '90分钟全身SPA按摩', 12800, 22800]
  ],
  6: [
    ['面部深层护理', '清洁+补水+按摩', 15900, 28800],
    ['全身SPA套餐', '120分钟全身放松', 25900, 42800]
  ],
  7: [
    ['亲子畅玩2小时', '海洋球+滑梯+沙池', 5900, 9900],
    ['亲子全天通票', '全天不限时 · 含家长陪同', 9900, 16800]
  ],
  8: [
    ['精酿畅饮套餐', '精酿4杯 · 赠小食', 8900, 15800],
    ['双人微醺套餐', '鸡尾酒2杯+果盘', 12900, 20800]
  ],
  9: [
    ['包场4小时', '含KTV+桌游 · 最多12人', 29900, 49900],
    ['包场8小时豪华套餐', '含KTV+台球+餐饮', 49900, 79900]
  ],
  10: [
    ['基础单色美甲', '任选单色 · 含护理', 3900, 7800],
    ['美甲+美睫套餐', '单色美甲+自然款美睫', 12900, 21800]
  ]
}

const ID_BASE = 1000000 // 用大基数 id，避免与既有数据冲突

function sqlEsc(s) {
  return String(s || '').replace(/\\/g, '').replace(/'/g, "''")
}

function hash(str) {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619) }
  return h >>> 0
}

function main() {
  const tsv = readFileSync(resolve(__dirname, 'shops.tsv'), 'utf8')
  const shops = tsv.split('\n').filter(Boolean).map((line) => {
    const [id, typeId, name, img] = line.split('\t')
    return { id: Number(id), typeId: Number(typeId), name, img }
  })

  const voucherRows = []
  const seckillRows = []
  let idx = 0

  for (const shop of shops) {
    const templates = TEMPLATES[shop.typeId] || TEMPLATES[1]
    // 每个模板生成一个商品；第一个为秒杀（限量+有效期），其余不限量
    templates.forEach(([title, subtitle, pay, orig], i) => {
      const id = ID_BASE + idx++
      const isSeckill = i === 0
      const type = isSeckill ? 1 : 0
      const img = shop.img && shop.img.startsWith('http') ? shop.img : ''
      const name = sqlEsc(shop.name)
      const t = sqlEsc(title)
      const st = sqlEsc(subtitle)
      voucherRows.push(
        `(${id}, ${shop.id}, '${t}', '${st}', '${sqlEsc(img)}', ${pay}, ${orig}, ${type}, 1)`
      )
      if (isSeckill) {
        const stock = 10 + (hash(shop.name) % 40)
        seckillRows.push(
          `(${id}, ${stock}, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY))`
        )
      }
    })
  }

  const header = `-- 由 tools/seed-groupbuy/gen.mjs 生成（每商家 2-3 个团购，1 秒杀 + 其余不限量）
-- 图片暂用店铺首图，SD 生成图后续替换
-- 生成时间 ${new Date().toISOString()}
`
  const vCols = '(`id`, `shop_id`, `title`, `sub_title`, `image`, `pay_value`, `actual_value`, `type`, `status`)'
  const sCols = '(`voucher_id`, `stock`, `begin_time`, `end_time`)'
  const sql = `${header}
INSERT INTO \`tb_voucher\` ${vCols} VALUES
${voucherRows.join(',\n')};

INSERT INTO \`tb_seckill_voucher\` ${sCols} VALUES
${seckillRows.join(',\n')};
`

  mkdirSync(resolve(__dirname, 'dist'), { recursive: true })
  const out = resolve(__dirname, 'dist', 'seed_groupbuy.sql')
  writeFileSync(out, sql, 'utf8')
  console.log(`生成完成：${voucherRows.length} 个团购商品，其中 ${seckillRows.length} 个秒杀；已写入 ${out}`)
}

main()
