<script setup>
import { ref, nextTick } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import { chatApi } from '../../api'

const ragMessages = ref([])
const ragInput = ref('')
const ragLoading = ref(false)
const ragMsgs = ref(null)

const agentMessages = ref([])
const agentInput = ref('')
const agentLoading = ref(false)
const agentMsgs = ref(null)

const suggestions = ['怎么退款？', '帮我取个号', '平台有哪些优惠券规则？']

function scrollTo(ref) {
  nextTick(() => {
    const el = ref.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function sendRag() {
  const q = ragInput.value.trim()
  if (!q || ragLoading.value) return
  ragMessages.value.push({ role: 'user', content: q })
  ragInput.value = ''
  ragLoading.value = true
  scrollTo(ragMsgs)
  try {
    const answer = (await chatApi.rag(`rag_demo_${Date.now()}`, q)) || '无回答'
    ragMessages.value.push({ role: 'assistant', content: answer })
  } catch (e) {
    ragMessages.value.push({ role: 'assistant', content: typeof e === 'string' ? e : '请求失败' })
  } finally {
    ragLoading.value = false
    scrollTo(ragMsgs)
  }
}

async function sendAgent() {
  const q = agentInput.value.trim()
  if (!q || agentLoading.value) return
  agentMessages.value.push({ role: 'user', content: q })
  agentInput.value = ''
  agentLoading.value = true
  scrollTo(agentMsgs)
  try {
    const answer = (await chatApi.react(`agent_demo_${Date.now()}`, q)) || '无回答'
    agentMessages.value.push({ role: 'assistant', content: answer })
  } catch (e) {
    agentMessages.value.push({ role: 'assistant', content: typeof e === 'string' ? e : '请求失败' })
  } finally {
    agentLoading.value = false
    scrollTo(agentMsgs)
  }
}
</script>

<template>
  <div class="rag-demo page no-tabbar">
    <AppHeader title="AI 模式对比" />

    <header class="demo__head">
      <h1><span class="stamp">AI</span> 生活优选智能客服</h1>
      <p>两种模式，一边看 AI 怎么答：RAG 检索增强 与 六节点 ReAct Agent</p>
    </header>

    <div class="demo__tips">
      <button v-for="s in suggestions" :key="s" class="chip" @click="ragInput = s; agentInput = s">{{ s }}</button>
    </div>

    <div class="demo__grid">
      <!-- RAG 模式 -->
      <section class="demo__panel">
        <h3><span class="stamp stamp--jade">检</span> RAG 客服模式</h3>
        <p class="demo__desc">检索知识库后直接回答，适合标准问答</p>
        <div ref="ragMsgs" class="demo__chat">
          <div v-for="(m, i) in ragMessages" :key="i" class="demo__bubble" :class="m.role">
            <small>{{ m.role === 'user' ? '你' : 'AI 客服' }}</small>
            <p>{{ m.content }}</p>
          </div>
          <div v-if="ragLoading" class="demo__bubble assistant">
            <small>AI 客服</small>
            <p class="typing-dots"><span /><span /><span /></p>
          </div>
        </div>
        <div class="demo__input">
          <input v-model="ragInput" placeholder="问问平台相关问题…" :disabled="ragLoading" @keyup.enter="sendRag" />
          <button :disabled="ragLoading" @click="sendRag"><AppIcon name="send" :size="16" /></button>
        </div>
      </section>

      <!-- Agent 模式 -->
      <section class="demo__panel">
        <h3><span class="stamp">智</span> ReAct Agent 模式</h3>
        <p class="demo__desc">六节点状态机 + 工具调用，可执行取号/查单</p>
        <div ref="agentMsgs" class="demo__chat">
          <div v-for="(m, i) in agentMessages" :key="i" class="demo__bubble" :class="m.role">
            <small>{{ m.role === 'user' ? '你' : 'AI 客服' }}</small>
            <p>{{ m.content }}</p>
          </div>
          <div v-if="agentLoading" class="demo__bubble assistant">
            <small>AI 客服</small>
            <p class="typing-dots"><span /><span /><span /></p>
          </div>
        </div>
        <div class="demo__input">
          <input v-model="agentInput" placeholder="试试「帮我取个号」…" :disabled="agentLoading" @keyup.enter="sendAgent" />
          <button :disabled="agentLoading" @click="sendAgent"><AppIcon name="send" :size="16" /></button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.demo__head { text-align: center; padding: 20px 16px 8px; }
.demo__head h1 { margin: 0; font-size: var(--text-xl); display: flex; align-items: center; justify-content: center; gap: 8px; }
.demo__head p { color: var(--ink-3); font-size: var(--text-sm); margin: 8px 0 0; }
.demo__tips { display: flex; justify-content: center; gap: 8px; flex-wrap: wrap; padding: 12px 16px; }
.demo__tips .chip { font-size: var(--text-xs); }

.demo__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--gap-md);
  max-width: 900px;
  margin: 0 auto;
  padding: var(--gap-md);
}
@media (min-width: 720px) {
  .demo__grid { grid-template-columns: 1fr 1fr; }
}
.demo__panel {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: var(--gap-lg);
  display: flex;
  flex-direction: column;
}
.demo__panel h3 { margin: 0 0 4px; font-size: var(--text-md); display: flex; align-items: center; gap: 8px; }
.demo__desc { margin: 0 0 10px; color: var(--ink-3); font-size: var(--text-xs); }
.demo__chat {
  flex: 1;
  min-height: 300px;
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 0;
}
.demo__bubble {
  max-width: 85%;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  line-height: 1.55;
}
.demo__bubble small { display: block; font-size: 10px; opacity: 0.6; margin-bottom: 2px; }
.demo__bubble p { margin: 0; white-space: pre-wrap; }
.demo__bubble.user { background: var(--vermilion); color: #fff; align-self: flex-end; border-bottom-right-radius: 2px; }
.demo__bubble.assistant { background: var(--paper); border: 1px solid var(--line); align-self: flex-start; border-bottom-left-radius: 2px; }
.demo__input { display: flex; gap: 8px; margin-top: 10px; }
.demo__input input {
  flex: 1;
  min-width: 0;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--paper);
  font-size: var(--text-sm);
  outline: none;
}
.demo__input input:focus { border-color: var(--vermilion); }
.demo__input button {
  width: 36px;
  height: 36px;
  flex: none;
  border: none;
  border-radius: 50%;
  background: var(--vermilion);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.demo__input button:disabled { background: var(--line); color: var(--ink-3); }
</style>
