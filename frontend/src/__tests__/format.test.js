import { describe, it, expect } from 'vitest'
import {
  fenToYuan,
  discount,
  formatDistance,
  formatRange,
  formatDate,
  relativeTime,
  seckillState
} from '../utils/format'

describe('fenToYuan', () => {
  it('分转元', () => {
    expect(fenToYuan(1200)).toBe('12.00')
    expect(fenToYuan(1)).toBe('0.01')
    expect(fenToYuan(0)).toBe('0.00')
  })
  it('空值返回 null', () => {
    expect(fenToYuan(null)).toBeNull()
    expect(fenToYuan('')).toBeNull()
    expect(fenToYuan('abc')).toBeNull()
  })
})

describe('discount', () => {
  it('按支付价/面额算折扣', () => {
    expect(discount(1200, 2000)).toBe('6.0折')
    expect(discount(1000, 1000)).toBe('10.0折')
  })
  it('缺失返回 null', () => {
    expect(discount(0, 100)).toBeNull()
    expect(discount(null, 100)).toBeNull()
  })
})

describe('formatDistance', () => {
  it('米与公里', () => {
    expect(formatDistance(350)).toBe('350.0m')
    expect(formatDistance(1234)).toBe('1.2km')
  })
  it('空值返回空串', () => {
    expect(formatDistance(null)).toBe('')
    expect(formatDistance(undefined)).toBe('')
  })
})

describe('formatRange', () => {
  it('格式化秒杀时间区间', () => {
    const s = formatRange('2026-08-03T10:00:00', '2026-08-03T22:30:00')
    expect(s).toBe('8月3日 10:00 ~ 22:30')
  })
  it('非法输入返回空串', () => {
    expect(formatRange('bad', 'worse')).toBe('')
  })
})

describe('formatDate', () => {
  it('格式化日期', () => {
    expect(formatDate('2026-08-03T00:00:00')).toBe('2026年8月3日')
  })
  it('非法输入返回空串', () => {
    expect(formatDate('bad')).toBe('')
  })
})

describe('relativeTime', () => {
  const now = Date.now()
  const min = 60 * 1000
  const hour = 60 * min
  const day = 24 * hour
  it('分级相对时间', () => {
    expect(relativeTime(new Date(now - 10 * 1000).toISOString())).toBe('刚刚')
    expect(relativeTime(new Date(now - 5 * min).toISOString())).toBe('5分钟前')
    expect(relativeTime(new Date(now - 3 * hour).toISOString())).toBe('3小时前')
    expect(relativeTime(new Date(now - 1 * day).toISOString())).toBe('昨天')
  })
  it('非法输入返回空串', () => {
    expect(relativeTime('bad')).toBe('')
  })
})

describe('seckillState', () => {
  const now = new Date('2026-08-03T12:00:00').getTime()
  it('未开始 / 进行中 / 已结束', () => {
    expect(
      seckillState({ beginTime: '2026-08-03T13:00:00', endTime: '2026-08-03T20:00:00' }, now)
    ).toBe('not_begin')
    expect(
      seckillState({ beginTime: '2026-08-03T10:00:00', endTime: '2026-08-03T20:00:00' }, now)
    ).toBe('active')
    expect(
      seckillState({ beginTime: '2026-08-03T10:00:00', endTime: '2026-08-03T11:00:00' }, now)
    ).toBe('ended')
  })
})
