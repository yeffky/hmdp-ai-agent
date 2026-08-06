#!/usr/bin/env node
// 生成 SD 批量清单 manifest.tsv（out_relpath<TAB>prompt）
// 输入: blog_types.tsv (blog_id\ttype_id)
import { readFileSync, writeFileSync } from 'node:fs'

const TYPE_PROMPT = {
  1: 'delicious chinese food, appetizing dishes on table, food photography, realistic, warm restaurant lighting',
  2: 'modern KTV karaoke room interior with microphone and neon lights, realistic photo',
  3: 'stylish hair salon interior, haircut, realistic photo',
  4: 'modern gym interior with fitness equipment, realistic photo',
  5: 'foot massage spa room, candles, towels, realistic photo',
  6: 'luxury spa treatment room, candles, orchid, realistic photo',
  7: 'colorful kids indoor playground, children play area, realistic photo',
  8: 'trendy bar counter with cocktails, neon light, realistic photo',
  9: 'party room with friends, ktv and games, realistic photo',
  10: 'nail salon manicure, colorful nail art, realistic photo'
}

const DEAL_PROMPT = {
  1: 'delicious chinese hotpot double meal set on table, overhead food photography, appetizing, realistic',
  2: 'ktv drinks and snacks set with microphone, realistic photo',
  3: 'hair salon care package, scissors and comb, realistic photo',
  4: 'fitness gym day pass, dumbbells, realistic photo',
  5: 'foot spa treatment set, essential oils, realistic photo',
  6: 'luxury spa facial care set, cream and towel, realistic photo',
  7: 'kids playground day pass, colorful, realistic photo',
  8: 'craft beer set with glasses, bar, realistic photo',
  9: 'party room package with ktv and board games, realistic photo',
  10: 'manicure set, nail polish colors, realistic photo'
}

const lines = []
const tsv = readFileSync(new URL('./blog_types.tsv', import.meta.url), 'utf8')
for (const line of tsv.split('\n').filter(Boolean)) {
  const [id, type] = line.split('\t')
  lines.push(`blogs/sd/${id}.png\t${TYPE_PROMPT[Number(type)] || TYPE_PROMPT[1]}`)
}
for (const [type, prompt] of Object.entries(DEAL_PROMPT)) {
  lines.push(`deals/type_${type}.png\t${prompt}`)
}
writeFileSync(new URL('./manifest.tsv', import.meta.url), lines.join('\n') + '\n', 'utf8')
console.log(`manifest 生成：${lines.length} 条（100 笔记 + ${Object.keys(DEAL_PROMPT).length} 类目团购封面）`)
