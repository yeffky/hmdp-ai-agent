import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { resolve } from 'node:path'

// 开发代理：把 /api 等路径转发到 Spring Boot(8081)
const backend = 'http://localhost:8081'

// 本地静态图片目录（分类图标、上传的图片都在 nginx 的 hmdp/imgs 下）
const IMGS_ROOT = resolve(__dirname, '../nginx-1.18.0/html/hmdp/imgs')

const MIME = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
}

// 开发期直接由 Vite 托管 /imgs 静态图片，避免依赖 nginx 启动
function serveImgs() {
  return {
    name: 'serve-imgs',
    configureServer(server) {
      server.middlewares.use('/imgs', (req, res, next) => {
        const rel = decodeURIComponent(req.url || '')
        const file = resolve(IMGS_ROOT, '.' + rel)
        if (!file.startsWith(IMGS_ROOT) || !existsSync(file) || !statSync(file).isFile()) {
          res.statusCode = 404
          res.end('Not Found')
          return
        }
        res.setHeader('Content-Type', MIME[ext(file).toLowerCase()] || 'application/octet-stream')
        createReadStream(file).pipe(res)
      })
    }
  }
}
function ext(p) {
  const i = p.lastIndexOf('.')
  return i < 0 ? '' : p.slice(i)
}

export default defineConfig({
  plugins: [vue(), serveImgs()],
  build: {
    // 构建产物直接落到 nginx 静态目录，nginx root html/hmdp-app 即可服务
    outDir: '../nginx-1.18.0/html/hmdp-app',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backend,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      '/chat': { target: backend, changeOrigin: true },
      '/kb': { target: backend, changeOrigin: true },
      '/queue-ticket': { target: backend, changeOrigin: true },
      '/upload': { target: backend, changeOrigin: true }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    css: false
  }
})
