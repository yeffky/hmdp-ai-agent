<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  icon: { type: String, default: 'ticket' },
  text: { type: String, default: '这里还空着' },
  action: { type: String, default: '' }
})
const emit = defineEmits(['action'])
</script>

<template>
  <div class="empty">
    <div class="empty__ic">
      <AppIcon :name="icon" :size="30" />
    </div>
    <p>{{ text }}</p>
    <button v-if="action" class="btn-ghost" @click="emit('action')">{{ action }}</button>
  </div>
</template>
