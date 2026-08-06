<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import AppTabbar from '../components/AppTabbar.vue'
import EmptyState from '../components/EmptyState.vue'
import { shopTypeApi, blogApi } from '../api'
import { useUserStore } from '../stores/user'
import { useChatStore } from '../stores/chat'
import { useLocationStore } from '../stores/location'

const router = useRouter()
const userStore = useUserStore()
const chat = useChatStore()
const loc = useLocationStore()

const pickerOpen = ref(false)
const activeCity = ref(null)

const types = ref([])
const blogs = ref([])
const current = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)
const loaded = ref(false)

const greeter = computed(() => {
  if (userStore.isLoggedIn && userStore.profile) return `Hi，${userStore.profile.nickName}`
  return '欢迎来到生活优选'
})

const conciergeTips = ['帮我取个号', '附近有什么好吃的', '怎么退款？']

onMounted(async () => {
  await loc.ensure()
  try {
    const [t, first] = await Promise.all([shopTypeApi.list(), blogApi.hot(1)])
    types.value = t || []
    blogs.value = (first || []).map((b) => ({ ...b, img: (b.images || '').split(',')[0] }))
    current.value = 1
    hasMore.value = (first || []).length >= 10
  } catch {
    /* 首页加载失败静默 */
  } finally {
    loaded.value = true
    window.addEventListener('scroll', onScroll, { passive: true })
  }
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

function onScroll() {
  const st = window.scrollY
  const ch = document.documentElement.clientHeight
  const sh = document.documentElement.scrollHeight
  if (st + ch >= sh - 100 && hasMore.value && !loadingMore.value) loadMore()
}

async function loadMore() {
  loadingMore.value = true
  try {
    current.value += 1
    const list = (await blogApi.hot(current.value)) || []
    list.forEach((b) => (b.img = (b.images || '').split(',')[0]))
    blogs.value = blogs.value.concat(list)
    hasMore.value = list.length >= 10
  } catch {
    current.value -= 1
  } finally {
    loadingMore.value = false
  }
}

function toShopList(type) {
  router.push({ path: '/shop-list', query: { type: type.id, name: type.name } })
}

function openPicker() {
  activeCity.value =
    (loc.current && loc.regions.find((c) => c.id === loc.current.cityId)) ||
    loc.regions[0] ||
    null
  pickerOpen.value = true
}

function chooseDistrict(d) {
  if (activeCity.value) loc.selectDistrict(activeCity.value, d)
  pickerOpen.value = false
}
function toBlog(b) {
  router.push(`/blog/${b.id}`)
}
function askBlack(text) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  chat.input = text
  chat.open()
}
async function toggleLike(b) {
  try {
    await blogApi.toggleLike(b.id)
    b.isLike = !b.isLike
    b.liked = (b.liked || 0) + (b.isLike ? 1 : -1)
  } catch {
    ElMessage.error('操作失败')
  }
}
</script>

<template>
  <div class="home page">
    <!-- 品牌头 -->
    <header class="home__head">
      <div class="home__brand">
        <span class="home__logo num">号</span>
        <div>
          <b>生活优选</b>
          <button class="home__loc" aria-label="切换城市/地区" @click="openPicker">
            <AppIcon name="pin" :size="12" />
            <span>{{ loc.label || '选择定位' }}</span>
          </button>
        </div>
      </div>
      <button class="home__search" @click="router.push('/shop-list?q=')">
        <AppIcon name="search" :size="16" />
        <span>想吃点什么？</span>
      </button>
    </header>

    <!-- AI 助手英雄卡 -->
    <div class="home__concierge">
      <div class="concierge">
        <div class="concierge__hi">{{ greeter }}</div>
        <div class="concierge__name">小优在，帮你取号</div>
        <div class="concierge__tips">
          <button
            v-for="tip in conciergeTips"
            :key="tip"
            class="concierge__tip"
            @click="askBlack(tip)"
          >{{ tip }}</button>
        </div>
        <button class="concierge__chat" @click="chat.open()">
          <AppIcon name="chat" :size="16" /> 找小优聊聊
        </button>
      </div>
    </div>

    <!-- 分类条 -->
    <nav class="chip-strip" aria-label="店铺分类">
      <button
        v-for="t in types"
        :key="t.id"
        class="chip"
        @click="toShopList(t)"
      >
        <img :src="`/imgs/${t.icon}`" alt="" loading="lazy" />
        {{ t.name }}
      </button>
    </nav>

    <!-- 热门探店 -->
    <section class="home__feed">
      <h2 class="home__section-title">
        <span class="stamp">热</span>
        达人探店
      </h2>

      <div v-if="blogs.length" class="blog-grid">
        <article v-for="b in blogs" :key="b.id" class="blog-card" @click="toBlog(b)">
          <div class="blog-card__img">
            <img :src="b.img" alt="" loading="lazy" />
          </div>
          <h3 class="blog-card__title">{{ b.title }}</h3>
          <footer class="blog-card__foot">
            <img class="blog-card__avatar" :src="b.icon || '/imgs/icons/default-icon.png'" alt="" />
            <span class="blog-card__author ellipsis">{{ b.name }}</span>
            <button class="blog-card__like" :class="{ 'is-active': b.isLike }" @click.stop="toggleLike(b)">
              <AppIcon name="heart" :size="15" />
              <span class="num">{{ b.liked }}</span>
            </button>
          </footer>
        </article>
      </div>

      <EmptyState v-else-if="loaded && !blogs.length" icon="heart" text="还没有探店笔记，来发第一篇吧" action="发布笔记" @action="router.push('/blog/edit')" />

      <p v-if="loadingMore" class="list-end">加载中…</p>
      <p v-else-if="!hasMore && blogs.length" class="list-end">— 到底啦 —</p>
    </section>

    <AppTabbar />

    <!-- 城市/地区选择器 -->
    <div v-if="pickerOpen" class="home__mask" @click.self="pickerOpen = false">
      <div class="home__picker">
        <div class="home__picker-head">
          <span>选择城市/地区</span>
          <button aria-label="关闭" @click="pickerOpen = false">
            <AppIcon name="close" :size="16" />
          </button>
        </div>
        <div class="home__picker-body">
          <div class="home__picker-cities">
            <button
              v-for="c in loc.regions"
              :key="c.id"
              class="home__picker-city"
              :class="{ 'is-active': activeCity && activeCity.id === c.id }"
              @click="activeCity = c"
            >{{ c.name }}</button>
          </div>
          <div class="home__picker-districts">
            <button
              v-for="d in (activeCity ? activeCity.districts : [])"
              :key="d.id"
              class="home__picker-district"
              :class="{ 'is-active': loc.current && loc.current.districtId === d.id }"
              @click="chooseDistrict(d)"
            >{{ d.name }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home { padding-bottom: calc(var(--tabbar-h) + 24px); }

/* 品牌头 */
.home__head {
  display: flex;
  align-items: center;
  gap: var(--gap-md);
  padding: var(--gap-lg) var(--gap-md) var(--gap-sm);
}
.home__brand { display: flex; align-items: center; gap: 8px; flex: none; }
.home__logo {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  background: var(--vermilion);
  color: #fff;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-card);
}
.home__brand b { font-size: var(--text-lg); letter-spacing: 1px; }
.home__brand small { display: block; font-size: var(--text-xs); color: var(--ink-3); }
.home__search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  background: var(--card);
  border: 1px solid var(--line);
  color: var(--ink-3);
  font-size: var(--text-sm);
  cursor: pointer;
}

/* 英雄区 */
.home__concierge { padding: 0 var(--gap-md) var(--gap-md); }

/* 分类条 */
.chip-strip { padding-bottom: var(--gap-md); }

/* 探店流 */
.home__feed { padding: 0 var(--gap-md); }
.home__section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--text-lg);
  margin: 4px 0 var(--gap-md);
}
.blog-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gap-md);
}
.blog-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  cursor: pointer;
}
.blog-card__img {
  aspect-ratio: 1 / 1;
  background: var(--line);
}
.blog-card__img img { width: 100%; height: 100%; object-fit: cover; }
.blog-card__title {
  margin: 8px 10px 6px;
  font-size: var(--text-base);
  font-weight: 600;
  line-height: 1.35;
  height: 36px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.blog-card__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px 10px;
}
.blog-card__avatar { width: 18px; height: 18px; border-radius: 50%; }
.blog-card__author { flex: 1; font-size: var(--text-xs); color: var(--ink-2); }
.blog-card__like {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: none;
  color: var(--ink-3);
  font-size: var(--text-xs);
  cursor: pointer;
}
.blog-card__like.is-active { color: var(--vermilion); }

/* 定位选择器 */
.home__loc {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 2px;
  border: none;
  background: none;
  padding: 0;
  color: var(--ink-3);
  font-size: var(--text-xs);
  cursor: pointer;
}
.home__loc:hover { color: var(--vermilion); }

.home__mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.home__picker {
  width: 100%;
  max-width: 480px;
  background: var(--paper);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: var(--gap-md);
}
.home__picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  margin-bottom: var(--gap-sm);
}
.home__picker-head button {
  border: none;
  background: none;
  color: var(--ink-3);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}
.home__picker-body { display: flex; gap: var(--gap-md); }
.home__picker-cities {
  flex: none;
  width: 88px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.home__picker-city {
  border: none;
  background: none;
  text-align: left;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  color: var(--ink-2);
  font-size: var(--text-sm);
  cursor: pointer;
}
.home__picker-city.is-active { background: var(--vermilion-soft); color: var(--vermilion); font-weight: 700; }
.home__picker-districts {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
}
.home__picker-district {
  border: 1px solid var(--line);
  background: var(--card);
  border-radius: var(--radius-pill);
  padding: 6px 14px;
  font-size: var(--text-sm);
  color: var(--ink-2);
  cursor: pointer;
}
.home__picker-district.is-active { border-color: var(--vermilion); color: var(--vermilion); font-weight: 700; }
</style>
