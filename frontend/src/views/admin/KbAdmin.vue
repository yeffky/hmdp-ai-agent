<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import { kbApi, chatApi } from '../../api'

const stats = ref({ totalPoints: 0, collectionName: '' })
const documents = ref([])
const loadingDocs = ref(false)

const ingestForm = ref({ source: 'faq', title: '', content: '' })
const ingesting = ref(false)
const ingestResult = ref(null)
const ingestOk = ref(true)

const splitContent = ref('')
const splitting = ref(false)
const splitResult = ref(null)

const searchQuery = ref('')
const searching = ref(false)
const searched = ref(false)
const searchResults = ref([])

const ragQuery = ref('')
const ragLoading = ref(false)
const ragAnswer = ref('')

const seeding = ref(false)

const sourceOptions = [
  { value: 'faq', label: '帮助文档 (faq)' },
  { value: 'shop', label: '商家信息 (shop)' },
  { value: 'rule', label: '平台规则 (rule)' },
  { value: 'blog', label: '博客笔记 (blog)' },
  { value: 'manual', label: '其他 (manual)' }
]

onMounted(() => {
  loadStats()
  loadDocuments()
})

async function loadStats() {
  try {
    stats.value = (await kbApi.stats()) || stats.value
  } catch { /* 忽略 */ }
}

async function loadDocuments() {
  loadingDocs.value = true
  try {
    documents.value = (await kbApi.documents()) || []
  } catch {
    documents.value = []
  } finally {
    loadingDocs.value = false
  }
}

async function toggleChunks(doc) {
  if (doc._expanded) {
    doc._expanded = false
    return
  }
  doc._expanded = true
  if (doc._chunks) return
  doc._loading = true
  doc._chunks = []
  try {
    doc._chunks = (await kbApi.chunks(doc.source, doc.title)) || []
  } catch {
    doc._chunks = []
  } finally {
    doc._loading = false
  }
}

async function deleteDoc(doc) {
  try {
    await ElMessageBox.confirm(`确认删除「${doc.title}」吗？其所有切片将被移除。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await kbApi.deleteDocument(doc.source, doc.title)
    ElMessage.success(`已删除 ${doc.title}`)
    loadDocuments()
    loadStats()
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '删除失败')
  }
}

async function ingest() {
  if (!ingestForm.value.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  ingesting.value = true
  try {
    const res = await kbApi.ingest(ingestForm.value)
    ingestResult.value = typeof res === 'string' ? res : '摄入成功'
    ingestOk.value = true
    loadStats()
    loadDocuments()
  } catch (e) {
    ingestResult.value = typeof e === 'string' ? e : '摄入失败'
    ingestOk.value = false
  } finally {
    ingesting.value = false
  }
}

async function previewSplit() {
  if (!splitContent.value.trim()) {
    ElMessage.warning('请输入 Markdown 内容')
    return
  }
  splitting.value = true
  splitResult.value = null
  try {
    splitResult.value = (await kbApi.previewSplit(splitContent.value)) || { totalChunks: 0, chunks: [] }
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '切片失败')
  } finally {
    splitting.value = false
  }
}

async function search() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  searched.value = false
  try {
    searchResults.value = (await kbApi.search(searchQuery.value, 5)) || []
  } catch {
    searchResults.value = []
  } finally {
    searched.value = true
    searching.value = false
  }
}

async function ragChat() {
  if (!ragQuery.value.trim()) return
  ragLoading.value = true
  ragAnswer.value = ''
  try {
    ragAnswer.value = (await chatApi.react(`kb_admin_${Date.now()}`, ragQuery.value)) || '无回答'
  } catch (e) {
    ragAnswer.value = typeof e === 'string' ? e : '请求失败'
  } finally {
    ragLoading.value = false
  }
}

const mdExample = `# 生活优选帮助中心

## 注册与登录

注册生活优选账号非常简单。打开生活优选首页，点击右上角的"注册"按钮，输入手机号码并获取验证码，设置密码后即可完成注册。注册即送50元新人礼包。

## 退款政策

未使用的优惠券订单支持随时退款，款项将在3-5个工作日内退回原支付方式。已核销的优惠券不支持退款。`

const seedItems = [
  { source: 'faq', title: '如何注册账号', content: '注册生活优选账号非常简单。打开生活优选首页，点击右上角的"注册"按钮，输入手机号码并获取验证码，设置密码后即可完成注册。注册即送50元新人礼包。' },
  { source: 'faq', title: '优惠券使用规则', content: '优惠券使用规则：1. 每张优惠券仅限在指定商家使用；2. 优惠券不可叠加使用；3. 优惠券有有效期，过期自动失效；4. 优惠券在下单支付时自动抵扣；5. 如发生退款，优惠券金额不予退还。' },
  { source: 'faq', title: '退款政策', content: '退款政策：未使用的优惠券订单支持随时退款，款项将在3-5个工作日内退回原支付方式。已核销的优惠券不支持退款。退款申请方法：进入"我的订单"页面，选择需要退款的订单，点击"申请退款"按钮，填写退款原因后提交即可。' },
  { source: 'rule', title: '平台投诉处理规范', content: '投诉处理流程：1. 用户可通过客服入口提交投诉；2. 客服在1小时内响应投诉；3. 对于商家违规行为，平台将在24小时内核实；4. 核实属实后，平台将对商家采取警告、下架或封禁措施。' },
  { source: 'rule', title: '商家入驻标准', content: '商家入驻生活优选平台的标准：1. 需有合法的营业执照和卫生许可证；2. 店铺评分需保持在3.5分以上；3. 不得有虚假宣传行为；4. 需按照平台要求提供完整菜单和服务信息。' }
]

async function seedData() {
  seeding.value = true
  try {
    for (const item of seedItems) {
      await kbApi.ingest(item)
    }
    ElMessage.success(`成功导入 ${seedItems.length} 条种子数据`)
    loadStats()
    loadDocuments()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    seeding.value = false
  }
}
</script>

<template>
  <div class="kb-admin page no-tabbar">
    <AppHeader title="知识库管理" />

    <div class="admin">
      <!-- 概览 -->
      <section class="admin__stats">
        <div class="admin__stat">
          <b class="num">{{ stats.totalPoints || 0 }}</b>
          <small>向量总数</small>
        </div>
        <div class="admin__stat">
          <b class="num">{{ documents.length }}</b>
          <small>文档数</small>
        </div>
        <div class="admin__stat">
          <b class="num" style="font-size:14px;width:auto">{{ stats.collectionName || '—' }}</b>
          <small>集合</small>
        </div>
      </section>

      <!-- 已存储文档 -->
      <section class="admin__card">
        <div class="admin__card-head">
          <h2>已存储文档</h2>
          <button class="btn-ghost" @click="loadDocuments">
            <AppIcon name="refresh" :size="14" /> 刷新
          </button>
        </div>

        <div v-if="loadingDocs" class="list-end">加载中…</div>
        <div v-else-if="!documents.length" class="empty"><p>知识库为空，先摄取文档吧</p></div>
        <div v-else class="admin__doclist">
          <div v-for="d in documents" :key="`${d.source}|${d.title}`" class="admin__doc ticket">
            <button class="admin__doc-main" @click="toggleChunks(d)">
              <span class="stamp stamp--jade">{{ d.source }}</span>
              <span class="admin__doc-title ellipsis">{{ d.title }}</span>
              <span class="num">{{ d.chunkCount }}</span>
              <small class="admin__doc-hash">{{ d.contentHash }}</small>
              <AppIcon name="chevron" :size="14" :class="{ rotate: d._expanded }" />
            </button>
            <div v-if="d._expanded" class="admin__chunks">
              <div v-if="d._loading" class="list-end">加载切片中…</div>
              <div v-else-if="!d._chunks.length" class="empty"><p>无切片数据</p></div>
              <div v-else v-for="c in d._chunks" :key="c.id" class="admin__chunk">
                <div class="admin__chunk-head">
                  <span>#{{ c.chunkIndex }} <small v-if="c.headingPath">{{ c.headingPath }}</small></span>
                  <small>{{ c.length }} 字</small>
                </div>
                <pre>{{ c.text }}</pre>
              </div>
            </div>
            <button class="admin__doc-del" @click="deleteDoc(d)">删除</button>
          </div>
        </div>
      </section>

      <!-- 文档摄取 -->
      <section class="admin__card">
        <h2>文档摄取</h2>
        <div class="admin__form">
          <label>来源类型
            <select v-model="ingestForm.source">
              <option v-for="o in sourceOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>标题
            <input v-model="ingestForm.title" placeholder="文档标题" />
          </label>
          <label>内容
            <textarea v-model="ingestForm.content" rows="5" placeholder="粘贴文档正文内容…"></textarea>
          </label>
          <div class="admin__form-row">
            <button class="admin__btn primary" :disabled="ingesting" @click="ingest">
              {{ ingesting ? '摄入中…' : '提交摄入' }}
            </button>
            <button class="btn-ghost" @click="ingestForm = { source: 'faq', title: '', content: '' }">重置</button>
          </div>
          <div v-if="ingestResult" class="admin__alert" :class="{ ok: ingestOk }">{{ ingestResult }}</div>
        </div>
      </section>

      <!-- 切片预览 -->
      <section class="admin__card">
        <h2>切片预览</h2>
        <p class="admin__hint">自适应切片器：识别 Markdown 标题 / 中文章节号，保留 headingPath 层级；无结构时递归降级，统一滑动窗口重叠。</p>
        <textarea v-model="splitContent" rows="6" placeholder="粘贴 Markdown 文档…"></textarea>
        <div class="admin__form-row">
          <button class="admin__btn primary" :disabled="splitting" @click="previewSplit">
            {{ splitting ? '切片中…' : '预览切片' }}
          </button>
          <button class="btn-ghost" @click="splitContent = mdExample">加载 Markdown 示例</button>
        </div>
        <div v-if="splitResult" class="admin__split">
          <p>共 <b class="num">{{ splitResult.totalChunks }}</b> 个切片</p>
          <div v-if="!splitResult.chunks.length" class="empty"><p>文档太短，无需切片</p></div>
          <div v-for="c in splitResult.chunks" :key="c.index" class="admin__chunk">
            <div class="admin__chunk-head">
              <span>#{{ c.index }} <small v-if="c.headingPath">{{ c.headingPath }}</small></span>
              <small>{{ c.length }} 字</small>
            </div>
            <pre>{{ c.text }}</pre>
          </div>
        </div>
      </section>

      <!-- 检索测试 -->
      <section class="admin__card">
        <h2>检索测试</h2>
        <div class="admin__form-row">
          <input v-model="searchQuery" class="admin__wide" placeholder="输入测试查询…" @keyup.enter="search" />
          <button class="admin__btn primary" :disabled="searching" @click="search">检索</button>
        </div>
        <div v-for="r in searchResults" :key="r.text" class="admin__result">
          <div class="admin__result-meta">
            {{ r.source }} · {{ r.title }}
            <span class="stamp stamp--amber">{{ (r.score * 100).toFixed(1) }}%</span>
          </div>
          <p>{{ r.text }}</p>
        </div>
        <div v-if="searched && !searchResults.length" class="empty"><p>知识库中未找到相关内容</p></div>
      </section>

      <!-- RAG 问答 -->
      <section class="admin__card">
        <h2>RAG 问答测试</h2>
        <div class="admin__form-row">
          <input v-model="ragQuery" class="admin__wide" placeholder="输入问题测试 RAG…" @keyup.enter="ragChat" />
          <button class="admin__btn jade" :disabled="ragLoading" @click="ragChat">问答</button>
        </div>
        <div v-if="ragAnswer" class="admin__result jade">
          <div class="admin__result-meta">AI 回答</div>
          <p>{{ ragAnswer }}</p>
        </div>
      </section>

      <!-- 种子数据 -->
      <section class="admin__card">
        <h2>种子数据导入</h2>
        <p class="admin__hint">快速导入示例问答，用于测试 RAG 闭环。</p>
        <button class="admin__btn amber" :disabled="seeding" @click="seedData">
          {{ seeding ? '导入中…' : '导入示例问答' }}
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin { max-width: 860px; margin: 0 auto; padding: var(--gap-md); display: flex; flex-direction: column; gap: var(--gap-md); }

.admin__stats { display: flex; gap: var(--gap-md); }
.admin__stat {
  flex: 1;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 14px;
  text-align: center;
  line-height: 1.4;
}
.admin__stat b { display: block; font-size: var(--num-lg); color: var(--vermilion); }
.admin__stat small { color: var(--ink-3); font-size: var(--text-xs); }

.admin__card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: var(--gap-lg);
}
.admin__card h2 {
  margin: 0 0 var(--gap-md);
  font-size: var(--text-md);
  display: inline-block;
  border-bottom: 2px solid var(--vermilion);
  padding-bottom: 4px;
}
.admin__card-head { display: flex; align-items: center; justify-content: space-between; }
.admin__card-head h2 { margin-bottom: var(--gap-md); }

.admin__doclist { display: flex; flex-direction: column; gap: var(--gap-sm); }
.admin__doc { padding: 10px 12px; }
.admin__doc-main {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font-size: var(--text-sm);
  text-align: left;
}
.admin__doc-title { flex: 1; font-weight: 600; }
.admin__doc-hash { color: var(--ink-3); font-size: var(--text-xs); font-family: monospace; }
.admin__doc-main .app-icon { color: var(--ink-3); transition: transform 0.15s; }
.admin__doc-main .app-icon.rotate { transform: rotate(90deg); }
.admin__doc-del {
  border: none;
  background: none;
  color: var(--vermilion);
  font-size: var(--text-xs);
  cursor: pointer;
  padding: 4px 0 0;
}
.admin__chunks {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  display: flex;
  flex-direction: column;
  gap: var(--gap-sm);
}
.admin__chunk {
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
}
.admin__chunk-head { display: flex; justify-content: space-between; align-items: center; font-size: var(--text-xs); margin-bottom: 6px; }
.admin__chunk-head span { color: var(--vermilion); font-weight: 600; }
.admin__chunk-head small { color: var(--ink-3); }
.admin__chunk pre {
  margin: 0;
  font-family: var(--font-body);
  font-size: var(--text-xs);
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 140px;
  overflow-y: auto;
}

.admin__form { display: flex; flex-direction: column; gap: 12px; }
.admin__form label { display: flex; flex-direction: column; gap: 6px; font-size: var(--text-xs); color: var(--ink-2); }
.admin__form select,
.admin__form input,
.admin__form textarea,
.admin__wide {
  font-family: inherit;
  font-size: var(--text-sm);
  color: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  background: var(--paper);
  outline: none;
}
.admin__form select:focus,
.admin__form input:focus,
.admin__form textarea:focus,
.admin__wide:focus { border-color: var(--vermilion); }
.admin__form textarea { resize: vertical; }
.admin__wide { flex: 1; min-width: 0; }
.admin__form-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.admin__btn {
  border: none;
  border-radius: var(--radius-sm);
  padding: 9px 16px;
  font-size: var(--text-sm);
  font-weight: 700;
  cursor: pointer;
  color: #fff;
  background: var(--vermilion);
}
.admin__btn:disabled { opacity: 0.6; }
.admin__btn.jade { background: var(--jade); }
.admin__btn.amber { background: var(--amber); }
.admin__alert {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  background: var(--vermilion-soft);
  color: var(--vermilion);
}
.admin__alert.ok { background: var(--jade-soft); color: var(--jade); }
.admin__hint { color: var(--ink-3); font-size: var(--text-xs); margin: 0 0 10px; }

.admin__split { margin-top: 12px; display: flex; flex-direction: column; gap: var(--gap-sm); }
.admin__split > p { margin: 0 0 4px; font-size: var(--text-sm); }
.admin__result {
  margin-top: 12px;
  padding: 10px 12px;
  border-left: 3px solid var(--vermilion);
  background: var(--paper);
  border-radius: var(--radius-sm);
}
.admin__result.jade { border-left-color: var(--jade); }
.admin__result-meta { font-size: var(--text-xs); color: var(--ink-2); margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
.admin__result p { margin: 0; font-size: var(--text-sm); color: var(--ink-2); white-space: pre-wrap; }
</style>
