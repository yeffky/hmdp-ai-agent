import { describe, it, expect } from 'vitest'
import { shopMarkerColor, shopMarkerContent, shopMarkerLabel } from '../utils/shopMarker'

describe('shopMarkerColor', () => {
  it('按店铺类型返回对应低饱和颜色', () => {
    expect(shopMarkerColor(1)).toBe('#E2482D') // 美食
    expect(shopMarkerColor(8)).toBe('#5A5AE0') // 酒吧
    expect(shopMarkerColor(10)).toBe('#E05AA0') // 美甲
  })
  it('未知类型回退到中性灰', () => {
    expect(shopMarkerColor(99)).toBe('#8a8f98')
    expect(shopMarkerColor(undefined)).toBe('#8a8f98')
  })
})

describe('shopMarkerContent', () => {
  it('生成 14px 半透明圆点，颜色取自类型', () => {
    const html = shopMarkerContent({ typeId: 1 })
    expect(html).toContain('width:14px')
    expect(html).toContain('opacity:.55')
    expect(html).toContain('#E2482D')
    expect(html).toContain('rgba(255,255,255,.95)')
  })
})

describe('shopMarkerLabel', () => {
  it('hover 标签包含店名', () => {
    expect(shopMarkerLabel({ name: '星巴克' })).toContain('星巴克')
  })
})
