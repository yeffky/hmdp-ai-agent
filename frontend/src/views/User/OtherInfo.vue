<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '../../components/AppHeader.vue'
import AppTabbar from '../../components/AppTabbar.vue'
import EmptyState from '../../components/EmptyState.vue'
import AppIcon from '../../components/AppIcon.vue'
import { userApi, blogApi, followApi } from '../../api'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const profile = ref(null)
const info = ref(null)
const blogs = ref([])
const followed = ref(false)
const common = ref([])
const tab = ref('notes')
const isSelf = ref(false)

async function loadUser(id) {
  profile.value = null
  info.value = null
  blogs.value = []
  common.value = []
  followed.value = false
  isSelf.value = userStore.isLoggedIn && userStore.profile && userStore.profile.id === Number(id)
  try {
    profile.value = await userApi.user(id)
    info.value = await userApi.info(id)
    if (!isSelf.value && userStore.isLoggedIn) {
      followed.value = !!(await followApi.isFollowing(id))
    }
  } catch { /* 加载失败 */ }
  try {
    blogs.value = (await blogApi.ofUser(id, 1)) || []
  } catch {
    blogs.value = []
  }
}

onMounted(() => loadUser(route.params.id))
watch(() => route.params.id, (id) => loadUser(id))

async function onTab(name) {
  tab.value = name
  if (name === 'common' && common.value.length === 0) {
    try {
      common.value = (await followApi.common(route.params.id)) || []
    } catch {
      common.value = []
    }
  }
}

async function toggleFollow() {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  try {
    await followApi.toggle(profile.value.id, !followed.value)
    followed.value = !followed.value
    ElMessage.success(followed.value ? '已关注' : '已取消关注')
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '操作失败')
  }
}
</script>

<template>
  <div class="other-info page">
    <AppHeader title="" />

    <template v-if="profile">
      <section class="oi__card ticket">
        <img class="oi__avatar" :src="profile.icon || '/imgs/icons/default-icon.png'" alt="" />
        <div class="oi__who">
          <b>{{ profile.nickName }}</b>
          <small><AppIcon name="pin" :size="12" /> {{ info && info.city ? info.city : '杭州' }}</small>
          <small v-if="info && info.introduce">{{ info.introduce }}</small>
        </div>
        <button
          v-if="!isSelf && userStore.isLoggedIn"
          class="oi__follow"
          :class="{ 'is-followed': followed }"
          @click="toggleFollow"
        >{{ followed ? '已关注' : '+ 关注' }}</button>
      </section>

      <nav class="oi__tabs">
        <button :class="{ 'is-active': tab === 'notes' }" @click="onTab('notes')">笔记</button>
        <button :class="{ 'is-active': tab === 'common' }" @click="onTab('common')">共同关注</button>
      </nav>

      <div v-if="tab === 'notes'">
        <div v-if="blogs.length" class="oi__notes">
          <button v-for="b in blogs" :key="b.id" class="oi__note" @click="router.push(`/blog/${b.id}`)">
            <img :src="(b.images || '').split(',')[0]" alt="" loading="lazy" />
            <span class="ellipsis">{{ b.title }}</span>
          </button>
        </div>
        <EmptyState v-else icon="heart" text="TA 还没有发布过笔记" />
      </div>

      <div v-else>
        <div v-if="common.length" class="oi__common">
          <div v-for="u in common" :key="u.id" class="oi__common-item ticket" @click="router.push(`/user/${u.id}`)">
            <img :src="u.icon || '/imgs/icons/default-icon.png'" alt="" />
            <span>{{ u.nickName }}</span>
            <AppIcon name="chevron" :size="16" />
          </div>
        </div>
        <EmptyState v-else icon="user" text="你们还没有共同关注的人" />
      </div>
    </template>

    <AppTabbar />
  </div>
</template>

<style scoped>
.oi__card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: var(--gap-md);
  padding: var(--gap-md);
}
.oi__avatar { width: 56px; height: 56px; border-radius: 50%; }
.oi__who { flex: 1; min-width: 0; line-height: 1.5; }
.oi__who b { font-size: var(--text-lg); }
.oi__who small { display: flex; align-items: center; gap: 2px; color: var(--ink-3); font-size: var(--text-xs); }
.oi__follow {
  flex: none;
  border: 1px solid var(--vermilion);
  color: var(--vermilion);
  background: none;
  border-radius: var(--radius-pill);
  padding: 7px 16px;
  font-size: var(--text-sm);
  cursor: pointer;
}
.oi__follow.is-followed { border-color: var(--line); color: var(--ink-3); }

.oi__tabs { display: flex; background: var(--card); border-block: 1px solid var(--line); }
.oi__tabs button {
  flex: 1;
  padding: 12px 0;
  border: none;
  background: none;
  color: var(--ink-2);
  font-size: var(--text-md);
  cursor: pointer;
  position: relative;
}
.oi__tabs button.is-active { color: var(--vermilion); font-weight: 700; }
.oi__tabs button.is-active::after {
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

.oi__notes {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
  padding: var(--gap-md);
}
.oi__note {
  border: none;
  padding: 0;
  background: none;
  cursor: pointer;
  overflow: hidden;
  border-radius: var(--radius-sm);
}
.oi__note img { width: 100%; aspect-ratio: 1 / 1; object-fit: cover; background: var(--line); }
.oi__note span { display: block; font-size: var(--text-xs); color: var(--ink-2); padding: 6px 4px; background: var(--card); }

.oi__common { padding: var(--gap-md); display: flex; flex-direction: column; gap: var(--gap-sm); }
.oi__common-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
}
.oi__common-item img { width: 36px; height: 36px; border-radius: 50%; }
.oi__common-item span { flex: 1; font-size: var(--text-sm); }
.oi__common-item .app-icon { color: var(--ink-3); }
</style>
