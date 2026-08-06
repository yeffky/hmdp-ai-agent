#!/usr/bin/env node
// 生成虚拟达人账号 + 达人探店笔记种子 SQL
// 输入: ../seed-groupbuy/shops.tsv (id\ttype_id\tname\t首图\taddress)
// 输出: dist/seed_blogs.sql
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

const USER_ID_BASE = 2000000
const BLOG_ID_BASE = 3000000
const BLOG_COUNT = 100

const DAREN = [
  ['觅食小分队', '/imgs/icons/user5-icon.png'],
  ['小鹿爱探店', '/imgs/icons/kkjtbcr.jpg'],
  ['干饭魂', '/imgs/icons/user5-icon.png'],
  ['阿茶喝奶茶', '/imgs/icons/default-icon.png'],
  ['芋泥啵啵', '/imgs/icons/default-icon.png'],
  ['荔枝去哪儿', '/imgs/icons/user5-icon.png'],
  ['汤圆不圆', '/imgs/icons/default-icon.png'],
  ['一颗橙子', '/imgs/icons/user5-icon.png'],
  ['今天想吃肉', '/imgs/icons/default-icon.png'],
  ['阿紫爱溜达', '/imgs/icons/kkjtbcr.jpg'],
  ['圆滚滚的米粒', '/imgs/icons/default-icon.png'],
  ['肥宅快乐屋', '/imgs/icons/user5-icon.png']
]

const TITLES = [
  '周末探店｜{shop} 真的好吃到跺脚！',
  '打卡 {shop}，人均超值的宝藏小店',
  '{shop} 值得二刷！菜品和氛围都在线',
  '藏不住了！{shop} 这家店太适合拍照了',
  '{shop} 探店日记：从进门到光盘',
  '亲测 {shop}，味道在线不踩雷',
  '{shop} 也太会了吧，好吃到舔盘',
  '带家人吃了 {shop}，一致好评',
  '{shop} 新晋网红店，我先冲了',
  '在 {shop} 吃到了久违的美味',
  '{shop} 打卡成功，性价比拉满',
  '朋友安利的 {shop}，果然没让人失望'
]

const CONTENT = [
  `周末和朋友一起去「{shop}」打卡～<br/>位置在{address}，很好找，店面挺有氛围的，随手一拍都很好看📷<br/>招牌菜分量很足，味道是真在线，人均也不贵，性价比直接拉满💯<br/>店员服务很热情，会主动推荐招牌，体验感很好～<br/>总之非常推荐，下次还会带家人来！`,
  `「{shop}」我真的会谢！！<br/>本来只是路过，结果被香味勾进去了😂<br/>点了店里的招牌，一端上来就惊艳到了，色香味俱全！<br/>口感很绝，不夸张地说，好吃到想打包带走🤤<br/>地址在{address}，附近的朋友一定要来试试，强推！`,
  `打卡「{shop}」🫧<br/>环境：干净舒服，装修很有格调，适合约会/聚会<br/>口味：招牌菜非常稳，口味在线不踩雷，分量也实在<br/>服务：小姐姐很耐心，上菜也快<br/>坐标{address}，性价比很高，值得N刷！`,
  `在{address}附近找到一家宝藏店「{shop}」✨<br/>招牌菜真的太顶了，入口即化，幸福感爆棚！<br/>而且价格很友好，学生党也能放心冲🫡<br/>环境干净，出片率高，探店打卡必备～<br/>已经安利给全宿舍了哈哈哈`,
  `「{shop}」探店｜绝绝子！<br/>作为吃货，这家店我必须实名表扬！！<br/>菜品新鲜、味道地道，连小菜都做得认真<br/>人均不高，吃得饱饱的，太满足了🥹<br/>{address}，公交地铁都方便，都给我冲！`,
  `分享一家私藏小店「{shop}」🍽️<br/>第一次来就被圈粉了，环境舒服，不吵闹<br/>招牌菜闭眼点不踩雷，食材新鲜，味道在线<br/>服务员态度超级好，像朋友一样亲切～<br/>地址{address}，工作日晚餐首选，爱了爱了！`
]

function hash(str) {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619) }
  return h >>> 0
}
function pick(arr, seed) {
  return arr[hash(seed) % arr.length]
}
function esc(s) {
  return String(s || '').replace(/\\/g, '').replace(/'/g, "''")
}

function main() {
  const tsv = readFileSync(resolve(__dirname, '../seed-groupbuy/shops.tsv'), 'utf8')
  const shops = tsv.split('\n').filter(Boolean).map((line) => {
    const [id, typeId, name, img, address] = line.split('\t')
    return { id: Number(id), typeId: Number(typeId), name, img, address }
  })

  // 达人用户
  const userRows = DAREN.map(([nick, icon], i) => {
    const id = USER_ID_BASE + i
    const phone = '139' + String(10000000 + id).slice(0, 8)
    return `(${id}, '${phone}', '', '${esc(nick)}', '${icon}', NOW(), NOW())`
  })

  // 笔记：优先美食类目，随机达人
  const blogRows = []
  for (let i = 0; i < BLOG_COUNT; i++) {
    const id = BLOG_ID_BASE + i
    const shop = shops[i % shops.length]
    const seed = shop.id + ':' + i
    const user = DAREN[i % DAREN.length]
    const userId = USER_ID_BASE + (i % DAREN.length)
    const title = esc(pick(TITLES, seed + 't').replace('{shop}', shop.name))
    const content = esc(pick(CONTENT, seed + 'c')
      .replace(/\{shop\}/g, shop.name)
      .replace(/\{address\}/g, shop.address || shop.name))
    const img = shop.img && shop.img.startsWith('http') ? shop.img : '/imgs/icons/default-icon.png'
    const liked = 20 + (hash(seed + 'l') % 2000)
    const comments = hash(seed + 'm') % 300
    blogRows.push(`(${id}, ${shop.id}, ${userId}, '${title}', '${esc(img)}', '${content}', ${liked}, ${comments}, NOW(), NOW())`)
  }

  const header = `-- 由 tools/seed-blog/gen.mjs 生成（虚拟达人 + 探店笔记，图片暂用店铺首图，SD 图后续替换）
-- 生成时间 ${new Date().toISOString()}
`
  const uCols = '(`id`, `phone`, `password`, `nick_name`, `icon`, `create_time`, `update_time`)'
  const bCols = '(`id`, `shop_id`, `user_id`, `title`, `images`, `content`, `liked`, `comments`, `create_time`, `update_time`)'
  const sql = `${header}
INSERT INTO \`tb_user\` ${uCols} VALUES
${userRows.join(',\n')};

INSERT INTO \`tb_blog\` ${bCols} VALUES
${blogRows.join(',\n')};
`
  mkdirSync(resolve(__dirname, 'dist'), { recursive: true })
  const out = resolve(__dirname, 'dist', 'seed_blogs.sql')
  writeFileSync(out, sql, 'utf8')
  console.log(`生成完成：${DAREN.length} 位达人，${BLOG_COUNT} 条笔记；已写入 ${out}`)
}

main()
