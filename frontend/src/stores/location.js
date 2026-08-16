import { defineStore } from 'pinia'
import { regionApi } from '../api'

// 默认定位：福州市鼓楼区
const DEFAULT_LOCATION = {
  cityId: 2,
  districtId: 2,
  cityName: '福州',
  districtName: '鼓楼区',
  centerX: 119.3026,
  centerY: 26.0855
}

const STORAGE_KEY = 'hmdp.location'

export const useLocationStore = defineStore('location', {
  state: () => ({
    regions: [], // [{ id, name, districts: [{ id, cityId, name, centerX, centerY }] }]
    current: null,
    loaded: false
  }),
  getters: {
    label: (s) => (s.current ? `${s.current.cityName} · ${s.current.districtName}` : ''),
    // 当前地区 id（1拱墅/2鼓楼），未选时兜底默认福州鼓楼
    districtId: (s) => s.current?.districtId ?? DEFAULT_LOCATION.districtId,
    // 当前地区中心坐标（getter：从 current 读，未恢复时兜底默认福州鼓楼）
    centerX: (s) => s.current?.centerX ?? DEFAULT_LOCATION.centerX,
    centerY: (s) => s.current?.centerY ?? DEFAULT_LOCATION.centerY
  },
  actions: {
    async ensure() {
      if (this.loaded) return
      try {
        this.regions = (await regionApi.list()) || []
      } catch {
        /* 接口失败用默认定位兜底 */
      }
      this.loaded = true
      this.restore()
    },
    restore() {
      let saved = null
      try {
        saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
      } catch {
        /* 忽略损坏的缓存 */
      }
      this.select(saved || DEFAULT_LOCATION)
    },
    select(sel) {
      if (!sel) return
      this.current = sel
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sel))
    },
    selectDistrict(city, district) {
      this.select({
        cityId: city.id,
        districtId: district.id,
        cityName: city.name,
        districtName: district.name,
        centerX: district.centerX,
        centerY: district.centerY
      })
    }
  }
})
