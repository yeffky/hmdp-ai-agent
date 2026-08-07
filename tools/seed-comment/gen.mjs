#!/usr/bin/env node
// 生成评论种子 SQL：店铺评论（每店 5-24 条）+ 笔记评论（每笔记 3-19 条）
// 并用 COUNT 回填 tb_shop.comments / tb_blog.comments，保证计数与行数一致
// 输入: ../seed-groupbuy/shops.tsv (id\ttype\tname\timg\taddress)
//       blogs_v2.tsv (blog_id\ttype\tshop_name)
// 输出: dist/seed_comments.sql
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

// 评论用户池：12 达人 + 既有用户
const USERS = [2000000, 2000001, 2000002, 2000003, 2000004, 2000005, 2000006, 2000007, 2000008, 2000009, 2000010, 2000011, 1, 2, 5]

const SHOP_COMMENTS = [
  '环境不错，服务也很热情，会再来～',
  '味道在线，性价比高，推荐！',
  '周末人有点多，但体验还不错',
  '第一次来，整体满意，服务员很耐心',
  '位置好找，出品稳定，值得一试',
  '价格实惠，分量足，物超所值',
  '装修很有特色，拍照超出片',
  '口味地道，和朋友约着来的，都很喜欢',
  '上菜速度快，菜品新鲜，好评',
  '服务态度好，还会主动推荐招牌',
  '整体一般，还有提升空间',
  '朋友推荐的，果然没让人失望',
  '环境干净卫生，带家人来很放心',
  '高峰期需要排队，但等得值',
  '性价比高，学生党友好',
  '出品精致，细节到位，推荐打卡'
]

const BLOG_COMMENTS = [
  '写得好详细，改天去打卡！',
  '被种草了，感谢分享～',
  '这家我也去过，确实不错！',
  '照片拍得真好，看着就想吃',
  '博主推荐必属精品，收藏了',
  '正好在附近，周末就去',
  '请问人均大概多少呀？',
  '同款心动，已经约上朋友了',
  '看得我流口水了哈哈哈',
  '这家店服务确实好，同意博主',
  '打卡成功，确实名不虚传',
  '收藏收藏，下次带闺蜜去',
  '拍得也太馋人了，立刻下单',
  '期待博主多更这种探店笔记！'
]

function hash(str) {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619) }
  return h >>> 0
}
function pick(arr, seed) { return arr[hash(seed) % arr.length] }
function esc(s) { return String(s || '').replace(/\\/g, '').replace(/'/g, "''") }

function main() {
  const shopTsv = readFileSync(resolve(__dirname, '../seed-groupbuy/shops.tsv'), 'utf8')
  const shops = shopTsv.split('\n').filter(Boolean).map((l) => {
    const [id, type, name] = l.split('\t')
    return { id: Number(id), type: Number(type), name }
  })
  const blogTsv = readFileSync(new URL('../sd-gen/blogs_v2.tsv', import.meta.url), 'utf8')
  const blogs = blogTsv.split('\n').filter(Boolean).map((l) => {
    const [id, type, name] = l.split('\t')
    return { id: Number(id), type: Number(type), name }
  })

  const shopRows = []
  const blogRows = []
  let day = 1

  // 店铺评论：每店 5-24 条
  for (const shop of shops) {
    const n = 5 + (hash(shop.name) % 20)
    for (let i = 0; i < n; i++) {
      const seed = shop.id + ':' + i
      const userId = USERS[hash(seed + 'u') % USERS.length]
      const content = esc(pick(SHOP_COMMENTS, seed))
      const rating = 3 + (hash(seed + 'r') % 3) // 3-5
      const liked = hash(seed + 'l') % 60
      const days = (day++ % 40)
      shopRows.push(`(${shop.id}, ${userId}, '${content}', ${rating}, ${liked}, 0, DATE_SUB(NOW(), INTERVAL ${days} DAY), NOW())`)
    }
  }

  // 笔记评论：每笔记 3-19 条
  for (const blog of blogs) {
    const n = 3 + (hash(blog.id + 'b') % 17)
    for (let i = 0; i < n; i++) {
      const seed = blog.id + ':' + i
      const userId = USERS[hash(seed + 'u') % USERS.length]
      const content = esc(pick(BLOG_COMMENTS, seed))
      const liked = hash(seed + 'l') % 40
      const days = (day++ % 40)
      blogRows.push(`(${blog.id}, ${userId}, 0, 0, '${content}', ${liked}, 0, DATE_SUB(NOW(), INTERVAL ${days} DAY), NOW())`)
    }
  }

  const header = `-- 由 tools/seed-comment/gen.mjs 生成（店铺评论 + 笔记评论，计数与行数对齐）
-- 生成时间 ${new Date().toISOString()}
`
  const sql = `${header}
INSERT INTO \`tb_shop_comment\` (\`shop_id\`, \`user_id\`, \`content\`, \`rating\`, \`liked\`, \`status\`, \`create_time\`, \`update_time\`) VALUES
${shopRows.join(',\n')};

INSERT INTO \`tb_blog_comments\` (\`blog_id\`, \`user_id\`, \`parent_id\`, \`answer_id\`, \`content\`, \`liked\`, \`status\`, \`create_time\`, \`update_time\`) VALUES
${blogRows.join(',\n')};

-- 计数对齐
UPDATE \`tb_shop\` s SET s.\`comments\` = (SELECT COUNT(*) FROM \`tb_shop_comment\` c WHERE c.\`shop_id\` = s.\`id\`);
UPDATE \`tb_blog\` b SET b.\`comments\` = (SELECT COUNT(*) FROM \`tb_blog_comments\` c WHERE c.\`blog_id\` = b.\`id\`);
`
  mkdirSync(resolve(__dirname, 'dist'), { recursive: true })
  const out = resolve(__dirname, 'dist', 'seed_comments.sql')
  writeFileSync(out, sql, 'utf8')
  console.log(`生成完成：店铺评论 ${shopRows.length} 条 / 笔记评论 ${blogRows.length} 条；已写入 ${out}`)
}

main()
