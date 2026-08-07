import axios from 'axios'
import { ElMessage } from 'element-plus'
import { refreshAccessToken, forceLogin } from '../utils/auth'

// 统一请求实例：baseURL /api（nginx 剥掉前缀转发到 Spring Boot）
const http = axios.create({
  baseURL: '/api',
  timeout: 60000
})

http.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('token')
    if (token) config.headers['authorization'] = token
    return config
  },
  (error) => Promise.reject(error)
)

// 401 时用 refreshToken 换发新 accessToken 并重试原请求（双 token 续约）。
// 续约本身由 utils/auth 单飞去重，避免并发 401 各自续约导致的轮换吊销竞态。
async function refreshAndRetry(config) {
  if (config._retried) {
    // 续约后重试仍 401 → 会话确实失效
    forceLogin()
    return Promise.reject('请先登录')
  }
  config._retried = true
  const ok = await refreshAccessToken()
  if (!ok) {
    forceLogin()
    return Promise.reject('请先登录')
  }
  return http(config)
}

// 后端约定：业务失败返回 HTTP 200 + {success:false}，未登录返回 HTTP 401
http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.success === false) {
      return Promise.reject(body.errorMsg || '请求失败')
    }
    return body
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      return refreshAndRetry(err.config || {})
    }
    const msg = (err.response && err.response.data && err.response.data.errorMsg) || '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(msg)
  }
)

export default http
