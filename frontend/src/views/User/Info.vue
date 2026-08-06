<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '../../components/AppHeader.vue'
import AppTabbar from '../../components/AppTabbar.vue'
import AppIcon from '../../components/AppIcon.vue'
import EmptyState from '../../components/EmptyState.vue'
import { blogApi, userApi } from '../../api'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()

const info = ref(null)
const blogs = ref([])
const feed = ref([])
const signCount = ref(0)
const signToday = ref(false)
const signing = ref(false)
const tab = ref('notes')

const feedParams = ref({ minTime: 0, offset: 0 })
const feedMore = ref(true)
const loadingFeed = ref(false)

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.replace('/login')
    return
  }
  try {
    await userStore.loadMe(true)
    const [myBlogs, count, today] = await Promise.all([
      blogApi.ofMe(1),
      userApi.signCount(),
      userApi.signToday()
    ])
    await userStore.loadInfo(userStore.profile.id, true)
    info.value = userStore.info
    blogs.value = myBlogs || []
    signCount.value = count || 0
    signToday.value = !!today
  } catch {
    /* 加载失败 */
  }
})

async function doSign() {
  if (signing.value || signToday.value) return
  signing.value = true
  try {
    await userApi.sign()
    ElMessage.success('签到成功')
    signToday.value = true
    signCount.value = (await userApi.signCount()) || 0
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '签到失败')
  } finally {
    signing.value = false
  }
}

async function loadFeed(clear = false) {
  if (loadingFeed.value) return
  if (!clear && !feedMore.value) return
  loadingFeed.value = true
  try {
    const res = await blogApi.ofFollow(
      clear ? new Date().getTime() + 1 : (feedParams.value.minTime || new Date().getTime() + 1),
      clear ? 0 : feedParams.value.offset
    )
    if (!res) {
      feedMore.value = false
      return
    }
    const list = (res.list || []).map((b) => ({ ...b, img: (b.images || '').split(',')[0] }))
    feed.value = clear ? list : feed.value.concat(list)
    feedParams.value = { minTime: res.minTime, offset: res.offset }
    feedMore.value = list.length > 0
  } catch {
    feedMore.value = false
  } finally {
    loadingFeed.value = false
  }
}

function onTabChange(name) {
  tab.value = name
  if (name === 'follow' && feed.value.length === 0) loadFeed(true)
}

function logout() {
  userStore.logout()
}

function toBlog(b) {
  router.push(`/blog/${b.id}`)
}
</script>

<template>
  <div class="me page">
    <AppHeader title="个人主页">
      <template #actions>
        <button class="me__logout" @click="logout">
          <AppIcon name="logout" :size="18" />
        </button>
      </template>
    </AppHeader>

    <template v-if="userStore.profile">
      <!-- 资料卡 -->
      <section class="me__card ticket">
        <img class="me__avatar" :src="userStore.profile.icon || '/imgs/icons/default-icon.png'" alt="" />
        <div class="me__who">
          <b>{{ userStore.profile.nickName }}</b>
          <small><AppIcon name="pin" :size="12" /> {{ info && info.city ? info.city : '杭州' }}</small>
        </div>
        <button class="btn-ghost me__edit" @click="router.push('/me/edit')">编辑资料</button>
      </section>

      <!-- 我的订单入口 -->
      <section class="me__orders-entry ticket" @click="router.push('/me/orders')">
        <span>我的订单</span>
        <span class="me__orders-arrow"><AppIcon name="chevron" :size="14" /></span>
      </section>

      <!-- 签到条 -->
      <section class="me__sign">
        <div>
          <span class="num">{{ signCount }}</span>
          <small>连续签到天数</small>
        </div>
        <button
          :disabled="signing || signToday"
          :class="{ 'is-done': signToday }"
          @click="doSign"
        >{{ signing ? '签到中…' : signToday ? '今日已签到' : '去签到' }}</button>
      </section>

      <!-- 统计 -->
      <section class="me__stats">
        <div><b class="num">{{ blogs.length }}</b><small>笔记</small></div>
        <div><b class="num">{{ info ? info.fans : 0 }}</b><small>粉丝</small></div>
        <div><b class="num">{{ info ? info.followee : 0 }}</b><small>关注</small></div>
      </section>

      <!-- 页签 -->
      <nav class="me__tabs">
        <button :class="{ 'is-active': tab === 'notes' }" @click="onTabChange('notes')">笔记</button>
        <button :class="{ 'is-active': tab === 'comments' }" @click="onTabChange('comments')">评价</button>
        <button :class="{ 'is-active': tab === 'fans' }" @click="onTabChange('fans')">粉丝</button>
        <button :class="{ 'is-active': tab === 'follow' }" @click="onTabChange('follow')">关注</button>
      </nav>

      <!-- 笔记 -->
      <div v-if="tab === 'notes'">
        <div v-if="blogs.length" class="me__notes">
          <button v-for="b in blogs" :key="b.id" class="me__note" @click="toBlog(b)">
            <img :src="(b.images || '').split(',')[0]" alt="" loading="lazy" />
            <span class="ellipsis">{{ b.title }}</span>
          </button>
        </div>
        <EmptyState v-else icon="heart" text="还没有发布过笔记" action="去发布" @action="router.push('/blog/edit')" />
      </div>

      <!-- 评价 -->
      <EmptyState v-else-if="tab === 'comments'" icon="chat" text="还没有写过评价" />

      <!-- 粉丝 -->
      <EmptyState v-else-if="tab === 'fans'" icon="user" text="还没有粉丝" />

      <!-- 关注动态 -->
      <div v-else>
        <div v-if="feed.length" class="me__feed">
          <article v-for="b in feed" :key="b.id" class="me__feed-card ticket" @click="toBlog(b)">
            <img class="me__feed-img" :src="b.img" alt="" loading="lazy" />
            <div class="me__feed-info">
              <h4>{{ b.title }}</h4>
              <span>{{ b.name }}</span>
            </div>
          </article>
        </div>
        <EmptyState v-else icon="refresh" text="关注的人还没有更新" action="刷新" @action="loadFeed(true)" />
        <p v-if="loadingFeed" class="list-end">加载中…</p>
        <p v-else-if="!feedMore && feed.length" class="list-end">— 到底啦 —</p>
      </div>
    </template>

    <AppTabbar />
  </div>
</template>

<style scoped>
.me__logout {
  width: 34px;
  height: 34px;
  margin-left: auto;
  border: none;
  background: none;
  color: var(--ink);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.me__card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: var(--gap-md);
  padding: var(--gap-md);
}
.me__avatar { width: 56px; height: 56px; border-radius: 50%; }
.me__who { flex: 1; min-width: 0; line-height: 1.4; }
.me__who b { font-size: var(--text-lg); }
.me__who small { display: flex; align-items: center; gap: 2px; color: var(--ink-3); font-size: var(--text-xs); }
.me__edit { font-size: var(--text-xs); }

.me__orders-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--gap-md);
  padding: 14px var(--gap-md);
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  font-weight: 700;
  cursor: pointer;
}
.me__orders-arrow { display: inline-flex; color: var(--ink-3); }

.me__sign {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 var(--gap-md) var(--gap-md);
  padding: 10px 14px;
  background: var(--vermilion-soft);
  border: 1px solid var(--vermilion);
  border-radius: var(--radius-md);
}
.me__sign > div { display: flex; align-items: baseline; gap: 6px; color: var(--vermilion); }
.me__sign .num { font-size: var(--num-lg); }
.me__sign small { font-size: var(--text-xs); }
.me__sign button {
  border: none;
  background: var(--vermilion);
  color: #fff;
  border-radius: var(--radius-pill);
  padding: 7px 16px;
  font-size: var(--text-sm);
  cursor: pointer;
}
.me__sign button:disabled { opacity: 0.6; cursor: not-allowed; }
.me__sign button.is-done { background: var(--line); color: var(--ink-3); }

.me__stats {
  display: flex;
  margin: 0 var(--gap-md) var(--gap-md);
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 14px 0;
}
.me__stats > div { flex: 1; text-align: center; line-height: 1.4; }
.me__stats b { font-size: var(--text-lg); }
.me__stats small { display: block; color: var(--ink-3); font-size: var(--text-xs); }

.me__tabs {
  display: flex;
  background: var(--card);
  border-block: 1px solid var(--line);
}
.me__tabs button {
  flex: 1;
  padding: 12px 0;
  border: none;
  background: none;
  color: var(--ink-2);
  font-size: var(--text-md);
  cursor: pointer;
  position: relative;
}
.me__tabs button.is-active { color: var(--vermilion); font-weight: 700; }
.me__tabs button.is-active::after {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 24px;
  height: 3px;
  border-radius: 2px;
  background: var(--vermilion);
}

.me__notes {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
  padding: var(--gap-md);
}
.me__note {
  border: none;
  padding: 0;
  background: none;
  cursor: pointer;
  overflow: hidden;
  border-radius: var(--radius-sm);
}
.me__note img { width: 100%; aspect-ratio: 1 / 1; object-fit: cover; background: var(--line); }
.me__note span {
  display: block;
  font-size: var(--text-xs);
  color: var(--ink-2);
  padding: 6px 4px;
  background: var(--card);
}

.me__feed { padding: var(--gap-md); display: flex; flex-direction: column; gap: var(--gap-md); }
.me__feed-card { display: flex; gap: 10px; padding: 8px; cursor: pointer; }
.me__feed-img { width: 72px; height: 72px; flex: none; border-radius: var(--radius-sm); object-fit: cover; background: var(--line); }
.me__feed-info { min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 4px; }
.me__feed-info h4 {
  margin: 0;
  font-size: var(--text-sm);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.me__feed-info span { color: var(--ink-3); font-size: var(--text-xs); }
</style>
