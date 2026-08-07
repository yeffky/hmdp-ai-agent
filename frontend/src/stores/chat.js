import { defineStore } from 'pinia'
import streamChat from '../utils/sse'
import { chatApi } from '../api'
import router from '../router'
import { refreshAccessToken, forceLogin } from '../utils/auth'
import { pushUserMessage, pushAssistantMessage, reduceSSEEvent } from './chatMachine'

export const useChatStore = defineStore('chat', {
  state: () => ({
    show: false,
    messages: [],
    input: '',
    sending: false,
    loadingHistory: false,
    hasMore: true,
    oldestId: null,
    pendingTail: 0 // 当前发送轮次的起始下标，用于滚动定位
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
      this.show = true
      if (this.messages.length === 0) {
        this.oldestId = null
        this.hasMore = true
        this.loadHistory()
      }
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
            newMessages.push({ id: `h${r.id}a`, role: 'assistant', content: r.assistantMessage })
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
            await streamChat({
              message: msg,
              token: sessionStorage.getItem('token'),
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
