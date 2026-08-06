import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

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
      ElMessage.error('请先登录')
      router.push('/login')
      return Promise.reject('请先登录')
    }
    const msg = (err.response && err.response.data && err.response.data.errorMsg) || '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(msg)
  }
)

export default http
