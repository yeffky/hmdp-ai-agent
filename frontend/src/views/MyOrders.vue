<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { voucherOrderApi } from '../api'
import { fenToYuan } from '../utils/format'
import AppHeader from '../components/AppHeader.vue'
import AppTabbar from '../components/AppTabbar.vue'
import EmptyState from '../components/EmptyState.vue'
import PayDialog from '../components/PayDialog.vue'
import OrderDetailDialog from '../components/OrderDetailDialog.vue'

const orders = ref([])
const tab = ref('all')
const payVisible = ref(false)
const payOrderId = ref(0)
const detailVisible = ref(false)
const detailOrder = ref(null)
const loaded = ref(false)

const STATUS_TEXT = { 1: '待支付', 2: '已支付', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款' }
const TABS = [
  { key: 'all', label: '全部' },
  { key: 1, label: '待支付' },
  { key: 2, label: '已支付' },
  { key: 'cancelled', label: '已取消' }
]

// 「已取消」tab 同时归入 已取消(4) + 已退款(6)
const filtered = computed(() => {
  if (tab.value === 'all') return orders.value
  if (tab.value === 'cancelled') return orders.value.filter((o) => o.status === 4 || o.status === 6)
  return orders.value.filter((o) => o.status === tab.value)
})

onMounted(loadOrders)

async function loadOrders() {
  try {
    orders.value = (await voucherOrderApi.my()) || []
  } catch {
    orders.value = []
  }
  loaded.value = true
}

function pay(o) {
  payOrderId.value = o.id
  payVisible.value = true
}

function openDetail(o) {
  detailOrder.value = o
  detailVisible.value = true
}

async function cancel(o) {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？取消后不可恢复。', '取消订单', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想'
    })
  } catch {
    return // 用户选择不取消
  }
  try {
    await voucherOrderApi.cancel(o.id)
    ElMessage.success('已取消')
    loadOrders()
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '取消失败')
  }
}

function onPaid() {
  loadOrders()
}
</script>

<template>
  <div class="orders page">
    <AppHeader title="我的订单" />

    <nav class="orders__tabs">
      <button
        v-for="t in TABS"
        :key="t.key"
        class="orders__tab"
        :class="{ 'is-active': tab === t.key }"
        @click="tab = t.key"
      >{{ t.label }}</button>
    </nav>

    <div v-if="filtered.length" class="orders__list">
      <div v-for="o in filtered" :key="o.id" class="order-card" @click="openDetail(o)">
        <img class="order-card__img" :src="o.image" alt="" loading="lazy" />
        <div class="order-card__body">
          <h3 class="order-card__title ellipsis">{{ o.title }}</h3>
          <p class="order-card__shop">{{ o.shopName }}</p>
          <div class="order-card__price">
            <span class="order-card__pay num">¥{{ fenToYuan(o.payValue) }}</span>
            <span v-if="o.status === 1" class="order-card__orig num">¥{{ fenToYuan(o.actualValue) }}</span>
          </div>
        </div>
        <div class="order-card__side">
          <span class="order-card__status" :class="'is-' + o.status">{{ STATUS_TEXT[o.status] || '未知' }}</span>
          <div v-if="o.status === 1" class="order-card__actions" @click.stop>
            <button class="order-card__btn order-card__btn--ghost" @click="cancel(o)">取消</button>
            <button class="order-card__btn" @click="pay(o)">去支付</button>
          </div>
        </div>
      </div>
    </div>

    <EmptyState v-else-if="loaded" icon="ticket" text="还没有相关订单" />

    <PayDialog v-model="payVisible" :order-id="payOrderId" @paid="onPaid" />
    <OrderDetailDialog
      v-model="detailVisible"
      :order="detailOrder"
      @canceled="loadOrders"
      @paid="loadOrders"
      @refunded="loadOrders"
    />

    <AppTabbar />
  </div>
</template>

<style scoped>
.orders__tabs {
  display: flex;
  gap: 8px;
  padding: 10px var(--gap-md);
  overflow-x: auto;
  scrollbar-width: none;
}
.orders__tabs::-webkit-scrollbar { display: none; }
.orders__tab {
  flex: none;
  border: none;
  background: none;
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  color: var(--ink-2);
  font-size: var(--text-sm);
  cursor: pointer;
}
.orders__tab.is-active { background: var(--vermilion-soft); color: var(--vermilion); font-weight: 700; }

.orders__list { padding: 0 var(--gap-md); display: flex; flex-direction: column; gap: var(--gap-md); }
.order-card {
  display: flex;
  gap: var(--gap-md);
  padding: 10px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  cursor: pointer;
}
.order-card__img {
  width: 80px;
  height: 80px;
  flex: none;
  border-radius: var(--radius-sm);
  object-fit: cover;
  background: var(--line);
}
.order-card__body { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: space-between; }
.order-card__title { margin: 0; font-size: var(--text-sm); }
.order-card__shop { margin: 2px 0 0; color: var(--ink-3); font-size: var(--text-xs); }
.order-card__price { display: flex; align-items: baseline; gap: 6px; }
.order-card__pay { color: var(--vermilion); font-size: var(--text-md); }
.order-card__orig { color: var(--ink-3); font-size: var(--text-xs); text-decoration: line-through; }
.order-card__side { display: flex; flex-direction: column; align-items: flex-end; justify-content: space-between; }
.order-card__status { font-size: var(--text-xs); }
.order-card__status.is-1 { color: var(--amber); }
.order-card__status.is-2 { color: var(--jade); }
.order-card__status.is-4 { color: var(--ink-3); }
.order-card__actions { display: flex; gap: 6px; }
.order-card__btn {
  border: none;
  border-radius: var(--radius-pill);
  background: var(--vermilion);
  color: #fff;
  padding: 5px 14px;
  font-size: var(--text-xs);
  cursor: pointer;
}
.order-card__btn--ghost { background: none; color: var(--ink-2); border: 1px solid var(--line); }
</style>
