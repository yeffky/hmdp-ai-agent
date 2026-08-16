<script setup>
import { ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import AppIcon from './AppIcon.vue'
import { useChatStore } from '../stores/chat'
import { useUserStore } from '../stores/user'

const chat = useChatStore()
const user = useUserStore()
const router = useRouter()

// LLM 回答以 markdown 输出（后端已剥离 [[id]] 卡片占位符），渲染为富文本。
// 排版由全局 github-markdown-css 的 .markdown-body 提供（专业间距/列表/标题），
// DOMPurify 清洗防 XSS（LLM 输出不可信）。
marked.setOptions({ gfm: true, breaks: false })

function renderMd(text) {
  if (!text) return ''
  const normalized = text.replace(/\n{3,}/g, '\n\n') // 压缩多余连续换行（LLM 输出空行堆积）
  return DOMPurify.sanitize(marked.parse(normalized))
}

const msgBox = ref(null)
const scrolledToBottom = ref(true)
// 挂起确认/选择的自由输入框（对齐 Claude Code：不只按钮，可自由输入，交给 LLM 理解）
const actionInput = ref('')
function sendActionInput() {
  const v = actionInput.value.trim()
  if (!v) return
  chat.send(v)
  actionInput.value = ''
}

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

// 店铺卡片评分显示：score 存 INT（5分×10）
function scoreOf(c) {
  if (c.score == null) return '—'
  return (c.score / 10).toFixed(1)
}
// 距离格式化：米 → m / km
function fmtDistance(d) {
  if (d == null) return ''
  return d >= 1000 ? (d / 1000).toFixed(1) + 'km' : d + 'm'
}

// 调用链折叠：展开/收起状态 + 摘要统计
const openChain = ref(null)
function toggleChain(id) {
  openChain.value = openChain.value === id ? null : id
}
function toolCount(m) {
  return (m.steps || []).filter((s) => s.kind === 'tool').length
}
// 卡片全局序号：累计该消息前面所有 cards block 的卡片数（每个 [[CARD]] 独立成 block 时序号连续）
function cardGlobalIndex(m, blockIndex, localIndex) {
  let n = 0
  const blocks = m.blocks || []
  for (let i = 0; i < blockIndex; i++) {
    const b = blocks[i]
    if (b.type === 'cards' && Array.isArray(b.cards)) n += b.cards.length
  }
  return n + localIndex + 1
}
// 折叠态缩略图：横向排开所有工具结果的店铺图片（最多 8 张）
function thumbs(m) {
  const out = []
  for (const s of m.steps || []) {
    for (const c of s.cards || []) {
      if (c.image) out.push(c.image)
      if (out.length >= 8) return out
    }
  }
  return out
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
            <!-- 调用链：默认折叠展示思考/工具调用过程；折叠态显示卡片缩略图，点击右侧箭头展开 -->
            <div v-if="m.role === 'toolchain'" class="ai-chat__row">
              <span class="ai-chat__avatar sm">优</span>
              <div class="ai-chat__toolchain" :class="{ 'is-open': openChain === m.id }">
                <button class="ai-chat__toolchain-head" @click="toggleChain(m.id)">
                  <span class="ai-chat__toolchain-title">🔧 调用过程</span>
                  <span class="ai-chat__toolchain-count">{{ toolCount(m) }} 次工具</span>
                  <span v-if="thumbs(m).length" class="ai-chat__toolchain-thumbs">
                    <img
                      v-for="(t, i) in thumbs(m)"
                      :key="i"
                      :src="t"
                      class="ai-chat__toolchain-thumb"
                      alt=""
                      loading="lazy"
                    />
                  </span>
                  <span class="ai-chat__toolchain-arrow">{{ openChain === m.id ? '▾' : '▸' }}</span>
                </button>
                <div v-if="openChain === m.id" class="ai-chat__toolchain-body">
                  <div v-for="(s, i) in m.steps" :key="i" class="ai-chat__toolchain-step">
                    <span class="ai-chat__toolchain-ic">{{ s.kind === 'tool' ? '🔧' : '💭' }}</span>
                    <span class="ai-chat__toolchain-text">
                      {{ s.kind === 'tool' ? (s.toolName || '工具') + ' · ' : '' }}{{ s.content }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <!-- 操作按钮：写操作确认/选择，点击即发送；可能带确认卡片 -->
            <div v-else-if="m.role === 'actions'" class="ai-chat__actions">
              <div v-if="m.card" class="ai-chat__confirm-card">
                <div v-if="m.card.kind === 'queue'" class="ai-chat__confirm-body">
                  <div class="ai-chat__confirm-title">确认取号</div>
                  <div class="ai-chat__confirm-shop">🏪 {{ m.card.shopName }}</div>
                  <div class="ai-chat__confirm-meta">用餐人数：{{ m.card.peopleCount }} 人</div>
                </div>
                <div v-else-if="m.card.kind === 'queue-cancel'" class="ai-chat__confirm-body">
                  <div class="ai-chat__confirm-title">确认取消排队</div>
                  <div v-if="m.card.shopName" class="ai-chat__confirm-shop">🏪 {{ m.card.shopName }}</div>
                  <div v-if="m.card.queueNumber" class="ai-chat__confirm-meta">排队号：{{ m.card.queueNumber }}</div>
                  <div v-if="m.card.peopleCount" class="ai-chat__confirm-meta">用餐人数：{{ m.card.peopleCount }} 人</div>
                  <div v-if="m.card.status" class="ai-chat__confirm-meta">当前状态：{{ m.card.status }}</div>
                  <div v-if="m.card.noQueue" class="ai-chat__confirm-meta">您当前没有排队中的记录</div>
                </div>
                <div v-else class="ai-chat__confirm-body">
                  <div v-if="m.hint" class="ai-chat__confirm-text">{{ m.hint }}</div>
                </div>
              </div>
              <button
                v-for="a in m.actions"
                :key="a.value"
                class="ai-chat__action"
                @click="chat.send(a.value)"
              >{{ a.label }}</button>
              <div class="ai-chat__action-input-wrap">
                <input
                  v-model="actionInput"
                  class="ai-chat__action-input"
                  placeholder="输入其他内容…（可选）"
                  @keyup.enter="sendActionInput"
                />
              </div>
            </div>
            <!-- 用户 -->
            <div v-else-if="m.role === 'user'" class="ai-chat__row user">
              <div class="ai-chat__bubble user">{{ m.content }}</div>
            </div>
            <!-- 助手（含流式中）：卡片渲染在气泡内部 -->
            <div v-else class="ai-chat__row">
              <span class="ai-chat__avatar sm">优</span>
              <div
                class="ai-chat__bubble"
                :class="{ 'has-cards': m.cards && m.cards.length, 'stream-cursor': m.role === 'assistant_streaming', 'is-error': m.error }"
              >
                <!-- assistant：blocks 分段（文本 + 卡片穿插，卡片在对应商家位置） -->
                <template v-if="(m.blocks || []).length">
                  <template v-for="(b, i) in m.blocks" :key="i">
                    <span v-if="b.type === 'text'" class="ai-chat__bubble-text markdown-body" v-html="renderMd(b.value)"></span>
                    <div v-else-if="b.type === 'cards'" class="ai-chat__bubble-cards">
                      <div v-for="(c, j) in b.cards" :key="c.id || j" class="ai-chat__card">
                        <div class="ai-chat__card-reason">
                          <span class="ai-chat__card-idx">{{ cardGlobalIndex(m, i, j) }}</span>
                          <span>★ {{ c.reason || '推荐' }}</span>
                        </div>
                        <div class="ai-chat__card-inner" @click="router.push('/shop/' + c.id)">
                          <img v-if="c.image" :src="c.image" alt="" loading="lazy" />
                          <div v-else class="ai-chat__card-ph">🏪</div>
                          <div class="ai-chat__card-body">
                            <div class="ai-chat__card-name ellipsis">{{ c.name }}</div>
                            <div class="ai-chat__card-meta">
                              <span class="num">{{ scoreOf(c) }}</span>
                              <span v-if="c.avgPrice">¥{{ c.avgPrice }}/人</span>
                              <span v-if="c.foodCategory" class="ai-chat__card-cat">{{ c.foodCategory }}</span>
                              <span v-if="c.comments">{{ c.comments }}条</span>
                            </div>
                            <div v-if="c.signatureDishes" class="ai-chat__card-dishes ellipsis">🍽 {{ c.signatureDishes }}</div>
                            <div v-if="c.reviewSummary" class="ai-chat__card-review ellipsis">💬 {{ c.reviewSummary }}</div>
                            <div v-if="c.distance != null || c.petFriendly || c.childFriendly || c.hasParking" class="ai-chat__card-tags">
                              <span v-if="c.distance != null" class="ai-chat__card-tag">📍 {{ fmtDistance(c.distance) }}</span>
                              <span v-if="c.petFriendly" class="ai-chat__card-tag">🐾 宠物友好</span>
                              <span v-if="c.childFriendly" class="ai-chat__card-tag">👶 亲子</span>
                              <span v-if="c.hasParking" class="ai-chat__card-tag">🅿️ 停车</span>
                            </div>
                            <div class="ai-chat__card-addr ellipsis">{{ c.address || c.area }}</div>
                            <div v-if="c.openHours" class="ai-chat__card-hours ellipsis">🕒 {{ c.openHours }}</div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </template>
                </template>
                <!-- 兼容旧结构（content + cards） -->
                <template v-else>
                  <span v-if="m.content" class="ai-chat__bubble-text markdown-body" v-html="renderMd(m.content)"></span>
                  <div v-if="m.cards && m.cards.length" class="ai-chat__bubble-cards">
                    <div v-for="(c, j) in m.cards" :key="c.id || j" class="ai-chat__card">
                      <div class="ai-chat__card-reason">
                        <span class="ai-chat__card-idx">{{ j + 1 }}</span>
                        <span>★ {{ c.reason || '推荐' }}</span>
                      </div>
                      <div class="ai-chat__card-inner" @click="router.push('/shop/' + c.id)">
                        <img v-if="c.image" :src="c.image" alt="" loading="lazy" />
                        <div v-else class="ai-chat__card-ph">🏪</div>
                        <div class="ai-chat__card-body">
                          <div class="ai-chat__card-name ellipsis">{{ c.name }}</div>
                          <div class="ai-chat__card-meta">
                            <span class="num">{{ scoreOf(c) }}</span>
                            <span v-if="c.avgPrice">¥{{ c.avgPrice }}/人</span>
                            <span v-if="c.foodCategory" class="ai-chat__card-cat">{{ c.foodCategory }}</span>
                            <span v-if="c.comments">{{ c.comments }}条</span>
                          </div>
                          <div v-if="c.signatureDishes" class="ai-chat__card-dishes ellipsis">🍽 {{ c.signatureDishes }}</div>
                          <div v-if="c.reviewSummary" class="ai-chat__card-review ellipsis">💬 {{ c.reviewSummary }}</div>
                          <div v-if="c.distance != null || c.petFriendly || c.childFriendly || c.hasParking" class="ai-chat__card-tags">
                            <span v-if="c.distance != null" class="ai-chat__card-tag">📍 {{ fmtDistance(c.distance) }}</span>
                            <span v-if="c.petFriendly" class="ai-chat__card-tag">🐾 宠物友好</span>
                            <span v-if="c.childFriendly" class="ai-chat__card-tag">👶 亲子</span>
                            <span v-if="c.hasParking" class="ai-chat__card-tag">🅿️ 停车</span>
                          </div>
                          <div class="ai-chat__card-addr ellipsis">{{ c.address || c.area }}</div>
                          <div v-if="c.openHours" class="ai-chat__card-hours ellipsis">🕒 {{ c.openHours }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
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
.ai-chat__bubble.is-error {
  color: var(--danger, #d33);
  background: rgba(221, 51, 51, 0.06);
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

/* ---- 店铺卡片（渲染在回答气泡内，逐组穿插：理由标题 + 卡片主体） ---- */
.ai-chat__bubble.has-cards { max-width: 92%; }
.ai-chat__bubble-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 6px; /* 卡片在结论文本下方 */
}
.ai-chat__bubble-text { word-break: break-word; }
/* markdown-body 容器在聊天气泡内的紧凑适配（排版元素由全局 github-markdown-css 提供，对 v-html 生效） */
.ai-chat__bubble-text.markdown-body {
  font-size: 14px;
  padding: 0;
  color: inherit;
  background: transparent;
}
.ai-chat__bubble-text.markdown-body > :first-child { margin-top: 0; }
.ai-chat__bubble-text.markdown-body > :last-child { margin-bottom: 0; }
.ai-chat__card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
}
.ai-chat__card--inline { margin: 6px 0; }
.ai-chat__card-reason {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--vermilion);
}
.ai-chat__card-idx {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex: none;
  border-radius: 50%;
  background: var(--vermilion);
  color: #fff;
  font-size: 10px;
}
.ai-chat__card-inner {
  display: flex;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.1s ease;
}
.ai-chat__card-inner:active { transform: scale(0.98); }
.ai-chat__card-inner img {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: var(--radius-sm);
  object-fit: cover;
  background: var(--line);
}
.ai-chat__card-ph {
  width: 56px;
  height: 56px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: var(--line);
  border-radius: var(--radius-sm);
}
.ai-chat__card-body { flex: 1; min-width: 0; }
.ai-chat__card-name { font-size: var(--text-sm); font-weight: 700; }
.ai-chat__card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
  font-size: var(--text-xs);
  color: var(--ink-2);
}
.ai-chat__card-meta .num { color: var(--amber); font-weight: 800; }
.ai-chat__card-cat {
  color: var(--vermilion);
  background: var(--vermilion-soft);
  padding: 0 5px;
  border-radius: var(--radius-pill);
}
.ai-chat__card-dishes,
.ai-chat__card-review {
  margin-top: 3px;
  font-size: var(--text-xs);
  color: var(--ink-2);
}
.ai-chat__card-review { color: var(--ink-3); }
.ai-chat__card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 3px;
}
.ai-chat__card-tag {
  font-size: 10px;
  color: var(--ink-2);
  background: var(--paper);
  border: 1px solid var(--line);
  padding: 0 5px;
  border-radius: var(--radius-pill);
}
.ai-chat__card-addr {
  margin-top: 3px;
  font-size: var(--text-xs);
  color: var(--ink-3);
}
.ai-chat__card-hours {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--ink-3);
}

/* ---- 调用链（折叠：思考/工具过程，折叠态显示卡片缩略图） ---- */
.ai-chat__toolchain {
  display: flex;
  flex-direction: column;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.ai-chat__toolchain-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 10px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: var(--text-xs);
  color: var(--ink-2);
  text-align: left;
}
.ai-chat__toolchain-title { font-weight: 700; color: var(--ink); white-space: nowrap; }
.ai-chat__toolchain-count { color: var(--ink-3); white-space: nowrap; }
.ai-chat__toolchain-thumbs {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  flex: 1;
  min-width: 0;
}
.ai-chat__toolchain-thumb {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: var(--radius-sm);
  object-fit: cover;
  background: var(--line);
}
.ai-chat__toolchain-arrow { color: var(--ink-3); flex: none; }
.ai-chat__toolchain-body {
  border-top: 1px dashed var(--line);
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 160px;
  overflow-y: auto;
}
.ai-chat__toolchain-step {
  display: flex;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--ink-2);
}
.ai-chat__toolchain-ic { flex: none; }
.ai-chat__toolchain-text { word-break: break-word; }

/* ---- 操作按钮 ---- */
.ai-chat__actions {
  display: flex;
  gap: 8px;
  align-self: flex-start;
  flex-wrap: wrap;
}
.ai-chat__action {
  padding: 8px 22px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-base);
  font-weight: 700;
  cursor: pointer;
}
.ai-chat__action:active { background: var(--vermilion-deep); }
.ai-chat__action:last-child {
  background: var(--card);
  color: var(--ink-2);
  border: 1px solid var(--line);
}
.ai-chat__action-input-wrap { margin-top: 6px; }
.ai-chat__action-input {
  width: 100%;
  box-sizing: border-box;
  padding: 7px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--card);
  color: var(--ink);
  font-size: var(--text-sm, 13px);
}

/* ---- 写操作确认卡片（取号：店名+人数） ---- */
.ai-chat__confirm-card {
  width: 100%;
  background: var(--card);
  border: 1px solid var(--line);
  border-left: 3px solid var(--vermilion);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  margin-bottom: 2px;
}
.ai-chat__confirm-title { font-size: var(--text-xs); color: var(--ink-3); margin-bottom: 4px; }
.ai-chat__confirm-shop { font-size: var(--text-base); font-weight: 700; }
.ai-chat__confirm-meta { font-size: var(--text-sm); color: var(--ink-2); margin-top: 2px; }
/* 无确认卡片时的 hint（选项选择的推荐语/取消排队等）：完整文字块 */
.ai-chat__confirm-text {
  white-space: pre-wrap;
  font-size: var(--text-sm, 13px);
  color: var(--ink-2);
  line-height: 1.6;
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

<style>
/* 聊天气泡内 markdown 紧凑排版：覆盖 github-markdown-css 的文章级大间距，块高度贴近文字高度、尽量无间隔 */
.ai-chat__bubble-text.markdown-body { line-height: 1.35; }
.ai-chat__bubble-text.markdown-body :is(p, ul, ol, li, blockquote, pre, h1, h2, h3, h4, h5, h6, hr) {
  margin-top: 0 !important;
  margin-bottom: 0 !important;
}
.ai-chat__bubble-text.markdown-body ul,
.ai-chat__bubble-text.markdown-body ol { padding-left: 1.2em; }
.ai-chat__bubble-text.markdown-body li > ul,
.ai-chat__bubble-text.markdown-body li > ol { margin-top: 0 !important; }
.ai-chat__bubble-text.markdown-body hr {
  border: none;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}
.ai-chat__bubble-text.markdown-body > :last-child { margin-bottom: 0 !important; }
</style>
