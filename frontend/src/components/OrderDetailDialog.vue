<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { voucherOrderApi } from '../api'
import { fenToYuan, discount } from '../utils/format'
import AppIcon from './AppIcon.vue'
import PayDialog from './PayDialog.vue'

const props = defineProps({
  modelValue: Boolean,
  order: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'canceled', 'paid', 'refunded'])

const STATUS_TEXT = { 1: '待支付', 2: '已支付', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款' }
const PAY_TEXT = { 1: '余额支付', 2: '支付宝', 3: '微信' }

const payVisible = ref(false)
const canceling = ref(false)
const refunding = ref(false)

const o = computed(() => props.order || {})
const discountText = computed(() => discount(o.value.payValue, o.value.actualValue))

function close() {
  if (!canceling.value) emit('update:modelValue', false)
}

// "2026-08-07 18:30"（复用不了 format.js 的 date-only，这里补全到分钟）
function formatTime(str) {
  if (!str) return ''
  const d = new Date(str)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => (n < 10 ? `0${n}` : `${n}`)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function cancel() {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？取消后不可恢复。', '取消订单', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想'
    })
  } catch {
    return // 用户选择不取消
  }
  canceling.value = true
  try {
    await voucherOrderApi.cancel(o.value.id)
    ElMessage.success('订单已取消')
    emit('canceled')
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '取消失败')
  } finally {
    canceling.value = false
  }
}

function pay() {
  payVisible.value = true
}

async function refund() {
  try {
    await ElMessageBox.confirm('确定申请退款吗？退款后该券不可再使用。', '申请退款', {
      type: 'warning',
      confirmButtonText: '确认退款',
      cancelButtonText: '再想想'
    })
  } catch {
    return // 用户选择不退款
  }
  refunding.value = true
  try {
    await voucherOrderApi.refund(o.value.id)
    ElMessage.success('退款成功')
    emit('refunded')
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '退款失败')
  } finally {
    refunding.value = false
  }
}

function onPaid() {
  emit('paid')
  emit('update:modelValue', false)
}
</script>

<template>
  <div v-if="modelValue && o.id" class="od-mask" @click.self="close">
    <div class="od-dialog">
      <div class="od-dialog__head">
        <span>订单详情</span>
        <button aria-label="关闭" @click="close"><AppIcon name="close" :size="16" /></button>
      </div>

      <div class="od-product">
        <img class="od-product__img" :src="o.image" alt="" />
        <div class="od-product__body">
          <h3 class="od-product__title">{{ o.title }}</h3>
          <p class="od-product__shop">{{ o.shopName }}</p>
        </div>
        <span class="od-product__status" :class="'is-' + o.status">{{ STATUS_TEXT[o.status] || '未知' }}</span>
      </div>

      <div class="od-amount">
        <div class="od-amount__row">
          <span class="od-amount__pay num">¥{{ fenToYuan(o.payValue) }}</span>
          <span v-if="o.actualValue" class="od-amount__orig num">¥{{ fenToYuan(o.actualValue) }}</span>
          <span v-if="discountText" class="od-amount__discount">{{ discountText }}</span>
        </div>
        <div v-if="o.actualValue" class="od-amount__save">已优惠 ¥{{ fenToYuan(o.actualValue - o.payValue) }}</div>
      </div>

      <div class="od-info">
        <div class="od-info__row"><span>订单号</span><span class="od-info__val">{{ o.id }}</span></div>
        <div class="od-info__row"><span>券类型</span><span class="od-info__val">{{ o.type === 1 ? '秒杀券' : '普通券' }}</span></div>
        <div class="od-info__row"><span>下单时间</span><span class="od-info__val">{{ formatTime(o.createTime) }}</span></div>
        <div v-if="o.payTime" class="od-info__row"><span>支付时间</span><span class="od-info__val">{{ formatTime(o.payTime) }}</span></div>
        <div v-if="o.payType && o.status !== 1" class="od-info__row"><span>支付方式</span><span class="od-info__val">{{ PAY_TEXT[o.payType] || '其他' }}</span></div>
      </div>

      <div v-if="o.status === 1" class="od-actions">
        <button class="od-actions__ghost" :disabled="canceling" @click="cancel">
          {{ canceling ? '取消中…' : '取消订单' }}
        </button>
        <button class="od-actions__primary" @click="pay">去支付</button>
      </div>
      <div v-else-if="o.status === 2" class="od-actions">
        <button class="od-actions__refund" :disabled="refunding" @click="refund">
          {{ refunding ? '退款中…' : '申请退款' }}
        </button>
      </div>
    </div>

    <PayDialog v-model="payVisible" :order-id="o.id" @paid="onPaid" />
  </div>
</template>

<style scoped>
.od-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.od-dialog {
  width: 100%;
  max-width: 480px;
  background: var(--paper);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: var(--gap-md);
  max-height: 85vh;
  overflow-y: auto;
}
.od-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  margin-bottom: var(--gap-sm);
}
.od-dialog__head button {
  border: none;
  background: none;
  color: var(--ink-3);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}

.od-product {
  display: flex;
  gap: var(--gap-sm);
  padding: var(--gap-sm) 0;
  border-bottom: 1px solid var(--line);
}
.od-product__img {
  width: 72px;
  height: 72px;
  flex: none;
  border-radius: var(--radius-sm);
  object-fit: cover;
  background: var(--line);
}
.od-product__body { flex: 1; min-width: 0; }
.od-product__title { margin: 0; font-size: var(--text-md); }
.od-product__shop { margin: 4px 0 0; color: var(--ink-3); font-size: var(--text-xs); }
.od-product__status { font-size: var(--text-xs); align-self: flex-start; }
.od-product__status.is-1 { color: var(--amber); }
.od-product__status.is-2 { color: var(--jade); }
.od-product__status.is-4 { color: var(--ink-3); }

.od-amount { padding: var(--gap-sm) 0; border-bottom: 1px solid var(--line); }
.od-amount__row { display: flex; align-items: baseline; gap: 8px; }
.od-amount__pay { color: var(--vermilion); font-size: var(--num-md); }
.od-amount__orig { color: var(--ink-3); font-size: var(--text-xs); text-decoration: line-through; }
.od-amount__discount {
  font-size: var(--text-xs);
  color: var(--vermilion);
  background: var(--vermilion-soft);
  padding: 1px 6px;
  border-radius: var(--radius-pill);
}
.od-amount__save { margin-top: 2px; font-size: var(--text-xs); color: var(--jade); }

.od-info { padding: var(--gap-sm) 0; border-bottom: 1px solid var(--line); display: flex; flex-direction: column; gap: 10px; }
.od-info__row { display: flex; justify-content: space-between; gap: var(--gap-md); font-size: var(--text-sm); }
.od-info__row > span:first-child { color: var(--ink-3); flex: none; }
.od-info__val { color: var(--ink-1); word-break: break-all; text-align: right; }

.od-actions { display: flex; gap: 10px; padding-top: var(--gap-md); }
.od-actions button {
  flex: 1;
  height: 44px;
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-md);
  font-weight: 700;
  cursor: pointer;
}
.od-actions__ghost {
  background: var(--card);
  color: var(--ink-2);
  border: 1px solid var(--line) !important;
}
.od-actions__primary {
  background: var(--vermilion);
  color: #fff;
}
.od-actions__refund {
  background: var(--amber);
  color: #fff;
}
.od-actions__ghost:disabled, .od-actions__refund:disabled { opacity: 0.6; }
</style>
