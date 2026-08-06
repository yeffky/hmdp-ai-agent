<script setup>
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'

defineProps({
  title: { type: String, default: '' }
})
const router = useRouter()
</script>

<template>
  <header class="app-header">
    <button class="app-header__back" aria-label="返回" @click="router.back()">
      <AppIcon name="back" :size="20" />
    </button>
    <div class="app-header__title">{{ title }}</div>
    <slot name="actions" />
  </header>
</template>
