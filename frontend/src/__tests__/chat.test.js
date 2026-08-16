import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// store 用默认导入 streamChat，mock 需同时提供 named + default
const { mockStreamChat, mockRefresh, mockForceLogin } = vi.hoisted(() => ({
  mockStreamChat: vi.fn(),
  mockRefresh: vi.fn(),
  mockForceLogin: vi.fn()
}))
vi.mock('../utils/sse', () => ({ streamChat: mockStreamChat, default: mockStreamChat }))
vi.mock('../utils/auth', () => ({
  refreshAccessToken: mockRefresh,
  forceLogin: mockForceLogin
}))
vi.mock('../api', () => ({ chatApi: { history: vi.fn() } }))

import { chatApi } from '../api'
import { useChatStore } from '../stores/chat'

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('open() 未登录时跳转登录、不弹窗', () => {
    const store = useChatStore()
    store.open()
    expect(store.show).toBe(false)
  })

  it('send() 推送用户消息并消费 SSE 事件', async () => {
    sessionStorage.setItem('token', 'tok')
    mockStreamChat.mockImplementation(async ({ onEvent }) => {
      onEvent({ type: 'thinking', content: '分析中' })
      onEvent({ type: 'answer_chunk', content: '你好' })
      onEvent({ type: 'answer', content: '你好' })
    })
    const store = useChatStore()
    await store.send('hi')
    expect(mockStreamChat).toHaveBeenCalledOnce()
    expect(store.sending).toBe(false)
    // 过程消息累积进「调用链」折叠消息，最后是正式回答
    expect(store.messages.map((m) => m.role)).toEqual(['user', 'toolchain', 'assistant'])
    expect(store.messages.find((m) => m.role === 'assistant').blocks[0].value).toBe('你好')
  })

  it('send() 时若已在发送则忽略', async () => {
    sessionStorage.setItem('token', 'tok')
    let release
    mockStreamChat.mockImplementation(() => new Promise((r) => (release = r)))
    const store = useChatStore()
    const p1 = store.send('a')
    const p2 = store.send('b')
    release()
    await Promise.all([p1, p2])
    expect(store.messages.filter((m) => m.role === 'user')).toHaveLength(1)
  })

  it('streamChat 网络错误时追加错误提示', async () => {
    sessionStorage.setItem('token', 'tok')
    mockStreamChat.mockRejectedValue(new Error('boom'))
    const store = useChatStore()
    await store.send('hi')
    expect(store.messages[1]).toMatchObject({ role: 'assistant' })
    expect(store.messages[1].blocks[0].value).toBe('网络异常，请稍后再试')
  })

  it('send() 401 时刷新 token 并用新 token 重试一次', async () => {
    sessionStorage.setItem('token', 'old')
    sessionStorage.setItem('refreshToken', 'rt')
    mockRefresh.mockImplementation(async () => {
      sessionStorage.setItem('token', 'new')
      return true
    })
    mockStreamChat
      .mockRejectedValueOnce({ code: 401 })
      .mockImplementationOnce(async ({ token, onEvent }) => {
        expect(token).toBe('new')
        onEvent({ type: 'answer', content: 'ok' })
      })
    const store = useChatStore()
    await store.send('hi')
    expect(mockRefresh).toHaveBeenCalledOnce()
    expect(sessionStorage.getItem('token')).toBe('new')
    expect(mockStreamChat).toHaveBeenCalledTimes(2)
    expect(store.sending).toBe(false)
    expect(store.messages.map((m) => m.role)).toEqual(['user', 'assistant'])
  })

  it('send() 401 且刷新失败时登出', async () => {
    sessionStorage.setItem('token', 'old')
    sessionStorage.setItem('refreshToken', 'rt')
    mockRefresh.mockResolvedValue(false)
    mockStreamChat.mockRejectedValue({ code: 401 })
    const store = useChatStore()
    await store.send('hi')
    expect(mockForceLogin).toHaveBeenCalledOnce()
    expect(mockStreamChat).toHaveBeenCalledTimes(1)
    expect(store.sending).toBe(false)
  })

  it('loadHistory 翻转服务端降序并按序前置', async () => {
    sessionStorage.setItem('token', 'tok')
    chatApi.history.mockResolvedValue({
      rounds: [
        { id: 3, userMessage: 'u3', assistantMessage: 'a3' },
        { id: 2, userMessage: 'u2', assistantMessage: 'a2' }
      ],
      hasMore: false
    })
    const store = useChatStore()
    await store.loadHistory()
    expect(store.messages.map((m) => m.role)).toEqual(['user', 'assistant', 'user', 'assistant'])
    expect(store.messages.map((m) => (m.role === 'user' ? m.content : m.blocks[0].value))).toEqual(['u2', 'a2', 'u3', 'a3'])
    expect(store.oldestId).toBe(2)
    expect(store.hasMore).toBe(false)
  })

  it('loadHistory 把历史卡片挂到 assistant 消息（卡片持久化重建）', async () => {
    sessionStorage.setItem('token', 'tok')
    const cards = [{ id: 1, name: '海底捞', reason: '评分最高（4.8）', petFriendly: true }]
    chatApi.history.mockResolvedValue({
      rounds: [{ id: 5, userMessage: 'q', assistantMessage: 'a', cards }],
      hasMore: false
    })
    const store = useChatStore()
    await store.loadHistory()
    const a = store.messages.find((m) => m.role === 'assistant')
    expect(a.blocks).toHaveLength(2)
    expect(a.blocks[0].value).toBe('a')
    expect(a.blocks[1].cards).toEqual(cards)
  })

  it('open() 切换账号时清空内存并按新用户重建', async () => {
    sessionStorage.setItem('token', 'tok')
    sessionStorage.setItem('userProfile', JSON.stringify({ id: 1 }))
    chatApi.history.mockResolvedValue({
      rounds: [{ id: 1, userMessage: 'u1', assistantMessage: 'a1' }],
      hasMore: false
    })
    const store = useChatStore()
    store.open()
    await new Promise((r) => setTimeout(r, 0))
    expect(store.messages.map((m) => (m.role === 'user' ? m.content : m.blocks[0].value))).toEqual(['u1', 'a1'])
    expect(store.loadedForUser).toBe('1')

    // 切到账号 2：内存残留被清空，按新用户重新加载
    sessionStorage.setItem('userProfile', JSON.stringify({ id: 2 }))
    chatApi.history.mockResolvedValue({
      rounds: [{ id: 9, userMessage: 'u9', assistantMessage: 'a9' }],
      hasMore: false
    })
    store.open()
    await new Promise((r) => setTimeout(r, 0))
    expect(store.messages.map((m) => (m.role === 'user' ? m.content : m.blocks[0].value))).toEqual(['u9', 'a9'])
    expect(store.loadedForUser).toBe('2')
  })
})
