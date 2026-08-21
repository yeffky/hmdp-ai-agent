import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 统一 refresh 续约 + 强制登出。
// refreshToken 存于服务端下发的 HttpOnly Cookie（JS 读不到，防 XSS 窃取长效凭证），
// 续约只需带 cookie 请求 /user/refresh，前端只存/发 accessToken。
// 后端 refresh 会轮换吊销旧 refreshToken，若并发 401 各自续约会竞态误登出，
// 这里用单飞 promise 去重：多个 401 只发一次 refresh，其余等待同一结果后重试。

let refreshPromise = null
let failing = false
let failTimer = null

/** 用 cookie 里的 refreshToken 换发新 accessToken 并写回 sessionStorage。并发共享同一在途请求。 */
export async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        // cookie 自动携带 refreshToken；withCredentials 确保同源请求也带上
        const res = await axios.post('/api/user/refresh', null, { withCredentials: true })
        const data = res.data && res.data.data
        if (data && data.accessToken) {
          sessionStorage.setItem('token', data.accessToken)
          return true
        }
        return false
      } catch {
        return false
      }
    })().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

/**
 * 应用启动时恢复登录态：浏览器重开后 sessionStorage 中没有 accessToken，
 * 但 HttpOnly refreshToken Cookie 仍可能有效，此时需要主动续约一次。
 */
export async function restoreSession() {
  if (sessionStorage.getItem('token')) return true
  return refreshAccessToken()
}

/**
 * 强制登出：清 accessToken + 一次性提示/跳转。
 * 注意：HttpOnly refreshToken cookie 无法从 JS 清除，靠后端 /user/logout 或会话失效自然失效。
 */
export function forceLogin() {
  sessionStorage.removeItem('token')
  if (failing) return
  failing = true
  ElMessage.error('请先登录')
  router.push('/login')
  clearTimeout(failTimer)
  failTimer = setTimeout(() => {
    failing = false
  }, 1000)
}
