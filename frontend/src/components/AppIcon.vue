<script setup>
// 极简线性图标集（24×24，描边继承 currentColor）
const paths = {
  home: '<path d="M3 10.5 12 3l9 7.5V21a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1v-10.5Z"/>',
  map: '<path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Z"/><path d="M9 4v14M15 6v14"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  chat: '<path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5c-1.5 0-2.9-.4-4.1-1L3 20l1-5.4a8.5 8.5 0 1 1 17-3.1Z"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  heart: '<path d="M12 21s-7-4.6-9.3-9A5.4 5.4 0 0 1 12 6.6 5.4 5.4 0 0 1 21.3 12C19 16.4 12 21 12 21Z"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
  pin: '<path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11Z"/><circle cx="12" cy="10" r="2.5"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  send: '<path d="M3 11.5 21 3l-8.5 18-2.5-8-8.5-1.5Z"/><path d="M21 3 10 12.5"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1"/>',
  ticket: '<path d="M4 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v1.5a2 2 0 0 0 0 4V13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-1.5a2 2 0 0 0 0-4V8Z"/><path d="M13 6v12M16 9.5v.01M16 14v.01"/>',
  refresh: '<path d="M20 11A8 8 0 0 0 5.6 6.6L4 8.2M4 4v4.2h4.2M4 13a8 8 0 0 0 14.4 4.4L20 15.8M20 20v-4.2h-4.2"/>',
  logout: '<path d="M14 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8"/><path d="M10 12h11m0 0-3.5-3.5M21 12l-3.5 3.5"/>',
  // 服务标识（店铺详情）：停车 / 儿童友好 / 宠物友好 / 容纳人数
  park: '<rect x="4.5" y="4.5" width="15" height="15" rx="3"/><path d="M9.5 16V8h3a2.5 2.5 0 0 1 0 5h-3"/>',
  child: '<circle cx="12" cy="9" r="4"/><path d="M12 5V3.5"/><path d="M8.2 6.8 7 5.6M15.8 6.8 17 5.6"/><path d="M4 20c1.6-2.8 4.5-4 8-4s6.4 1.2 8 4"/>',
  pet: '<circle cx="6.5" cy="8" r="1.4"/><circle cx="11" cy="5.8" r="1.4"/><circle cx="15.5" cy="8" r="1.4"/><path d="M12 10.5c-3.3 0-5 2.8-5 4.3a5 5 0 0 0 10 0c0-1.5-1.7-4.3-5-4.3Z"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 19c1.2-2.8 3.6-4 6.5-4s5.3 1.2 6.5 4"/><circle cx="17" cy="10" r="2.5"/><path d="M17.5 15.2c1.7.3 3 1.2 4 2.8"/>'
}
const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 20 }
})
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="paths[name] || ''"
  />
</template>

<style scoped>
.app-icon {
  display: inline-block;
  flex: none;
}
</style>
