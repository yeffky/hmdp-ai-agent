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

createApp(App).use(createPinia()).use(router).mount('#app')
