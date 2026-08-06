<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { uploadApi, blogApi, shopApi } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const fileInput = ref(null)
const images = ref([]) // 已上传的相对路径
const title = ref('')
const content = ref('')
const uploading = ref(false)
const publishing = ref(false)

const showShop = ref(false)
const shopKeyword = ref('')
const shops = ref([])
const selectedShop = ref(null)

onMounted(() => {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.replace('/login')
  }
})

function openFileDialog() {
  fileInput.value && fileInput.value.click()
}

async function onFileSelected(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    const path = await uploadApi.blog(file)
    images.value.push(path)
  } catch (err) {
    ElMessage.error(typeof err === 'string' ? err : '上传失败')
  } finally {
    uploading.value = false
  }
}

async function removeImage(i) {
  images.value.splice(i, 1)
}

async function searchShops() {
  try {
    shops.value = (await shopApi.ofName({ name: shopKeyword.value, current: 1 })) || []
  } catch {
    shops.value = []
  }
}

function selectShop(s) {
  selectedShop.value = s
  showShop.value = false
}

async function publish() {
  if (!title.value.trim() || !content.value.trim()) {
    ElMessage.error('标题和内容不能为空')
    return
  }
  if (images.value.length === 0) {
    ElMessage.error('请至少上传一张照片')
    return
  }
  publishing.value = true
  try {
    await blogApi.create({
      title: title.value.trim(),
      content: content.value,
      images: images.value.join(','),
      shopId: selectedShop.value ? selectedShop.value.id : null
    })
    ElMessage.success('发布成功')
    router.replace('/me')
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '发布失败')
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="blog-edit page no-tabbar">
    <header class="app-header">
      <button class="app-header__back" style="width: 48px" @click="router.back()">取消</button>
      <div class="app-header__title">发笔记</div>
      <button class="be__publish" :disabled="publishing" @click="publish">
        {{ publishing ? '发布中…' : '发布' }}
      </button>
    </header>

    <!-- 照片 -->
    <section class="be__photos">
      <button class="be__add" :disabled="uploading" @click="openFileDialog">
        <AppIcon name="plus" :size="22" />
        <span>{{ uploading ? '上传中…' : '上传照片' }}</span>
      </button>
      <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="onFileSelected" />
      <div v-for="(img, i) in images" :key="i" class="be__pic">
        <img :src="`/imgs${img}`" alt="" />
        <button aria-label="删除" @click="removeImage(i)">
          <AppIcon name="close" :size="12" />
        </button>
      </div>
    </section>

    <!-- 标题 / 正文 -->
    <section class="be__fields">
      <input v-model="title" class="be__title" type="text" placeholder="填写标题更容易上首页哦~" />
      <div class="tear" />
      <textarea v-model="content" class="be__content" placeholder="最近打卡了什么地方，有什么新奇体验呢？"></textarea>
    </section>

    <!-- 关联商户 -->
    <section class="be__shop" @click="searchShops(); showShop = true">
      <span>关联商户</span>
      <span v-if="selectedShop" class="be__shop-name">{{ selectedShop.name }}</span>
      <span v-else class="be__shop-choose">去选择 <AppIcon name="chevron" :size="14" /></span>
    </section>

    <!-- 商户选择底部抽屉 -->
    <Transition name="sheet">
      <div v-if="showShop" class="be__mask" @click="showShop = false"></div>
    </Transition>
    <Transition name="drawer">
      <section v-if="showShop" class="be__picker">
        <div class="be__picker-head">
          <b>关联商户</b>
          <button class="btn-ghost" @click="showShop = false">完成</button>
        </div>
        <div class="be__picker-search">
          <AppIcon name="search" :size="16" />
          <input v-model="shopKeyword" placeholder="搜索商户名称" @keyup.enter="searchShops" />
          <button @click="searchShops">搜索</button>
        </div>
        <ul class="be__picker-list">
          <li v-for="s in shops" :key="s.id" @click="selectShop(s)">
            <span>{{ s.name }}</span>
            <small>{{ s.area }}</small>
          </li>
          <li v-if="!shops.length" class="be__picker-empty">没有找到商户</li>
        </ul>
      </section>
    </Transition>
  </div>
</template>

<style scoped>
.be__publish {
  margin-left: auto;
  border: none;
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-sm);
  font-weight: 700;
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  cursor: pointer;
}
.be__publish:disabled { opacity: 0.6; }

.be__photos {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding: var(--gap-md);
}
.be__add {
  width: 84px;
  height: 84px;
  border: 2px dashed var(--line);
  border-radius: var(--radius-sm);
  background: var(--card);
  color: var(--ink-3);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: var(--text-xs);
  cursor: pointer;
}
.be__pic {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.be__pic img { width: 100%; height: 100%; object-fit: cover; }
.be__pic button {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.be__fields { background: var(--card); }
.be__title {
  width: 100%;
  border: none;
  outline: none;
  padding: 14px var(--gap-md);
  font-size: var(--text-md);
  font-weight: 700;
  background: none;
}
.be__content {
  width: 100%;
  min-height: 180px;
  border: none;
  outline: none;
  padding: 12px var(--gap-md);
  font-size: var(--text-base);
  line-height: 1.6;
  background: none;
  resize: none;
}

.be__shop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px var(--gap-md);
  background: var(--card);
  border-top: 1px solid var(--line);
  font-size: var(--text-md);
  cursor: pointer;
}
.be__shop-name { color: var(--vermilion); font-weight: 600; }
.be__shop-choose { color: var(--ink-3); font-size: var(--text-sm); display: inline-flex; align-items: center; gap: 2px; }

.be__mask {
  position: fixed;
  inset: 0;
  background: rgba(44, 38, 32, 0.4);
  z-index: 80;
}
.be__picker {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: 480px;
  max-height: 70vh;
  z-index: 81;
  background: var(--card);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.be__picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px var(--gap-md);
  border-bottom: 1px solid var(--line);
}
.be__picker-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px var(--gap-md);
  color: var(--ink-3);
}
.be__picker-search input {
  flex: 1;
  height: 34px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  padding: 0 12px;
  font-size: var(--text-sm);
  outline: none;
  background: var(--paper);
}
.be__picker-search button {
  border: none;
  background: none;
  color: var(--vermilion);
  font-size: var(--text-sm);
  cursor: pointer;
}
.be__picker-list { overflow-y: auto; }
.be__picker-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px var(--gap-md);
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}
.be__picker-list li small { color: var(--ink-3); font-size: var(--text-xs); }
.be__picker-empty { justify-content: center; color: var(--ink-3); }

.drawer-enter-active,
.drawer-leave-active { transition: transform 0.22s ease; }
.drawer-enter-from,
.drawer-leave-to { transform: translateX(-50%) translateY(100%); }
</style>
