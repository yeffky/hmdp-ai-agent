<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import EmptyState from '../../components/EmptyState.vue'
import { followApi } from '../../api'

const route = useRoute()
const router = useRouter()

const isFans = computed(() => route.meta.type === 'fans')
const users = ref([])
const loaded = ref(false)

onMounted(async () => {
  try {
    users.value = (isFans.value ? await followApi.fans() : await followApi.my()) || []
  } catch {
    users.value = []
  }
  loaded.value = true
})
</script>

<template>
  <div class="follow-list page no-tabbar">
    <AppHeader :title="isFans ? '我的粉丝' : '我的关注'" />

    <div v-if="users.length" class="fl__list">
      <div v-for="u in users" :key="u.id" class="fl__item ticket" @click="router.push(`/user/${u.id}`)">
        <img class="fl__avatar" :src="u.icon || '/imgs/icons/default-icon.png'" alt="" />
        <span class="fl__name">{{ u.nickName }}</span>
        <AppIcon name="chevron" :size="16" />
      </div>
    </div>

    <EmptyState
      v-else-if="loaded"
      :icon="isFans ? 'user' : 'heart'"
      :text="isFans ? '还没有粉丝' : '还没有关注任何人'"
    />
  </div>
</template>

<style scoped>
.fl__list {
  padding: var(--gap-md);
  display: flex;
  flex-direction: column;
  gap: var(--gap-sm);
}
.fl__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
}
.fl__avatar { width: 40px; height: 40px; border-radius: 50%; background: var(--line); }
.fl__name { flex: 1; font-size: var(--text-md); }
.fl__item .app-icon { color: var(--ink-3); }
</style>
