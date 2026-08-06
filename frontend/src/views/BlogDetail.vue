<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import { blogApi, followApi, shopApi, blogCommentApi } from '../api'
import { useUserStore } from '../stores/user'
import { useChatStore } from '../stores/chat'
import { relativeTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chat = useChatStore()

const blog = ref(null)
const shop = ref(null)
const likers = ref([])
const followed = ref(false)
const activeImg = ref(0)

const comments = ref([])
const commentTotal = ref(0)
const commentCurrent = ref(1)
const commentHasMore = ref(false)
const commentText = ref('')
const commentSubmitting = ref(false)

const isMine = computed(() => blog.value && userStore.profile && blog.value.userId === userStore.profile.id)

async function loadBlog(id) {
  blog.value = null
  shop.value = null
  likers.value = []
  followed.value = false
  comments.value = []
  commentCurrent.value = 1
  try {
    const b = await blogApi.detail(id)
    b.images = (b.images || '').split(',')
    blog.value = b
    loadShop(b.shopId)
    loadLikers(id)
    loadComments()
    if (userStore.isLoggedIn) loadFollowState(b.userId)
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '笔记不存在')
  }
}

async function loadComments() {
  try {
    const res = (await blogCommentApi.list(route.params.id, commentCurrent.value)) || {}
    commentTotal.value = res.total || 0
    commentHasMore.value = !!res.hasMore
    comments.value = commentCurrent.value === 1 ? res.list || [] : comments.value.concat(res.list || [])
  } catch {
    /* 评论加载失败忽略 */
  }
}

async function submitComment() {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  const text = commentText.value.trim()
  if (!text) return ElMessage.error('评论内容不能为空')
  commentSubmitting.value = true
  try {
    await blogCommentApi.add({ blogId: route.params.id, content: text })
    commentText.value = ''
    ElMessage.success('评论成功')
    commentCurrent.value = 1
    loadComments()
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '评论失败')
  } finally {
    commentSubmitting.value = false
  }
}

onMounted(() => loadBlog(route.params.id))
watch(() => route.params.id, (id) => loadBlog(id))

async function loadShop(shopId) {
  if (!shopId) return
  try {
    const s = await shopApi.detail(shopId)
    s.image = (s.images || '').split(',')[0]
    s.price = s.avgPrice
    shop.value = s
  } catch { /* 关联商户加载失败忽略 */ }
}

async function loadLikers(id) {
  try {
    likers.value = (await blogApi.likes(id)) || []
  } catch { /* 忽略 */ }
}

async function loadFollowState(userId) {
  try {
    followed.value = !!(await followApi.isFollowing(userId))
  } catch { /* 忽略 */ }
}

async function toggleFollow() {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  try {
    await followApi.toggle(blog.value.userId, !followed.value)
    followed.value = !followed.value
    ElMessage.success(followed.value ? '已关注' : '已取消关注')
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '操作失败')
  }
}

async function toggleLike() {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  try {
    await blogApi.toggleLike(blog.value.id)
    blog.value.isLike = !blog.value.isLike
    blog.value.liked = (blog.value.liked || 0) + (blog.value.isLike ? 1 : -1)
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '操作失败')
  }
}

function toOther() {
  if (isMine.value) router.push('/me')
  else router.push(`/user/${blog.value.userId}`)
}

function chatOpen() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  chat.open()
}
</script>

<template>
  <div class="blog-detail page no-tabbar">
    <AppHeader title="探店笔记" />

    <template v-if="blog">
      <!-- 图集 -->
      <div class="bd__gallery">
        <img :src="blog.images[0]" :alt="blog.title" />
        <div v-if="blog.images.length > 1" class="bd__dots">
          <span v-for="(_, i) in blog.images" :key="i" :class="{ 'is-active': activeImg === i }" />
        </div>
        <span class="bd__count stamp" v-if="blog.images.length > 1">
          {{ activeImg + 1 }}/{{ blog.images.length }}
        </span>
      </div>

      <!-- 作者 -->
      <div class="bd__author">
        <button class="bd__author-main" @click="toOther">
          <img :src="blog.icon || '/imgs/icons/default-icon.png'" alt="" />
        </button>
        <div class="bd__author-info">
          <b>{{ blog.name }}</b>
          <small>{{ relativeTime(blog.createTime) }}</small>
        </div>
        <button v-if="!isMine" class="bd__follow" :class="{ 'is-followed': followed }" @click="toggleFollow">
          {{ followed ? '已关注' : '+ 关注' }}
        </button>
      </div>

      <!-- 正文 -->
      <article class="bd__body">
        <h1>{{ blog.title }}</h1>
        <div class="bd__content">{{ blog.content }}</div>
      </article>

      <!-- 关联店铺 -->
      <button v-if="shop" class="bd__shop ticket" @click="router.push(`/shop/${shop.id}`)">
        <img :src="shop.image" alt="" />
        <div class="bd__shop-info">
          <b>{{ shop.name }}</b>
          <span class="bd__shop-rate num">{{ (shop.score / 10).toFixed(1) }} 分</span>
          <small>¥{{ shop.price }}/人</small>
        </div>
        <AppIcon name="chevron" :size="18" />
      </button>

      <!-- 点赞区 -->
      <div class="bd__zan">
        <div class="bd__zan-avatars">
          <img v-for="u in likers" :key="u.id" :src="u.icon || '/imgs/icons/default-icon.png'" alt="" />
          <span v-if="blog.liked" class="num">{{ blog.liked }}人赞过</span>
        </div>
      </div>

      <div class="tear--lg" />

      <!-- 评论区 -->
      <section class="bd__comments">
        <h2>网友评价 <span class="num">({{ commentTotal }})</span></h2>
        <div v-if="comments.length" class="bd__comment-list">
          <div v-for="c in comments" :key="c.id" class="bd__comment">
            <img :src="c.icon || '/imgs/icons/default-icon.png'" alt="" />
            <div>
              <b>{{ c.nickName }}</b>
              <p>{{ c.content }}</p>
              <span class="bd__comment-time">{{ relativeTime(c.createTime) }}</span>
            </div>
          </div>
          <button v-if="commentHasMore" class="bd__comment-more" @click="commentCurrent++; loadComments()">
            加载更多
          </button>
        </div>
        <p v-else class="bd__comment-empty">还没有评论，来抢沙发～</p>
        <div class="bd__comment-input">
          <input v-model="commentText" placeholder="说点什么…" @keyup.enter="submitComment" />
          <button :disabled="commentSubmitting" @click="submitComment">
            {{ commentSubmitting ? '发送中…' : '发表' }}
          </button>
        </div>
      </section>
    </template>

    <!-- 底部操作栏 -->
    <div class="bd__footbar">
      <button :class="{ 'is-active': blog && blog.isLike }" @click="toggleLike">
        <AppIcon name="heart" :size="22" />
        <span class="num">{{ blog ? blog.liked : 0 }}</span>
      </button>
      <button class="bd__footbar-go" @click="chatOpen()">
        <AppIcon name="chat" :size="18" /> 问小优
      </button>
    </div>
  </div>
</template>

<style scoped>
.bd__gallery { position: relative; }
.bd__gallery > img { width: 100%; height: 300px; object-fit: cover; }
.bd__dots {
  position: absolute;
  left: 50%;
  bottom: 10px;
  transform: translateX(-50%);
  display: flex;
  gap: 5px;
}
.bd__dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.55);
}
.bd__dots span.is-active { background: #fff; width: 14px; border-radius: 3px; }
.bd__count { position: absolute; right: 10px; bottom: 8px; }

.bd__author {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px var(--gap-md);
}
.bd__author-main { border: none; background: none; padding: 0; cursor: pointer; }
.bd__author-main img { width: 42px; height: 42px; border-radius: 50%; }
.bd__author-info { flex: 1; line-height: 1.3; }
.bd__author-info b { font-size: var(--text-md); }
.bd__author-info small { color: var(--ink-3); font-size: var(--text-xs); }
.bd__follow {
  border: 1px solid var(--vermilion);
  color: var(--vermilion);
  background: none;
  border-radius: var(--radius-pill);
  padding: 6px 14px;
  font-size: var(--text-sm);
  cursor: pointer;
}
.bd__follow.is-followed { border-color: var(--line); color: var(--ink-3); }

.bd__body { padding: 0 var(--gap-md) var(--gap-lg); }
.bd__body h1 { margin: 0 0 10px; font-size: var(--text-lg); }
.bd__content { font-size: var(--text-base); line-height: 1.7; color: var(--ink-2); white-space: pre-wrap; word-break: break-word; }

.bd__shop {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 var(--gap-md);
  padding: 10px;
  width: calc(100% - var(--gap-lg));
  cursor: pointer;
}
.bd__shop img { width: 56px; height: 56px; border-radius: var(--radius-sm); object-fit: cover; }
.bd__shop-info { flex: 1; text-align: left; line-height: 1.4; }
.bd__shop-info b { display: block; }
.bd__shop-rate { color: var(--amber); font-size: var(--text-sm); }
.bd__shop-info small { color: var(--ink-3); }

.bd__zan { padding: var(--gap-md); display: flex; align-items: center; }
.bd__zan-avatars { display: flex; align-items: center; gap: 6px; }
.bd__zan-avatars img { width: 28px; height: 28px; border-radius: 50%; border: 2px solid var(--card); margin-left: -8px; }
.bd__zan-avatars img:first-child { margin-left: 0; }
.bd__zan-avatars span { margin-left: 8px; color: var(--ink-2); font-size: var(--text-xs); }

.bd__comments { padding: var(--gap-md); }
.bd__comments h2 { font-size: var(--text-md); margin: 0 0 var(--gap-md); }
.bd__comment-list { display: flex; flex-direction: column; gap: var(--gap-md); }
.bd__comment { display: flex; gap: 10px; }
.bd__comment img { width: 34px; height: 34px; border-radius: 50%; object-fit: cover; }
.bd__comment b { font-size: var(--text-sm); }
.bd__comment p { margin: 4px 0 0; color: var(--ink-2); font-size: var(--text-sm); }
.bd__comment-time { color: var(--ink-3); font-size: var(--text-xs); }
.bd__comment-more {
  width: 100%;
  border: 1px dashed var(--line);
  background: none;
  color: var(--ink-2);
  border-radius: var(--radius-pill);
  padding: 8px 0;
  font-size: var(--text-xs);
  cursor: pointer;
}
.bd__comment-empty { color: var(--ink-3); font-size: var(--text-sm); text-align: center; padding: 12px 0; }
.bd__comment-input { display: flex; gap: 8px; margin-top: var(--gap-md); }
.bd__comment-input input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--paper);
  font-size: var(--text-sm);
  outline: none;
}
.bd__comment-input input:focus { border-color: var(--vermilion); }
.bd__comment-input button {
  flex: none;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--vermilion);
  color: #fff;
  padding: 0 18px;
  font-size: var(--text-sm);
  cursor: pointer;
}
.bd__comment-input button:disabled { opacity: 0.6; }

.bd__footbar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: 480px;
  height: var(--tabbar-h);
  display: flex;
  align-items: center;
  gap: var(--gap-md);
  padding: 0 var(--gap-lg);
  background: rgba(255, 252, 246, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid var(--line);
  z-index: 30;
  padding-bottom: env(safe-area-inset-bottom);
}
.bd__footbar > button:first-child {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  color: var(--ink-3);
  cursor: pointer;
  font-size: var(--text-xs);
}
.bd__footbar > button:first-child.is-active { color: var(--vermilion); }
.bd__footbar-go {
  flex: 1;
  height: 36px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
</style>
