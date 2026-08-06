<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '../../components/AppHeader.vue'
import AppIcon from '../../components/AppIcon.vue'
import { useUserStore } from '../../stores/user'
import { userApi } from '../../api'

const router = useRouter()
const userStore = useUserStore()

const introduce = ref('')
const gender = ref('') // '' 未设置 / 'male' / 'female'
const city = ref('')
const birthday = ref('')
const saving = ref(false)
const loaded = ref(false)

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.replace('/login')
    return
  }
  try {
    await userStore.loadMe(true)
    await userStore.loadInfo(userStore.profile.id, true)
    const info = userStore.info || {}
    introduce.value = info.introduce || ''
    gender.value = info.gender === null || info.gender === undefined ? '' : info.gender ? 'male' : 'female'
    city.value = info.city || ''
    birthday.value = info.birthday || ''
  } catch {
    /* 忽略 */
  } finally {
    loaded.value = true
  }
})

async function save() {
  saving.value = true
  try {
    await userApi.updateInfo({
      userId: userStore.profile.id,
      introduce: introduce.value.trim(),
      gender: gender.value === '' ? null : gender.value === 'male',
      city: city.value.trim(),
      birthday: birthday.value || null
    })
    ElMessage.success('保存成功')
    router.back()
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="edit-info page no-tabbar">
    <AppHeader title="资料编辑" />

    <template v-if="userStore.profile">
      <section class="ei__card ticket">
        <img :src="userStore.profile.icon || '/imgs/icons/default-icon.png'" alt="" />
        <div class="ei__who">
          <b>{{ userStore.profile.nickName }}</b>
          <small>ID {{ userStore.profile.id }}</small>
        </div>
      </section>

      <section class="ei__list">
        <div class="ei__item">
          <span>个人介绍</span>
          <textarea v-model="introduce" class="ei__textarea" rows="2" placeholder="介绍一下自己" />
        </div>
        <div class="tear" />
        <div class="ei__item">
          <span>性别</span>
          <select v-model="gender" class="ei__select">
            <option value="">未设置</option>
            <option value="male">男</option>
            <option value="female">女</option>
          </select>
        </div>
        <div class="tear" />
        <div class="ei__item">
          <span>城市</span>
          <input v-model="city" class="ei__input" placeholder="所在城市" />
        </div>
        <div class="tear" />
        <div class="ei__item">
          <span>生日</span>
          <input v-model="birthday" type="date" class="ei__input" />
        </div>
      </section>

      <button class="ei__save" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </div>
</template>

<style scoped>
.ei__card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: var(--gap-md);
  padding: var(--gap-md);
}
.ei__card img { width: 56px; height: 56px; border-radius: 50%; }
.ei__who { line-height: 1.5; }
.ei__who b { font-size: var(--text-lg); }
.ei__who small { color: var(--ink-3); font-size: var(--text-xs); }

.ei__list { margin: var(--gap-md); background: var(--card); border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
.ei__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px var(--gap-md);
  font-size: var(--text-sm);
}
.ei__textarea,
.ei__input,
.ei__select {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--paper);
  font-size: var(--text-sm);
  padding: 8px 10px;
  outline: none;
  flex: 1;
  max-width: 60%;
  text-align: right;
}
.ei__textarea { text-align: left; resize: none; }
.ei__input:focus,
.ei__textarea:focus { border-color: var(--vermilion); }

.ei__save {
  display: block;
  width: calc(100% - 2 * var(--gap-md));
  margin: var(--gap-md);
  height: 46px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-md);
  font-weight: 700;
  cursor: pointer;
}
.ei__save:disabled { opacity: 0.6; }
</style>
