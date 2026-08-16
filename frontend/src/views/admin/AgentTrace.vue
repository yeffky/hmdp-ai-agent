<script setup>
import { ref, onMounted } from 'vue'
import { agentTraceApi } from '../../api'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import EmptyState from '../../components/EmptyState.vue'

const list = ref([])
const hasMore = ref(false)
const page = ref(1)
const loading = ref(false)
const detail = ref(null)
const detailVisible = ref(false)
const llmCalls = ref([])
const llmLoading = ref(false)
// 大盘概览
const summary = ref({})
const trends = ref({ requests: [], llm: [] })
const trendRange = ref('24h')
const stages = ref([])

async function loadOverview() {
  try {
    summary.value = (await agentTraceApi.summary()) || {}
    stages.value = (await agentTraceApi.stages()) || []
  } catch {
    /* 概览加载失败不阻塞 */
  }
  await loadTrends()
}

async function loadTrends() {
  try {
    trends.value = (await agentTraceApi.trends(trendRange.value)) || { requests: [], llm: [] }
  } catch {
    trends.value = { requests: [], llm: [] }
  }
}

function maxOf(arr, key) {
  let m = 0
  for (const it of arr || []) m = Math.max(m, Number(it[key]) || 0)
  return m || 1
}

function barWidth(v, max) {
  return max ? Math.max(3, Math.round((v / max) * 100)) : 0
}

function stageColor(s) {
  return ({ agent: '#e6a23c', structured: '#3fb28b', error: '#f56c6c', other: '#9aa5b1' })[s] || '#9aa5b1'
}

// SVG 折线：polyline points + 数据点
function trendPoints(arr, key, w, h) {
  const max = maxOf(arr, key)
  const n = arr.length
  if (!n) return ''
  return arr
    .map((it, i) => {
      const x = n === 1 ? w / 2 : (i / (n - 1)) * w
      const y = h - 6 - (Number(it[key]) / max) * (h - 12)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}
function trendPts(arr, key, w, h) {
  const max = maxOf(arr, key)
  const n = arr.length
  return arr.map((it, i) => {
    const x = n === 1 ? w / 2 : (i / (n - 1)) * w
    const y = h - 6 - (Number(it[key]) / max) * (h - 12)
    return { x: +x.toFixed(1), y: +y.toFixed(1) }
  })
}

// 大数缩略：12345 → 1.2w / 1234 → 1.2k
function fmtK(n) {
  const v = Number(n) || 0
  return v >= 10000 ? (v / 10000).toFixed(1) + 'w' : v >= 1000 ? (v / 1000).toFixed(1) + 'k' : String(v)
}

async function load(clear = true) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await agentTraceApi.list(clear ? 1 : page.value + 1, 20)
    if (!res) return
    const items = (res.list || []).map((t) => ({ ...t, steps: safeParse(t.stepsJson) }))
    list.value = clear ? items : list.value.concat(items)
    hasMore.value = !!res.hasMore
    page.value = clear ? 1 : page.value + 1
  } catch {
    /* 加载失败 */
  } finally {
    loading.value = false
  }
}

function safeParse(json) {
  if (!json) return []
  try {
    return JSON.parse(json)
  } catch {
    return []
  }
}

async function openDetail(traceId) {
  try {
    const t = await agentTraceApi.detail(traceId)
    detail.value = t ? { ...t, steps: safeParse(t.stepsJson) } : null
    detailVisible.value = true
    loadLlm(traceId)
  } catch {
    /* 加载失败 */
  }
}

async function loadLlm(traceId) {
  llmLoading.value = true
  llmCalls.value = []
  try {
    const rows = (await agentTraceApi.llmTrace(traceId)) || []
    llmCalls.value = rows.map((r) => ({ ...r, open: false }))
  } catch {
    llmCalls.value = []
  } finally {
    llmLoading.value = false
  }
}

function stageLabel(s) {
  return ({ agent: '工具', structured: '规划', error: '错误', other: '其它' })[s] || s || '—'
}

function fmtTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n) => (n < 10 ? `0${n}` : `${n}`)
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(() => {
  load(true)
  loadOverview()
})
</script>

<template>
  <div class="trace page no-tabbar">
    <AppHeader title="Agent 轨迹">
      <template #actions>
        <button class="trace__refresh" aria-label="刷新" @click="load(true); loadOverview()"><AppIcon name="refresh" :size="18" /></button>
      </template>
    </AppHeader>

    <!-- L1 概览 -->
    <div class="dash__cards">
      <div class="dash__card" style="--ac: var(--vermilion)">
        <span class="dash__card-ic">🗒️</span>
        <b>{{ summary.reqs ?? 0 }}</b>
        <small>请求数</small>
      </div>
      <div class="dash__card" style="--ac: var(--amber)">
        <span class="dash__card-ic">⏱️</span>
        <b>{{ summary.avgMs ?? 0 }}<i>ms</i></b>
        <small>平均耗时</small>
      </div>
      <div class="dash__card" style="--ac: var(--jade)">
        <span class="dash__card-ic">🎯</span>
        <b>{{ summary.successRate ?? 0 }}<i>%</i></b>
        <small>成功率</small>
      </div>
      <div class="dash__card" style="--ac: #6c8cff">
        <span class="dash__card-ic">🤖</span>
        <b>{{ summary.llmCalls ?? 0 }}</b>
        <small>LLM 调用</small>
      </div>
      <div class="dash__card" style="--ac: #9b6cff">
        <span class="dash__card-ic">🧮</span>
        <b>{{ fmtK(summary.tokens ?? 0) }}</b>
        <small>总 Token</small>
      </div>
    </div>

    <!-- L2 趋势 -->
    <div class="dash__panel">
      <div class="dash__panel-head">
        <b>趋势</b>
        <div class="dash__range">
          <button v-for="r in ['24h', '7d', '30d']" :key="r" :class="{ on: trendRange === r }" @click="trendRange = r; loadTrends()">{{ r }}</button>
        </div>
      </div>
      <div v-if="trends.requests.length" class="dash__section">
        <div class="dash__label"><span class="dash__dot" style="background: var(--vermilion)" />请求量</div>
        <svg class="dash__chart" viewBox="0 0 260 76" preserveAspectRatio="none">
          <polyline :points="trendPoints(trends.requests, 'cnt', 260, 76)" fill="none" stroke="var(--vermilion)" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" />
          <circle v-for="p in trendPts(trends.requests, 'cnt', 260, 76)" :cx="p.x" :cy="p.y" r="2" fill="var(--vermilion)" />
        </svg>
        <div class="dash__chart-labels">
          <span>{{ trends.requests[0].bucket }}</span>
          <span>峰值 {{ maxOf(trends.requests, 'cnt') }}</span>
          <span>{{ trends.requests[trends.requests.length - 1].bucket }}</span>
        </div>
      </div>
      <div v-if="trends.llm.length" class="dash__section">
        <div class="dash__label"><span class="dash__dot" style="background: var(--jade)" />LLM Token</div>
        <svg class="dash__chart" viewBox="0 0 260 76" preserveAspectRatio="none">
          <polyline :points="trendPoints(trends.llm, 'tokens', 260, 76)" fill="none" stroke="var(--jade)" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" />
          <circle v-for="p in trendPts(trends.llm, 'tokens', 260, 76)" :cx="p.x" :cy="p.y" r="2" fill="var(--jade)" />
        </svg>
        <div class="dash__chart-labels">
          <span>{{ trends.llm[0].bucket }}</span>
          <span>峰值 {{ fmtK(maxOf(trends.llm, 'tokens')) }}</span>
          <span>{{ trends.llm[trends.llm.length - 1].bucket }}</span>
        </div>
      </div>
      <div v-if="!trends.requests.length && !trends.llm.length" class="dash__empty">暂无趋势数据</div>
    </div>

    <!-- L3 分布 -->
    <div class="dash__panel">
      <div class="dash__panel-head"><b>LLM 调用分布</b></div>
      <div v-for="s in stages" :key="s.stage" class="dash__bar-row">
        <span class="dash__stage" :style="{ background: stageColor(s.stage) + '1f', color: stageColor(s.stage) }">{{ stageLabel(s.stage) }}</span>
        <div class="dash__track"><i :style="{ width: barWidth(s.calls, maxOf(stages, 'calls')) + '%', background: stageColor(s.stage) }" /></div>
        <span class="dash__val">{{ s.calls }} 次</span>
        <span class="dash__sub">{{ fmtK(s.tokens) }} tok · {{ s.avgMs }}ms</span>
      </div>
      <div v-if="!stages.length" class="dash__empty">暂无 LLM 调用</div>
    </div>

    <div v-if="list.length" class="trace__list">
      <div v-for="t in list" :key="t.id" class="trace__item" @click="openDetail(t.traceId)">
        <div class="trace__top">
          <span class="trace__dot" :class="t.status === 'error' ? 'is-err' : 'is-ok'" />
          <span class="trace__q ellipsis">{{ t.query || '(无问题)' }}</span>
          <span class="trace__badge" :class="t.status === 'error' ? 'is-err' : 'is-ok'">{{ t.status === 'error' ? '异常' : '完成' }}</span>
        </div>
        <div class="trace__meta">
          <span>🕒 {{ fmtTime(t.createTime) }}</span>
          <span>⏱ {{ t.totalMs }}ms</span>
          <span>🔗 节点 {{ t.nodeCount }}</span>
          <span class="trace__tid">#{{ (t.traceId || '').slice(0, 12) }}</span>
        </div>
      </div>
    </div>

    <EmptyState v-else-if="!loading" icon="chart" text="还没有 Agent 轨迹（先去对话试试）" />

    <button v-if="hasMore" class="trace__more" :disabled="loading" @click="load(false)">
      {{ loading ? '加载中…' : '加载更多' }}
    </button>

    <!-- 详情抽屉 -->
    <div v-if="detailVisible" class="trace__mask" @click.self="detailVisible = false">
      <div class="trace__detail">
        <div class="trace__detail-head">
          <span>执行轨迹</span>
          <button aria-label="关闭" @click="detailVisible = false"><AppIcon name="close" :size="16" /></button>
        </div>

        <template v-if="detail">
          <div class="trace__detail-q">
            <b>问题：</b>{{ detail.query }}
          </div>
          <div class="trace__detail-meta">
            <span>状态：<b :class="detail.status === 'error' ? 'is-err' : 'is-ok'">{{ detail.status === 'error' ? '异常' : '完成' }}</b></span>
            <span>总耗时 {{ detail.totalMs }}ms</span>
            <span v-if="detail.errorMsg">错误：{{ detail.errorMsg }}</span>
          </div>

          <div class="trace__timeline">
            <div v-for="(s, i) in detail.steps" :key="i" class="trace__step">
              <span class="trace__step-dot" :class="{ 'is-tool': s.tool }" />
              <div class="trace__step-body">
                <div class="trace__step-head">
                  <div class="trace__step-title">
                    <b>{{ s.node }}</b>
                    <span v-if="s.tool" class="trace__step-tool">{{ s.tool }}</span>
                  </div>
                  <span class="trace__step-at">+{{ s.at }}ms</span>
                </div>
                <div v-if="s.toolArgs" class="trace__step-args">参数: {{ s.toolArgs }}</div>
                <div v-if="s.toolResult" class="trace__step-result">结果: {{ s.toolResult }}</div>
              </div>
            </div>
          </div>

          <!-- LLM 调用级时间线 -->
          <div class="trace__llm">
            <div class="trace__llm-title">LLM 调用 <span v-if="llmLoading">加载中…</span><span v-else>{{ llmCalls.length }}</span></div>
            <div v-if="llmCalls.length" class="trace__llm-list">
              <div v-for="(c, i) in llmCalls" :key="c.id || i" class="trace__llm-item" :class="{ open: c.open }" @click="c.open = !c.open">
                <div class="trace__llm-head">
                  <span class="trace__llm-stage" :class="'is-' + c.stage">{{ stageLabel(c.stage) }}</span>
                  <span class="trace__llm-tokens">in {{ c.inputTokens ?? '-' }} / out {{ c.outputTokens ?? '-' }}</span>
                  <span class="trace__llm-ms">{{ c.durationMs }}ms</span>
                  <span class="trace__llm-arrow">{{ c.open ? '▾' : '▸' }}</span>
                </div>
                <div class="trace__llm-preview">
                  <span class="trace__llm-k">请求</span>
                  <span class="ellipsis">{{ (c.prompt || '').slice(0, 60) }}</span>
                </div>
                <div class="trace__llm-preview">
                  <span class="trace__llm-k trace__llm-k--resp">响应</span>
                  <span class="ellipsis">{{ (c.response || '').slice(0, 60) }}</span>
                </div>
                <template v-if="c.open">
                  <div class="trace__llm-label">请求（{{ (c.prompt || '').length }} 字符）</div>
                  <pre class="trace__llm-body">{{ c.prompt }}</pre>
                  <div class="trace__llm-label trace__llm-label--resp">响应（{{ (c.response || '').length }} 字符）</div>
                  <pre class="trace__llm-body">{{ c.response }}</pre>
                </template>
              </div>
            </div>
            <div v-else-if="!llmLoading" class="trace__llm-empty">无 LLM 调用记录（需后端为 Phase 4 后版本）</div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---- 概览卡片 ---- */
.dash__cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  padding: var(--gap-md) var(--gap-md) 0;
}
.dash__card {
  position: relative;
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 10px 6px 8px;
  text-align: center;
}
.dash__card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--ac);
  opacity: 0.85;
}
.dash__card-ic { display: block; font-size: 15px; }
.dash__card b {
  display: block;
  font-size: 19px;
  font-weight: 800;
  color: var(--ink);
  margin-top: 2px;
  line-height: 1.1;
}
.dash__card i { font-style: normal; font-size: 10px; color: var(--ink-3); font-weight: 600; }
.dash__card small { font-size: 10px; color: var(--ink-3); }

/* ---- 面板 ---- */
.dash__panel {
  margin: var(--gap-md);
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: var(--gap-sm);
  box-shadow: var(--shadow-card);
}
.dash__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--gap-sm);
  font-size: var(--text-sm);
}
.dash__range { display: flex; gap: 4px; }
.dash__range button {
  border: 1px solid var(--line);
  background: none;
  color: var(--ink-3);
  font-size: var(--text-xs);
  border-radius: var(--radius-pill);
  padding: 2px 10px;
  cursor: pointer;
}
.dash__range button.on { background: var(--vermilion); color: #fff; border-color: var(--vermilion); }
.dash__section { margin-top: 10px; }
.dash__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--ink-3);
  margin-bottom: 6px;
  font-weight: 600;
}
.dash__dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }

/* ---- SVG 折线 ---- */
.dash__chart {
  width: 100%;
  height: 76px;
  display: block;
  background:
    repeating-linear-gradient(to bottom, transparent 0, transparent 24px, var(--line) 24px, var(--line) 25px);
}
.dash__chart-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--ink-3);
  margin-top: 4px;
}

/* ---- 分布 ---- */
.dash__bar-row { display: flex; align-items: center; gap: 8px; margin: 6px 0; }
.dash__stage {
  flex: none;
  width: 44px;
  text-align: center;
  font-size: 11px;
  font-weight: 700;
  border-radius: var(--radius-pill);
  padding: 2px 0;
}
.dash__track { flex: 1; height: 12px; background: var(--paper); border-radius: 99px; overflow: hidden; }
.dash__track i { display: block; height: 100%; border-radius: 99px; min-width: 3px; }
.dash__val { flex: none; font-size: var(--text-xs); font-weight: 700; color: var(--ink); width: 44px; text-align: right; }
.dash__sub { flex: none; font-size: 10px; color: var(--ink-3); }
.dash__empty { font-size: var(--text-xs); color: var(--ink-3); padding: 8px 0; }

/* ---- 刷新 ---- */
.trace__refresh {
  width: 34px;
  height: 34px;
  margin-left: auto;
  border: none;
  background: none;
  color: var(--ink);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

/* ---- 轨迹列表 ---- */
.trace__list {
  padding: var(--gap-md);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.trace__item {
  padding: 12px 14px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease;
}
.trace__item:active { transform: scale(0.99); }
.trace__top { display: flex; align-items: center; gap: 8px; }
.trace__dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: var(--ink-3);
}
.trace__dot.is-ok { background: var(--jade); }
.trace__dot.is-err { background: var(--vermilion); }
.trace__q { flex: 1; font-size: var(--text-md); font-weight: 700; }
.trace__badge {
  flex: none;
  font-size: 11px;
  font-weight: 700;
  border-radius: var(--radius-pill);
  padding: 1px 10px;
}
.trace__badge.is-ok { color: var(--jade); background: rgba(63, 178, 139, 0.12); }
.trace__badge.is-err { color: var(--vermilion); background: var(--vermilion-soft); }
.trace__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
  color: var(--ink-3);
  font-size: var(--text-xs);
}
.trace__tid { font-family: var(--font-mono, monospace); }
.trace__more {
  display: block;
  width: calc(100% - 2 * var(--gap-md));
  margin: var(--gap-md) auto;
  padding: 10px 0;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--card);
  color: var(--ink-2);
  font-size: var(--text-sm);
  cursor: pointer;
}

/* ---- 详情抽屉 ---- */
.trace__mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.trace__detail {
  width: 100%;
  max-width: 480px;
  max-height: 85vh;
  overflow-y: auto;
  background: var(--paper);
  border-radius: 18px 18px 0 0;
  padding: var(--gap-md);
}
.trace__detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  margin-bottom: var(--gap-sm);
}
.trace__detail-head button {
  border: none;
  background: none;
  color: var(--ink-3);
  cursor: pointer;
  display: inline-flex;
}
.trace__detail-q { font-size: var(--text-sm); padding-bottom: 8px; border-bottom: 1px solid var(--line); }
.trace__detail-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 0;
  color: var(--ink-3);
  font-size: var(--text-xs);
  border-bottom: 1px solid var(--line);
}
.is-ok { color: var(--jade); }
.is-err { color: var(--vermilion); }
.trace__timeline { padding-top: var(--gap-sm); display: flex; flex-direction: column; gap: 0; }
.trace__step { display: flex; gap: 10px; position: relative; padding-bottom: 14px; }
.trace__step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 12px;
  bottom: 0;
  width: 2px;
  background: var(--line);
}
.trace__step-dot {
  flex: none;
  width: 12px;
  height: 12px;
  margin-top: 3px;
  border-radius: 50%;
  background: var(--ink-3);
}
.trace__step-dot.is-tool { background: var(--vermilion); }
.trace__step-body { flex: 1; min-width: 0; }
.trace__step-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.trace__step-title { display: flex; align-items: center; gap: 6px; font-size: var(--text-sm); }
.trace__step-title b { text-transform: capitalize; }
.trace__step-tool {
  font-size: var(--text-xs);
  color: var(--vermilion);
  background: var(--vermilion-soft);
  padding: 1px 6px;
  border-radius: var(--radius-pill);
}
.trace__step-at { color: var(--ink-3); font-size: var(--text-xs); flex: none; }
.trace__step-args { margin-top: 4px; font-size: var(--text-xs); color: var(--ink-3); word-break: break-all; }
.trace__step-result {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--ink-2);
  word-break: break-all;
  background: var(--card);
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  max-height: 120px;
  overflow-y: auto;
}

/* ---- LLM 调用级时间线 ---- */
.trace__llm { margin-top: var(--gap-md); border-top: 1px solid var(--line); padding-top: var(--gap-sm); }
.trace__llm-title { font-weight: 700; font-size: var(--text-sm); margin-bottom: var(--gap-sm); }
.trace__llm-title span { color: var(--ink-3); font-weight: 400; font-size: var(--text-xs); }
.trace__llm-list { display: flex; flex-direction: column; gap: var(--gap-sm); }
.trace__llm-item {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 8px 12px;
  cursor: pointer;
  transition: border-color 0.1s ease;
}
.trace__llm-item:hover { border-color: var(--ink-3); }
.trace__llm-item.open { border-color: var(--vermilion); }
.trace__llm-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.trace__llm-stage {
  flex: none;
  font-size: var(--text-xs);
  color: var(--vermilion);
  background: var(--vermilion-soft);
  padding: 1px 6px;
  border-radius: var(--radius-pill);
}
.trace__llm-stage.is-structured { color: var(--jade); background: rgba(63, 178, 139, 0.12); }
.trace__llm-stage.is-error { color: var(--vermilion); background: var(--vermilion-soft); }
.trace__llm-tokens, .trace__llm-ms { font-size: var(--text-xs); color: var(--ink-3); }
.trace__llm-ms { margin-left: auto; }
.trace__llm-arrow { color: var(--ink-3); flex: none; }
.trace__llm-preview {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-xs);
  color: var(--ink-2);
  padding: 2px 0;
  min-width: 0;
}
.trace__llm-k { flex: none; color: var(--vermilion); font-weight: 600; }
.trace__llm-k--resp { color: var(--jade); }
.trace__llm-label {
  margin-top: 8px;
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--ink-2);
}
.trace__llm-label--resp { color: var(--jade); }
.trace__llm-body {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--paper);
  border-radius: var(--radius-sm);
  padding: 6px 8px;
  max-height: 340px;
  overflow-y: auto;
}
.trace__llm-empty { font-size: var(--text-xs); color: var(--ink-3); }
</style>
