import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'

vi.mock('../router', () => ({ default: { push: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

async function freshAuth() {
  // resetModules 每次拿到全新模块实例，避免模块级单飞状态在用例间串扰
  vi.resetModules()
  return await import('../utils/auth')
}

describe('auth 续约去重（refreshToken 在 HttpOnly cookie）', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('并发续约只发一次 refresh，成功后写回新 accessToken', async () => {
    sessionStorage.setItem('token', 't1')
    const postSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { success: true, data: { accessToken: 'a2' } }
    })

    const { refreshAccessToken } = await freshAuth()
    const results = await Promise.all([
      refreshAccessToken(),
      refreshAccessToken(),
      refreshAccessToken()
    ])

    expect(postSpy).toHaveBeenCalledTimes(1)
    expect(results).toEqual([true, true, true])
    expect(sessionStorage.getItem('token')).toBe('a2')
  })

  it('refresh 请求不带 body，走 withCredentials（refreshToken 由 cookie 携带）', async () => {
    sessionStorage.setItem('token', 't1')
    const postSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { success: true, data: { accessToken: 'a2' } }
    })

    const { refreshAccessToken } = await freshAuth()
    await refreshAccessToken()

    const [url, body, opts] = postSpy.mock.calls[0]
    expect(url).toBe('/api/user/refresh')
    expect(body).toBeNull()
    expect(opts).toMatchObject({ withCredentials: true })
  })

  it('refresh 接口失败返回 false 且不清 token（登出由调用方 forceLogin 决定）', async () => {
    sessionStorage.setItem('token', 't1')
    vi.spyOn(axios, 'post').mockResolvedValue({ data: { success: false, errorMsg: '登录已失效' } })
    const { refreshAccessToken } = await freshAuth()
    expect(await refreshAccessToken()).toBe(false)
    expect(sessionStorage.getItem('token')).toBe('t1')
  })

  it('forceLogin 清 token，并发调用只提示/跳转一次', async () => {
    sessionStorage.setItem('token', 't')
    const { forceLogin } = await freshAuth()
    forceLogin()
    forceLogin()
    expect(sessionStorage.getItem('token')).toBeNull()
    const { ElMessage } = await import('element-plus')
    const router = (await import('../router')).default
    expect(ElMessage.error).toHaveBeenCalledTimes(1)
    expect(router.push).toHaveBeenCalledTimes(1)
  })
})
