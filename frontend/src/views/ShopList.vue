<script setup>
import { ref, watch, nextTick, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import { shopTypeApi, shopApi } from '../api'
import { formatDistance } from '../utils/format'
import { buildShopParams } from '../utils/shopQuery'
import { useLocationStore } from '../stores/location'

const route = useRoute()
const router = useRouter()
const loc = useLocationStore()

const types = ref([])
const shops = ref([])
const keyword = ref('')
const isSearch = computed(() => route.query.q !== undefined)

const current = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)
const loading = ref(false)
const loaded = ref(false)
const sortBy = ref('') // '' 综合 / distance 距离 / comments 人气 / score 评分

const activeType = computed(() => Number(route.query.type) || 0)
const sentinel = ref(null)
let observer = null

const title = computed(() => {
  if (isSearch.value && keyword.value) return `搜索：${keyword.value}`
  return route.query.name || '店铺'
})

onMounted(async () => {
  keyword.value = route.query.q || ''
  await loc.ensure()
  try {
    const t = await shopTypeApi.list()
    types.value = t || []
  } catch { /* 分类加载失败忽略 */ }
  await fetchShops(false)
  loaded.value = true
  await fillViewport()
  // 双保险：window scroll + IntersectionObserver，任一触发即可加载更多
  window.addEventListener('scroll', onScroll, { passive: true })
  observer = new IntersectionObserver(onSentinel, { rootMargin: '160px 0px' })
  if (sentinel.value) observer.observe(sentinel.value)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  observer && observer.disconnect()
})

function onScroll() {
  const el = document.scrollingElement || document.documentElement
  if (el.scrollTop + window.innerHeight >= el.scrollHeight - 100 && hasMore.value && !loading.value) {
    fetchShops(true)
  }
}

function onSentinel(entries) {
  if (entries[0].isIntersecting && hasMore.value && !loading.value) {
    fetchShops(true)
  }
}

// 切换分类 / 进入搜索时路由复用，需监听 query 重新加载
watch(
  () => route.query,
  async () => {
    keyword.value = route.query.q || ''
    shops.value = []
    hasMore.value = true
    await fetchShops(false)
    await fillViewport()
  }
)

function switchType(id, name) {
  router.push({ path: '/shop-list', query: { type: id, name } })
}

// 搜索限定在当前分类内：携带 type + name，另加 q 作为搜索词
function doSearch() {
  const q = keyword.value.trim()
  const query = { type: activeType.value || undefined, name: route.query.name }
  if (q) query.q = q
  router.push({ path: '/shop-list', query })
}

function buildParams(page) {
  const cur = loc.current || {}
  return buildShopParams(route.query, types.value, page, sortBy.value, {
    districtId: cur.districtId,
    x: cur.centerX,
    y: cur.centerY
  })
}

async function fetchShops(append) {
  if (append && loading.value) return 0
  const params = buildParams(append ? current.value + 1 : 1)
  if (!params) return 0
  loading.value = true
  if (append) loadingMore.value = true
  try {
    let list
    if (isSearch.value) list = (await shopApi.ofName(params)) || []
    else list = (await shopApi.ofType(params)) || []
    list.forEach((s) => {
      s.images = (s.images || '').split(',')[0]
      s.rating = (s.score || 0) / 10
      s.price = s.avgPrice
      s.distText = formatDistance(s.distance)
    })
    if (append) {
      shops.value = shops.value.concat(list)
      if (list.length) current.value += 1
    } else {
      shops.value = list
      current.value = 1
    }
    hasMore.value = list.length >= (isSearch.value ? 10 : 5)
    return list.length
  } catch (e) {
    /* 加载失败静默，下次触发重试 */
    return 0
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

// 内容不足一屏且还有更多时继续加载，保证页面可滚动（设备仿真/小视口下尤其需要）
async function fillViewport() {
  while (hasMore.value && document.documentElement.scrollHeight <= window.innerHeight) {
    const n = await fetchShops(true)
    if (n <= 0) break
    await nextTick()
  }
}

async function applySort(key) {
  sortBy.value = key
  shops.value = []
  hasMore.value = true
  current.value = 1
  await fetchShops(false)
  await fillViewport()
  // 人气/评分由后端 ORDER BY 处理，与分页保持一致；距离走后端 geo
}

function toDetail(s) {
  router.push(`/shop/${s.id}`)
}
</script>

<template>
  <div class="shop-list page no-tabbar">
    <AppHeader :title="title" />

    <!-- 搜索：常驻，可限定在当前分类内 -->
    <nav class="shop-list__search">
      <input
        v-model="keyword"
        :placeholder="isSearch ? '输入店铺名搜索' : `在${route.query.name || '全部店铺'}中搜索`"
        @keyup.enter="doSearch"
      />
      <button @click="doSearch">搜索</button>
    </nav>
    <nav v-if="!isSearch" class="chip-strip">
      <button
        v-for="t in types"
        :key="t.id"
        class="chip"
        :class="{ 'is-active': activeType === t.id }"
        @click="switchType(t.id, t.name)"
      >
        <img :src="`/imgs/${t.icon}`" alt="" loading="lazy" />
        {{ t.name }}
      </button>
    </nav>

    <!-- 排序 -->
    <div class="shop-list__sort">
      <span class="shop-list__sort-label">排序</span>
      <button
        v-for="opt in [
          { key: '', label: '综合' },
          { key: 'distance', label: '距离' },
          { key: 'comments', label: '人气' },
          { key: 'score', label: '评分' }
        ]"
        :key="opt.key"
        :class="{ 'is-active': sortBy === opt.key }"
        @click="applySort(opt.key)"
      >{{ opt.label }}</button>
    </div>

    <!-- 列表 -->
    <div v-if="shops.length" class="shop-list__items">
      <article v-for="s in shops" :key="s.id" class="shop-card ticket" @click="toDetail(s)">
        <div class="shop-card__img">
          <img :src="s.images" alt="" loading="lazy" />
        </div>
        <div class="shop-card__info">
          <h3 class="shop-card__name">{{ s.name }}</h3>
          <div class="shop-card__meta">
            <span class="shop-card__score num">{{ s.rating.toFixed(1) }}</span>
            <span class="shop-card__bar" :style="{ '--w': `${Math.max(8, s.rating * 18)}%` }" />
            <span class="shop-card__dim">{{ s.comments }}条</span>
          </div>
          <div class="shop-card__meta">
            <span class="shop-card__dist">
              <AppIcon name="pin" :size="12" />
              {{ s.distText || s.area }}
            </span>
            <span class="shop-card__price">¥{{ s.price }}/人</span>
          </div>
          <div class="shop-card__addr ellipsis">
            <AppIcon name="pin" :size="12" />
            {{ s.address }}
          </div>
        </div>
      </article>
    </div>

    <EmptyState v-else-if="loaded" icon="map" :text="isSearch ? '没有找到相关店铺' : '该分类暂无店铺'" />
    <!-- 哨兵：IntersectionObserver 触发加载更多 -->
    <div ref="sentinel" class="shop-list__sentinel">
      <p v-if="loadingMore" class="list-end">加载中…</p>
      <p v-else-if="!hasMore && shops.length" class="list-end">— 已加载全部 —</p>
    </div>
  </div>
</template>

<style scoped>
.shop-list__search {
  display: flex;
  gap: 8px;
  padding: 10px var(--gap-md);
}
.shop-list__search input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--card);
  font-size: var(--text-base);
  outline: none;
}
.shop-list__search input:focus { border-color: var(--vermilion); }
.shop-list__search button {
  flex: none;
  padding: 0 18px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-sm);
  cursor: pointer;
}

.shop-list__sort {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px var(--gap-md) 10px;
  overflow-x: auto;
  scrollbar-width: none;
}
.shop-list__sort::-webkit-scrollbar { display: none; }
.shop-list__sort-label { font-size: var(--text-xs); color: var(--ink-3); margin-right: 4px; flex: none; }
.shop-list__sort button {
  flex: none;
  border: none;
  background: none;
  color: var(--ink-2);
  font-size: var(--text-sm);
  padding: 6px 10px;
  border-radius: var(--radius-pill);
  cursor: pointer;
}
.shop-list__sort button.is-active { background: var(--vermilion-soft); color: var(--vermilion); font-weight: 700; }

.shop-list__items { padding: 0 var(--gap-md); display: flex; flex-direction: column; gap: var(--gap-md); }
.shop-list__sentinel { min-height: 24px; }
.shop-card {
  display: flex;
  gap: var(--gap-md);
  padding: 10px;
  cursor: pointer;
}
.shop-card__img {
  width: 104px;
  height: 104px;
  flex: none;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--line);
}
.shop-card__img img { width: 100%; height: 100%; object-fit: cover; }
.shop-card__info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: space-between; }
.shop-card__name {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-card__meta { display: flex; align-items: center; gap: 6px; font-size: var(--text-xs); }
.shop-card__score { color: var(--amber); font-size: var(--text-md); }
.shop-card__bar {
  width: 40px;
  height: 4px;
  border-radius: 2px;
  background: var(--line);
  position: relative;
  overflow: hidden;
}
.shop-card__bar::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: var(--w);
  background: var(--amber);
  border-radius: 2px;
}
.shop-card__dim { color: var(--ink-3); }
.shop-card__dist { display: inline-flex; align-items: center; gap: 2px; color: var(--ink-2); }
.shop-card__price { color: var(--amber); font-weight: 700; font-family: var(--font-display); margin-left: auto; }
.shop-card__addr {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-3);
  font-size: var(--text-xs);
}
</style>
