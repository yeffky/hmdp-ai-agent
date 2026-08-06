<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { shopApi, shopTypeApi } from '../api'
import { useLocationStore } from '../stores/location'
import { shopMarkerContent, shopMarkerLabel } from '../utils/shopMarker'
import AppTabbar from '../components/AppTabbar.vue'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const loc = useLocationStore()

const mapEl = ref(null)
const types = ref([])
const activeType = ref(0) // 0 = 全部
const loaded = ref(false)
const mapError = ref('')

let map = null
let markers = []
let shops = []
let mapLoaded = false

function loadAmap() {
  return new Promise((resolve, reject) => {
    if (window.AMap) return resolve()
    const key = import.meta.env.VITE_AMAP_JS_KEY
    if (!key) return reject(new Error('缺少高德 JS key（.env 的 VITE_AMAP_JS_KEY）'))
    const existing = document.querySelector('script[data-amap]')
    if (existing) {
      existing.addEventListener('load', () => resolve())
      existing.addEventListener('error', () => reject(new Error('高德 JS 加载失败')))
      return
    }
    const s = document.createElement('script')
    s.src = `https://webapi.amap.com/maps?v=2.0&key=${key}`
    s.dataset.amap = '1'
    s.onload = () => {
      mapLoaded = true
      resolve()
    }
    s.onerror = () => reject(new Error('高德 JS 加载失败'))
    document.head.appendChild(s)
  })
}

onMounted(async () => {
  await loc.ensure()
  try {
    types.value = (await shopTypeApi.list()) || []
  } catch {
    /* 分类加载失败忽略 */
  }
  try {
    await loadAmap()
  } catch (e) {
    mapError.value = e.message || '地图加载失败'
    loaded.value = true
    return
  }
  initMap()
})

onBeforeUnmount(() => {
  if (map) map.destroy()
})

function initMap() {
  const cur = loc.current || {}
  map = new window.AMap.Map(mapEl.value, {
    zoom: 13,
    center: [cur.centerX || 119.3026, cur.centerY || 26.0855]
  })
  loadShops()
}

async function loadShops() {
  const cur = loc.current || {}
  if (!cur.districtId) {
    loaded.value = true
    return
  }
  try {
    shops = (await shopApi.forMap({
      districtId: cur.districtId,
      typeId: activeType.value || undefined
    })) || []
  } catch {
    shops = []
  }
  loaded.value = true
  renderMarkers()
}

function clearMarkers() {
  markers.forEach((m) => map && map.remove(m))
  markers = []
}

function renderMarkers() {
  clearMarkers()
  markers = shops.map((s) => {
    const m = new window.AMap.Marker({
      position: [s.x, s.y],
      content: shopMarkerContent(s),
      offset: new window.AMap.Pixel(-9, -9), // 14px 圆点 + 2px 描边，居中定位
      title: s.name
    })
    m.on('click', () => router.push(`/shop/${s.id}`))
    m.on('mouseover', () => m.setLabel({ content: shopMarkerLabel(s), direction: 'top' }))
    m.on('mouseout', () => m.setLabel({ content: '' }))
    map.add(m)
    return m
  })
  if (shops.length) {
    map.setFitView(markers, false, [70, 70, 70, 70])
  }
}

function switchType(id) {
  activeType.value = id
  if (map) loadShops()
}
</script>

<template>
  <div class="map-page">
    <div class="map-page__top">
      <div class="map-page__loc">
        <span>{{ loc.label || '选择定位' }}</span>
        <span class="map-page__count num">{{ shops.length }}</span>
      </div>
    </div>

    <nav v-if="types.length" class="map-page__chips">
      <button
        class="chip"
        :class="{ 'is-active': activeType === 0 }"
        @click="switchType(0)"
      >全部</button>
      <button
        v-for="t in types"
        :key="t.id"
        class="chip"
        :class="{ 'is-active': activeType === t.id }"
        @click="switchType(t.id)"
      >{{ t.name }}</button>
    </nav>

    <div v-if="mapError" class="map-page__placeholder">
      <EmptyState icon="map" :text="`地图暂不可用：${mapError}`" />
    </div>
    <div v-else ref="mapEl" class="map-page__canvas" />

    <AppTabbar />
  </div>
</template>

<style scoped>
.map-page {
  position: relative;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.map-page__top {
  flex: none;
  padding: 10px var(--gap-md);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.map-page__loc {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}
.map-page__count {
  color: var(--vermilion);
  font-size: var(--text-sm);
}
.map-page__chips {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 0 var(--gap-md) 10px;
  overflow-x: auto;
  scrollbar-width: none;
}
.map-page__chips::-webkit-scrollbar { display: none; }
.map-page__chips .chip { flex: none; }
.map-page__canvas {
  flex: 1;
  width: 100%;
  background: var(--line);
}
.map-page__placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
