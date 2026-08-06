import { describe, it, expect, beforeEach } from 'vitest'
import http from '../api/http'

function mockAdapter(body, status = 200) {
  http.defaults.adapter = () =>
    Promise.resolve({
      data: body,
      status,
      statusText: 'OK',
      headers: {},
      config: {}
    })
}

describe('http 拦截器', () => {
  beforeEach(() => sessionStorage.clear())

  it('success:true 时返回 Result 包装体', async () => {
    mockAdapter({ success: true, data: { id: 1 } })
    const res = await http.get('/x')
    expect(res.data).toEqual({ id: 1 })
  })

  it('success:false 时以 errorMsg 字符串拒绝', async () => {
    mockAdapter({ success: false, errorMsg: '库存不足' })
    await expect(http.get('/x')).rejects.toBe('库存不足')
  })

  it('缺失 success 字段（如裸数组接口）直接放行', async () => {
    mockAdapter([{ id: 1 }, { id: 2 }])
    const res = await http.get('/x')
    expect(res).toHaveLength(2)
  })

  it('请求携带 authorization 请求头', async () => {
    sessionStorage.setItem('token', 'abc123')
    let captured
    http.defaults.adapter = (config) => {
      captured = config
      return Promise.resolve({ data: { success: true }, status: 200, statusText: 'OK', headers: {}, config })
    }
    await http.get('/x')
    expect(captured.headers.authorization).toBe('abc123')
  })
})
