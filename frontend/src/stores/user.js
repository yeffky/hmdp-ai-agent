import { defineStore } from 'pinia'
import { userApi } from '../api'
import router from '../router'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: sessionStorage.getItem('token') || '',
    profile: (() => {
      try {
        return JSON.parse(sessionStorage.getItem('userProfile') || 'null')
      } catch {
        return null
      }
    })(),
    info: null
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    nickName: (s) => (s.profile && s.profile.nickName) || '未登录'
  },
  actions: {
    setToken(t) {
      this.token = t || ''
      if (t) sessionStorage.setItem('token', t)
      else sessionStorage.removeItem('token')
    },
    setProfile(p) {
      this.profile = p || null
      if (p) sessionStorage.setItem('userProfile', JSON.stringify(p))
      else sessionStorage.removeItem('userProfile')
    },
    async login(form) {
      const res = await userApi.login(form)
      this.setToken(res.accessToken)
      return res
    },
    async logout() {
      try {
        await userApi.logout()
      } catch {
        /* 忽略登出接口异常，本地仍清理 */
      }
      this.setToken('')
      this.setProfile(null)
      this.info = null
      router.push('/')
    },
    async loadMe(force = false) {
      if (this.profile && !force) return this.profile
      const p = await userApi.me()
      this.setProfile(p)
      return p
    },
    async loadInfo(id, force = false) {
      if (this.info && this.info.userId === Number(id) && !force) return this.info
      const i = await userApi.info(id)
      this.info = i
      return i
    }
  }
})
