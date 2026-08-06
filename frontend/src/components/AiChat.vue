<script setup>
import { ref, watch, nextTick } from 'vue'
import AppIcon from './AppIcon.vue'
import { useChatStore } from '../stores/chat'
import { useUserStore } from '../stores/user'

const chat = useChatStore()
const user = useUserStore()

const msgBox = ref(null)
const scrolledToBottom = ref(true)

// 消息列表变化后滚动（仅当用户本来就在底部附近）
watch(
  () => chat.messages.length + chat.sending,
  async () => {
    if (!chat.show) return
    await nextTick()
    scrollToBottomIfNear()
  }
)

function onScroll() {
  const box = msgBox.value
  if (!box) return
  scrolledToBottom.value = box.scrollHeight - box.scrollTop - box.clientHeight < 80
  if (box.scrollTop <= 30) chat.loadHistory()
}

function scrollToBottomIfNear() {
  const box = msgBox.value
  if (!box || !scrolledToBottom.value) return
  box.scrollTop = box.scrollHeight
}

function send() {
  if (!chat.input.trim() || chat.sending) return
  scrolledToBottom.value = true
  chat.send(chat.input)
}
</script>

<template>
  <teleport to="body">
    <div class="ai-layer">
    <!-- 悬浮入口 -->
    <button
      v-if="!chat.show"
      class="concierge-fab"
      aria-label="找小优帮忙"
      @click="chat.open()"
    >
      <span class="concierge-fab__face">
        <i /><i /><i />
      </span>
      <span class="concierge-fab__dot">号</span>
    </button>

    <!-- 聊天窗 -->
    <Transition name="sheet">
      <section v-if="chat.show" class="ai-chat" aria-label="AI 助手小优">
        <header class="ai-chat__head">
          <span class="ai-chat__avatar">优</span>
          <div class="ai-chat__who">
            <b>小优 · 帮你取号</b>
            <small>找店 · 排队 · 优惠券 · 平台规则</small>
          </div>
          <button class="ai-chat__close" aria-label="关闭" @click="chat.close()">
            <AppIcon name="close" :size="18" />
          </button>
        </header>

        <div ref="msgBox" class="ai-chat__msgs" @scroll.passive="onScroll">
          <p v-if="chat.hasMore" class="ai-chat__hint">上滑加载更早对话</p>
          <p v-else class="ai-chat__hint">— 没有更早的对话了 —</p>

          <template v-for="m in chat.messages" :key="m.id">
            <!-- 过程消息：思考 / 调工具 -->
            <div v-if="m.role === 'thinking' || m.role === 'tooling'" class="ai-chat__proc">
              <span class="ai-chat__proc-ic">
                <AppIcon :name="m.role === 'tooling' ? 'gear' : 'chat'" :size="12" />
              </span>
              <span class="ai-chat__proc-text">{{ m.content }}</span>
            </div>
            <!-- 用户 -->
            <div v-else-if="m.role === 'user'" class="ai-chat__row user">
              <div class="ai-chat__bubble user">{{ m.content }}</div>
            </div>
            <!-- 助手（含流式中） -->
            <div v-else class="ai-chat__row">
              <span class="ai-chat__avatar sm">优</span>
              <div
                class="ai-chat__bubble"
                :class="{ 'stream-cursor': m.role === 'assistant_streaming' }"
              >{{ m.content }}</div>
            </div>
          </template>

          <div v-if="chat.sending" class="ai-chat__row">
            <span class="ai-chat__avatar sm">优</span>
            <div class="ai-chat__bubble typing-dots"><span /><span /><span /></div>
          </div>
        </div>

        <footer class="ai-chat__input">
          <input
            v-model="chat.input"
            placeholder="例如：帮我在老张烧烤取个号"
            :disabled="chat.sending"
            @keyup.enter="send"
          />
          <button :disabled="chat.sending" @click="send">
            <AppIcon name="send" :size="18" />
          </button>
        </footer>
      </section>
    </Transition>
    </div>
  </teleport>
</template>

<style scoped>
/* 固定层：与 .app-shell 同宽居中，内部元素按此列对齐 */
.ai-layer {
  position: fixed;
  inset: 0;
  margin: 0 auto;
  max-width: 480px;
  pointer-events: none;
  z-index: 60;
}
.concierge-fab {
  position: absolute;
  right: 16px;
  bottom: calc(var(--tabbar-h) + 18px);
  width: 54px;
  height: 54px;
  border-radius: 50%;
  border: 3px solid var(--paper);
  background: var(--vermilion);
  box-shadow: var(--shadow-pop);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
}
.concierge-fab:active { background: var(--vermilion-deep); }
.concierge-fab__face {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #fff;
  position: relative;
}
.concierge-fab__face i {
  position: absolute;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--ink);
  top: 9px;
}
.concierge-fab__face i:nth-child(1) { left: 7px; }
.concierge-fab__face i:nth-child(2) { right: 7px; }
.concierge-fab__face i:nth-child(3) {
  left: 8px;
  right: 8px;
  top: 15px;
  width: 10px;
  height: 4px;
  background: none;
  border-bottom: 2px solid var(--ink);
  border-radius: 0 0 6px 6px;
}
.concierge-fab__dot {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--paper);
  border: 2px solid var(--vermilion);
  color: var(--vermilion);
  font-family: var(--font-display);
  font-weight: 900;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 聊天窗：底部抽屉（对齐 .app-shell 列） */
.ai-chat {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 72vh;
  background: var(--paper);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  box-shadow: 0 -8px 40px rgba(70, 52, 30, 0.25);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  pointer-events: auto;
}
.ai-chat__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: var(--vermilion);
  color: #fff;
}
.ai-chat__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #fff;
  color: var(--vermilion);
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex: none;
}
.ai-chat__avatar.sm { width: 28px; height: 28px; font-size: 13px; }
.ai-chat__who { flex: 1; line-height: 1.3; }
.ai-chat__who b { font-size: var(--text-md); }
.ai-chat__who small { display: block; font-size: var(--text-xs); opacity: 0.85; }
.ai-chat__close {
  background: none;
  border: none;
  color: #fff;
  cursor: pointer;
  padding: 4px;
}
.ai-chat__msgs {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-chat__hint {
  text-align: center;
  color: var(--ink-3);
  font-size: var(--text-xs);
  margin: 0;
}
.ai-chat__row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.ai-chat__row.user { justify-content: flex-end; }
.ai-chat__bubble {
  max-width: 78%;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  background: var(--card);
  border: 1px solid var(--line);
  font-size: var(--text-base);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-chat__bubble.user {
  background: var(--vermilion);
  border-color: var(--vermilion);
  color: #fff;
  border-bottom-right-radius: 2px;
}
.ai-chat__row .ai-chat__bubble:not(.user) {
  border-bottom-left-radius: 2px;
}
.ai-chat__proc {
  display: flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  color: var(--ink-3);
  font-size: var(--text-xs);
}
.ai-chat__proc-ic {
  color: var(--amber);
  display: flex;
}
.ai-chat__proc-text {
  max-width: 85%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-chat__input {
  display: flex;
  gap: 8px;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  background: var(--card);
  border-top: 1px solid var(--line);
}
.ai-chat__input input {
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--paper);
  font-size: var(--text-base);
  outline: none;
}
.ai-chat__input input:focus { border-color: var(--vermilion); }
.ai-chat__input button {
  width: 38px;
  height: 38px;
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
.ai-chat__input button:active { background: var(--vermilion-deep); }
.ai-chat__input button:disabled { background: var(--line); color: var(--ink-3); }

.sheet-enter-active,
.sheet-leave-active {
  transition: transform 0.24s ease, opacity 0.2s ease;
}
.sheet-enter-from,
.sheet-leave-to {
  transform: translateY(100%);
  opacity: 0;
}
</style>
