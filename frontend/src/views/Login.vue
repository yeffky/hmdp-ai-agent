<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const phone = ref('')
const code = ref('')
const agreed = ref(false)
const sending = ref(false)
const countdown = ref(0)
const logging = ref(false)

let timer = null

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendCode() {
  if (!/^1\d{10}$/.test(phone.value)) {
    ElMessage.error('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    await userApi.sendCode(phone.value)
    ElMessage.success('验证码已发送（查看后端日志）')
    startCountdown()
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '发送失败')
  } finally {
    sending.value = false
  }
}

async function login() {
  if (!agreed.value) {
    ElMessage.error('请先阅读并同意用户协议')
    return
  }
  if (!/^1\d{10}$/.test(phone.value) || !code.value) {
    ElMessage.error('请填写手机号和验证码')
    return
  }
  logging.value = true
  try {
    await userStore.login({ phone: phone.value, code: code.value })
    ElMessage.success('登录成功')
    router.replace('/')
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '登录失败')
  } finally {
    logging.value = false
  }
}
</script>

<template>
  <div class="login page no-tabbar">
    <header class="login__head">
      <button class="app-header__back" aria-label="返回" @click="router.back()">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>
      </button>
      <span>手机号快捷登录</span>
    </header>

    <div class="login__hero">
      <div class="login__ticket num">号</div>
      <h1>生活优选</h1>
      <p>未注册的手机号验证后自动创建账户</p>
    </div>

    <div class="login__card ticket">
      <label class="login__field">
        <span>手机号</span>
        <input v-model="phone" type="tel" maxlength="11" placeholder="请输入手机号" />
        <button class="login__code" :disabled="sending || countdown > 0" @click="sendCode">
          {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
        </button>
      </label>
      <div class="tear" />
      <label class="login__field">
        <span>验证码</span>
        <input v-model="code" type="text" maxlength="6" placeholder="请输入验证码" @keyup.enter="login" />
      </label>

      <button class="login__submit" :disabled="logging" @click="login">
        {{ logging ? '登录中…' : '登录' }}
      </button>

      <label class="login__agree">
        <input v-model="agreed" type="checkbox" />
        <span>我已阅读并同意《生活优选用户服务协议》与《隐私政策》</span>
      </label>
    </div>
  </div>
</template>

<style scoped>
.login { padding: 0 var(--gap-lg); background: var(--paper); }
.login__head {
  display: flex;
  align-items: center;
  gap: 4px;
  height: var(--header-h);
  font-weight: 700;
}
.login__hero { text-align: center; padding: 40px 0 28px; }
.login__ticket {
  width: 64px;
  height: 64px;
  margin: 0 auto 12px;
  border-radius: var(--radius-lg);
  background: var(--vermilion);
  color: #fff;
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-pop);
}
.login__hero h1 { margin: 0; font-size: var(--text-xl); letter-spacing: 2px; }
.login__hero p { margin: 6px 0 0; color: var(--ink-3); font-size: var(--text-sm); }

.login__card { padding: var(--gap-lg); }
.login__field {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 4px;
}
.login__field > span { font-size: var(--text-md); font-weight: 700; flex: none; }
.login__field input {
  flex: 1;
  min-width: 0;
  border: none;
  background: none;
  font-size: var(--text-base);
  outline: none;
  padding: 4px 0;
}
.login__code {
  flex: none;
  border: 1px solid var(--vermilion);
  color: var(--vermilion);
  background: none;
  border-radius: var(--radius-pill);
  padding: 6px 12px;
  font-size: var(--text-xs);
  cursor: pointer;
  white-space: nowrap;
}
.login__code:disabled { border-color: var(--line); color: var(--ink-3); }
.login__submit {
  width: 100%;
  height: 44px;
  margin-top: 16px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-md);
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-card);
}
.login__submit:active { background: var(--vermilion-deep); }
.login__submit:disabled { opacity: 0.6; }
.login__agree {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 14px;
  color: var(--ink-3);
  font-size: var(--text-xs);
}
.login__agree input { margin-top: 2px; accent-color: var(--vermilion); }
</style>
