// 金额：后端以「分」存储，这里统一转成「元」字符串
export function fenToYuan(fen) {
  if (fen === null || fen === undefined || fen === '') return null
  const n = Number(fen)
  if (Number.isNaN(n)) return null
  return (n / 100).toFixed(2)
}

// 折扣：payValue 分 / actualValue 分 → 如 "8.5折"
export function discount(payValueFen, actualValueFen) {
  if (!payValueFen || !actualValueFen) return null
  const d = (payValueFen * 10) / actualValueFen
  return `${d.toFixed(1)}折`
}

// 距离：米 → "350m" / "1.2km"
export function formatDistance(m) {
  if (m === null || m === undefined) return ''
  const n = Number(m)
  if (Number.isNaN(n)) return ''
  return n < 1000 ? `${n.toFixed(1)}m` : `${(n / 1000).toFixed(1)}km`
}

function pad2(n) {
  return n < 10 ? `0${n}` : `${n}`
}

// 秒杀时间区间：begin ~ end → "8月3日 10:00 ~ 22:00"
export function formatRange(begin, end) {
  const b = new Date(begin)
  const e = new Date(end)
  if (Number.isNaN(b.getTime()) || Number.isNaN(e.getTime())) return ''
  return `${b.getMonth() + 1}月${b.getDate()}日 ${pad2(b.getHours())}:${pad2(b.getMinutes())} ~ ${pad2(e.getHours())}:${pad2(e.getMinutes())}`
}

// 日期 → "2026年8月3日"
export function formatDate(str) {
  const d = new Date(str)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

// 相对时间 → "刚刚 / 5分钟前 / 3小时前 / 昨天 / 8月3日"
export function relativeTime(str) {
  const d = new Date(str)
  if (Number.isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const min = 60 * 1000
  const hour = 60 * min
  const day = 24 * hour
  if (diff < min) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / min)}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`
  if (diff < 2 * day) return '昨天'
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

// 秒杀状态：isNotBegin / isEnd
export const seckillState = (v, now = Date.now()) => {
  const begin = new Date(v.beginTime).getTime()
  const end = new Date(v.endTime).getTime()
  if (Number.isNaN(begin) || Number.isNaN(end)) return 'unknown'
  if (now < begin) return 'not_begin'
  if (now > end) return 'ended'
  return 'active'
}
