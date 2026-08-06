<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppHeader from '../../components/AppHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import { shopApi, queueApi } from '../../api'

const shops = ref([])
const selectedShopId = ref(null)
const queueInfo = ref({ currentNumber: 0, waitingCount: 0, waitingList: [] })
const refreshing = ref(false)
const calling = ref(false)
const callResult = ref(null)

onMounted(async () => {
  try {
    shops.value = await shopApi.allByTypes()
  } catch {
    shops.value = []
  }
})

async function refresh() {
  if (!selectedShopId.value) return
  refreshing.value = true
  callResult.value = null
  try {
    queueInfo.value = (await queueApi.shop(selectedShopId.value)) || queueInfo.value
  } catch {
    /* 忽略 */
  } finally {
    refreshing.value = false
  }
}

async function callNext() {
  if (!selectedShopId.value) return
  calling.value = true
  callResult.value = null
  try {
    const res = await queueApi.call(selectedShopId.value)
    callResult.value = res
    ElMessage.success((res && res.message) || '叫号成功')
    setTimeout(refresh, 500)
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '叫号失败')
  } finally {
    calling.value = false
  }
}
</script>

<template>
  <div class="queue-admin page no-tabbar">
    <AppHeader title="排队叫号管理" />

    <div class="admin">
      <!-- 选择商铺 -->
      <section class="admin__card">
        <h2>选择商铺</h2>
        <div class="qa__shop">
          <select v-model="selectedShopId" @change="refresh">
            <option value="" disabled>请选择商铺</option>
            <option v-for="s in shops" :key="s.id" :value="s.id">{{ s.name }}（{{ s.area }}）</option>
          </select>
          <button class="btn-ghost" @click="refresh"><i>⟳</i> 刷新</button>
        </div>
      </section>

      <!-- 叫号状态 -->
      <section v-if="selectedShopId" class="admin__card">
        <h2>叫号状态</h2>
        <div class="qa__current" :key="queueInfo.currentNumber">
          <span class="num ticket-num--hero num-punch">{{ queueInfo.currentNumber || 0 }}</span>
          <small>当前叫号</small>
        </div>
        <div class="qa__stats">
          <div><b class="num jade">{{ queueInfo.waitingCount || 0 }}</b><small>等待中</small></div>
          <div><b class="num">{{ queueInfo.currentNumber || 0 }}</b><small>今日已叫</small></div>
        </div>
        <button
          class="qa__call"
          :disabled="calling || !queueInfo.waitingCount"
          @click="callNext"
        >{{ calling ? '叫号中…' : '叫下一个号' }}</button>
        <div v-if="callResult" class="qa__alert">
          已叫号 <b class="num">{{ callResult.queueNumber }}</b> · {{ callResult.peopleCount }}人
        </div>
      </section>

      <!-- 等待队列 -->
      <section v-if="selectedShopId" class="admin__card">
        <h2>等待队列</h2>
        <div v-if="queueInfo.waitingList && queueInfo.waitingList.length" class="qa__waiting">
          <div v-for="w in queueInfo.waitingList" :key="w.queueNumber" class="qa__waiting-item">
            <span class="num qa__waiting-num">{{ w.queueNumber }}<small>号</small></span>
            <span>{{ w.peopleCount }}人</span>
          </div>
        </div>
        <EmptyState v-else-if="!refreshing" icon="ticket" text="暂无等待顾客" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin { max-width: 640px; margin: 0 auto; padding: var(--gap-md); display: flex; flex-direction: column; gap: var(--gap-md); }
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

.qa__shop { display: flex; gap: 8px; align-items: center; }
.qa__shop select {
  flex: 1;
  min-width: 0;
  font-family: inherit;
  font-size: var(--text-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 9px 10px;
  background: var(--paper);
  outline: none;
}
.qa__shop .btn-ghost { flex: none; font-size: var(--text-xs); }

.qa__current { text-align: center; padding: 10px 0; }
.qa__current small { display: block; color: var(--ink-3); font-size: var(--text-sm); margin-top: 6px; }

.qa__stats {
  display: flex;
  border-top: 1px solid var(--line);
  margin-top: 12px;
  padding-top: 14px;
  text-align: center;
}
.qa__stats > div { flex: 1; line-height: 1.4; }
.qa__stats b { font-size: var(--text-lg); }
.qa__stats b.jade { color: var(--jade); }
.qa__stats small { display: block; color: var(--ink-3); font-size: var(--text-xs); }

.qa__call {
  width: 100%;
  height: 46px;
  margin-top: 14px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-md);
  font-weight: 700;
  cursor: pointer;
}
.qa__call:active { background: var(--vermilion-deep); }
.qa__call:disabled { background: var(--line); color: var(--ink-3); cursor: not-allowed; }

.qa__alert {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--jade-soft);
  color: var(--jade);
  font-size: var(--text-sm);
}

.qa__waiting { display: flex; flex-wrap: wrap; gap: var(--gap-sm); }
.qa__waiting-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-left: 3px solid var(--vermilion);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
}
.qa__waiting-num { font-size: var(--text-lg); color: var(--vermilion); }
.qa__waiting-num small { font-size: var(--text-xs); color: var(--ink-3); margin-left: 2px; }
</style>
