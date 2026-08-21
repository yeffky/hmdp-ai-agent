import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 仅使用 ElMessage / ElMessageBox（按需 JS 摇树），保留其样式
import 'element-plus/dist/index.css'
// GitHub 风格 Markdown 排版（聊天气泡的 md 渲染，全局 .markdown-body）
import 'github-markdown-css/github-markdown.css'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/base.css'
import './styles/components.css'

import { restoreSession } from './utils/auth'

async function bootstrap() {
  // refreshToken 在 HttpOnly Cookie 中，浏览器重开后需先换回 accessToken，
  // 否则受保护页面会在任何请求发出前被组件误判为未登录。
  await restoreSession()
  createApp(App).use(createPinia()).use(router).mount('#app')
}

bootstrap()
