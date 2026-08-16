import { defineStore } from 'pinia'
import streamChat from '../utils/sse'
import { chatApi } from '../api'
import router from '../router'
import { refreshAccessToken, forceLogin } from '../utils/auth'
import { pushUserMessage, pushAssistantMessage, reduceSSEEvent } from './chatMachine'
import { useLocationStore } from './location'

// 当前登录用户标识（切账号隔离用）：从 sessionStorage 解析，避免引入 user store 造成循环依赖
function currentUserId() {
  try {
    const p = JSON.parse(sessionStorage.getItem('userProfile') || 'null')
    return p && p.id != null ? String(p.id) : null
  } catch {
    return null
  }
}

// 历史回答重建为 blocks：优先用后端持久化的 blocks（text/card 顺序 → 卡片插到对应位置），
// 无 blocks（旧数据）回退为 [text, cards]（卡片堆末尾）
function buildHistoryBlocks(r) {
  const byId = {}
  for (const c of r.cards || []) byId[String(c.id)] = c
  if (Array.isArray(r.blocks) && r.blocks.length) {
    const out = []
    for (const b of r.blocks) {
      if (b.type === 'text' && b.text) out.push({ type: 'text', value: b.text })
      else if (b.type === 'card') {
        const card = byId[String(b.id)]
        if (card) out.push({ type: 'cards', cards: [card] })
      }
    }
    if (!out.length && r.assistantMessage) out.push({ type: 'text', value: r.assistantMessage })
    return out
  }
  return [
    ...(r.assistantMessage ? [{ type: 'text', value: r.assistantMessage }] : []),
    ...(Array.isArray(r.cards) && r.cards.length ? [{ type: 'cards', cards: r.cards }] : [])
  ]
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    show: false,
    messages: [],
    input: '',
    sending: false,
    loadingHistory: false,
    hasMore: true,
    oldestId: null,
    pendingTail: 0, // 当前发送轮次的起始下标，用于滚动定位
    loadedForUser: null // 已加载历史的用户 id，用于切账号时清空重建
  }),
  getters: {
    isLoggedIn: () => !!sessionStorage.getItem('token')
  },
  actions: {
    toggle() {
      this.show ? this.close() : this.open()
    },
    open() {
      if (!this.isLoggedIn) {
        router.push('/login')
        return
      }
      // 切账号隔离：内存里若是上一个用户的对话，先清空再按当前用户加载
      const uid = currentUserId()
      if (uid && this.loadedForUser !== uid) {
        this.loadedForUser = uid
        this.messages = []
        this.oldestId = null
        this.hasMore = true
        this.loadHistory()
      } else if (this.messages.length === 0) {
        this.oldestId = null
        this.hasMore = true
        this.loadHistory()
      }
      this.show = true
    },
    close() {
      this.show = false
    },
    async loadHistory() {
      if (this.loadingHistory || !this.hasMore) return
      this.loadingHistory = true
      try {
        const params = { limit: 10 }
        if (this.oldestId !== null) params.beforeId = this.oldestId
        const payload = await chatApi.history(params)
        const rounds = payload.rounds || []
        const hasMore = payload.hasMore !== undefined ? payload.hasMore : rounds.length >= 10
        if (rounds.length > 0) {
          const serverRounds = rounds.slice() // 服务端降序（新→旧）
          const newMessages = []
          for (let i = serverRounds.length - 1; i >= 0; i--) {
            const r = serverRounds[i]
            newMessages.push({ id: `h${r.id}u`, role: 'user', content: r.userMessage })
            // 历史重建：优先用后端 blocks（text/card 顺序 → 卡片对应位置），无 blocks 回退 [text, cards]
            newMessages.push({
              id: `h${r.id}a`,
              role: 'assistant',
              blocks: buildHistoryBlocks(r)
            })
          }
          this.oldestId = serverRounds[serverRounds.length - 1].id
          this.messages = [...newMessages, ...this.messages]
        }
        this.hasMore = hasMore
      } catch (e) {
        if (e === '请先登录') {
          router.push('/login')
        }
      } finally {
        this.loadingHistory = false
      }
    },
    async send(text) {
      const msg = (text || '').trim()
      if (!msg || this.sending) return
      this.messages = pushUserMessage(this.messages, msg)
      this.input = ''
      this.sending = true
      this.pendingTail = this.messages.length
      try {
        for (let attempt = 0; attempt < 2; attempt++) {
          try {
            const loc = useLocationStore()
            await streamChat({
              message: msg,
              token: sessionStorage.getItem('token'),
              centerX: loc.centerX,
              centerY: loc.centerY,
              districtId: loc.districtId,
              onEvent: (evt) => {
                this.messages = reduceSSEEvent(this.messages, evt)
              }
            })
            return
          } catch (e) {
            if (!e || e.code !== 401) {
              this.messages = pushAssistantMessage(this.messages, '网络异常，请稍后再试')
              return
            }
            // 401（accessToken 过期）：先用 refreshToken 续约后重试一次，失败才登出
            if (attempt === 0 && (await refreshAccessToken())) continue
            forceLogin()
            return
          }
        }
      } finally {
        this.sending = false
      }
    }
  }
})
