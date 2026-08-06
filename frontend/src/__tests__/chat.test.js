import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// store 用默认导入 streamChat，mock 需同时提供 named + default
const { mockStreamChat } = vi.hoisted(() => ({ mockStreamChat: vi.fn() }))
vi.mock('../utils/sse', () => ({ streamChat: mockStreamChat, default: mockStreamChat }))
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
    // thinking 过程消息保留在列表（小灰条），最后是正式回答
    expect(store.messages.map((m) => m.role)).toEqual(['user', 'thinking', 'assistant'])
    expect(store.messages.find((m) => m.role === 'assistant').content).toBe('你好')
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
    expect(store.messages[1]).toMatchObject({ role: 'assistant', content: '网络异常，请稍后再试' })
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
    expect(store.messages.map((m) => m.content)).toEqual(['u2', 'a2', 'u3', 'a3'])
    expect(store.oldestId).toBe(2)
    expect(store.hasMore).toBe(false)
  })
})
