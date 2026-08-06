<script setup>
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { useChatStore } from '../stores/chat'

const route = useRoute()
const router = useRouter()
const chat = useChatStore()

const tabs = [
  { key: 'home', label: '首页', icon: 'home' },
  { key: 'map', label: '地图', icon: 'map' },
  { key: 'add', label: '发布', icon: 'plus', center: true },
  { key: 'chat', label: '消息', icon: 'chat' },
  { key: 'me', label: '我的', icon: 'user' }
]

const activeKey = {
  1: 'home',
  2: 'map',
  4: 'me'
}[route.meta.tabbar] || ''

function onTap(tab) {
  if (tab.key === 'home') return router.push('/')
  if (tab.key === 'map') return router.push('/map')
  if (tab.key === 'add') return router.push('/blog/edit')
  if (tab.key === 'chat') return chat.open()
  if (tab.key === 'me') return router.push('/me')
}
</script>

<template>
  <nav class="app-tabbar" aria-label="底部导航">
    <template v-for="tab in tabs" :key="tab.key">
      <button
        v-if="!tab.center"
        class="app-tabbar__item"
        :class="{ 'is-active': activeKey === tab.key }"
        @click="onTap(tab)"
      >
        <AppIcon class="ic" :name="tab.icon" :size="22" />
        <span>{{ tab.label }}</span>
      </button>
      <div v-else class="app-tabbar__add-wrap">
        <button class="app-tabbar__add" aria-label="发布笔记" @click="onTap(tab)">
          <AppIcon name="plus" :size="24" />
        </button>
      </div>
    </template>
  </nav>
</template>

<style scoped>
.app-tabbar__add-wrap {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}
</style>
