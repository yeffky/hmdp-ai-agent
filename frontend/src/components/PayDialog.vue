<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { voucherOrderApi } from '../api'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  modelValue: Boolean,
  orderId: { type: [Number, String], default: 0 }
})
const emit = defineEmits(['update:modelValue', 'paid'])

const payType = ref(1)
const paying = ref(false)

const options = [
  { id: 1, label: '余额支付' },
  { id: 2, label: '支付宝' },
  { id: 3, label: '微信' }
]

async function confirm() {
  paying.value = true
  try {
    await voucherOrderApi.pay(props.orderId, payType.value)
    ElMessage.success('支付成功')
    emit('update:modelValue', false)
    emit('paid')
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '支付失败')
  } finally {
    paying.value = false
  }
}

function close() {
  if (!paying.value) emit('update:modelValue', false)
}
</script>

<template>
  <div v-if="modelValue" class="pay-mask" @click.self="close">
    <div class="pay-dialog">
      <div class="pay-dialog__head">
        <span>选择支付方式</span>
        <button aria-label="关闭" @click="close"><AppIcon name="close" :size="16" /></button>
      </div>
      <div class="pay-dialog__options">
        <button
          v-for="o in options"
          :key="o.id"
          class="pay-dialog__option"
          :class="{ 'is-active': payType === o.id }"
          @click="payType = o.id"
        >{{ o.label }}</button>
      </div>
      <button class="pay-dialog__confirm" :disabled="paying" @click="confirm">
        {{ paying ? '支付中…' : '确认支付' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.pay-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.pay-dialog {
  width: 100%;
  max-width: 480px;
  background: var(--paper);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: var(--gap-md);
}
.pay-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  margin-bottom: var(--gap-sm);
}
.pay-dialog__head button {
  border: none;
  background: none;
  color: var(--ink-3);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}
.pay-dialog__options {
  display: flex;
  gap: 8px;
  margin-bottom: var(--gap-md);
}
.pay-dialog__option {
  flex: 1;
  padding: 12px 0;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--card);
  color: var(--ink-2);
  font-size: var(--text-sm);
  cursor: pointer;
}
.pay-dialog__option.is-active {
  border-color: var(--vermilion);
  color: var(--vermilion);
  font-weight: 700;
}
.pay-dialog__confirm {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-md);
  font-weight: 700;
  cursor: pointer;
}
.pay-dialog__confirm:disabled { opacity: 0.6; }
</style>
