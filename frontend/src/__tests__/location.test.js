import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useLocationStore } from '../stores/location'

describe('location store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('默认定位为福州鼓楼区', () => {
    const loc = useLocationStore()
    loc.restore()
    expect(loc.current).toMatchObject({ cityId: 2, districtId: 2, cityName: '福州', districtName: '鼓楼区' })
  })

  it('selectDistrict 记录所选城市/地区与圆心', () => {
    const loc = useLocationStore()
    loc.selectDistrict(
      { id: 1, name: '杭州' },
      { id: 1, name: '拱墅区', centerX: 120.147, centerY: 30.325 }
    )
    expect(loc.current).toMatchObject({
      cityId: 1,
      districtId: 1,
      cityName: '杭州',
      districtName: '拱墅区',
      centerX: 120.147,
      centerY: 30.325
    })
    expect(loc.label).toBe('杭州 · 拱墅区')
  })

  it('select 持久化到 localStorage', () => {
    const loc = useLocationStore()
    loc.select({ cityId: 2, districtId: 2, cityName: '福州', districtName: '鼓楼区', centerX: 1, centerY: 2 })
    const saved = JSON.parse(localStorage.getItem('hmdp.location'))
    expect(saved.districtId).toBe(2)
  })
})
