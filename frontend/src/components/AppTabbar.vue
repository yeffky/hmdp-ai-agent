<script setup>
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { key: 'home', label: '首页', icon: 'home' },
  { key: 'map', label: '地图', icon: 'map' },
  { key: 'add', label: '发布', icon: 'plus', center: true },
  { key: 'orders', label: '订单', icon: 'ticket' },
  { key: 'me', label: '我的', icon: 'user' }
]

const activeKey = {
  1: 'home',
  2: 'map',
  3: 'orders',
  4: 'me'
}[route.meta.tabbar] || ''

function onTap(tab) {
  if (tab.key === 'home') return router.push('/')
  if (tab.key === 'map') return router.push('/map')
  if (tab.key === 'add') return router.push('/blog/edit')
  if (tab.key === 'orders') return router.push('/me/orders')
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
