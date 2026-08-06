import { describe, it, expect } from 'vitest'
import { buildShopParams } from '../utils/shopQuery'

describe('buildShopParams', () => {
  const types = [{ id: 1 }, { id: 2 }]
  const loc = { districtId: 2, x: 119.3026, y: 26.0855 }

  it('分类浏览：默认(综合)按 shopType 列表，不带坐标', () => {
    expect(buildShopParams({ type: '1', name: '美食' }, types)).toEqual({
      current: 1,
      typeId: 1
    })
  })

  it('分类浏览：带地区时附加 districtId', () => {
    expect(buildShopParams({ type: '1', name: '美食' }, types, 1, '', { districtId: 2 })).toEqual({
      current: 1,
      typeId: 1,
      districtId: 2
    })
  })

  it('距离排序：带地区圆心坐标，走后端 geo 距离序', () => {
    expect(buildShopParams({ type: '1', name: '美食' }, types, 1, 'distance', loc)).toEqual({
      current: 1,
      typeId: 1,
      districtId: 2,
      x: 119.3026,
      y: 26.0855
    })
  })

  it('人气排序：sortBy=comments 传给后端 ORDER BY', () => {
    expect(buildShopParams({ type: '1', name: '美食' }, types, 1, 'comments', loc)).toEqual({
      current: 1,
      typeId: 1,
      districtId: 2,
      sortBy: 'comments'
    })
  })

  it('评分排序：搜索时 sortBy=score 也传给后端', () => {
    expect(buildShopParams({ type: '1', name: '美食', q: '烤鱼' }, types, 1, 'score', loc)).toEqual({
      current: 1,
      name: '烤鱼',
      typeId: 1,
      districtId: 2,
      sortBy: 'score'
    })
  })

  it('综合排序：不传 sortBy，也不带坐标', () => {
    expect(buildShopParams({ type: '1', name: '美食' }, types, 1, '', loc)).toEqual({
      current: 1,
      typeId: 1,
      districtId: 2
    })
  })

  it('未指定分类时回退到第一个类型', () => {
    expect(buildShopParams({}, types).typeId).toBe(1)
  })

  it('分类内搜索：携带 typeId 与 districtId 限定范围', () => {
    expect(buildShopParams({ type: '1', name: '美食', q: '烤鱼' }, types, 1, '', loc)).toEqual({
      current: 1,
      name: '烤鱼',
      typeId: 1,
      districtId: 2
    })
  })

  it('全局搜索：无 typeId/districtId', () => {
    expect(buildShopParams({ q: '烤鱼' }, types)).toEqual({ current: 1, name: '烤鱼' })
  })

  it('搜索关键词为空返回 null', () => {
    expect(buildShopParams({ q: '' }, types)).toBeNull()
  })

  it('分页透传 current', () => {
    expect(buildShopParams({ type: '1' }, types, 3).current).toBe(3)
  })
})
