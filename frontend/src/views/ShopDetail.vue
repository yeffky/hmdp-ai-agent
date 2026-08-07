<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import { shopApi, voucherApi, voucherOrderApi, queueApi, blogApi, shopCommentApi } from '../api'
import { useUserStore } from '../stores/user'
import { fenToYuan, discount, seckillState, relativeTime } from '../utils/format'
import PayDialog from '../components/PayDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const shop = ref(null)
const vouchers = ref([])
const queueTicket = ref(null)
const shopQueue = ref(null)
const relatedBlogs = ref([])

const comments = ref([])
const commentTotal = ref(0)
const commentCurrent = ref(1)
const commentHasMore = ref(false)
const commentText = ref('')
const commentRating = ref(5)
const commentSubmitting = ref(false)

const payVisible = ref(false)
const payOrderId = ref(0)

const queuePeople = ref(2)
const queueLoading = ref(false)
const queueCancelling = ref(false)
const taking = ref(null)

const now = ref(Date.now())
let ticker = null

const shopId = computed(() => Number(route.params.id))

onMounted(async () => {
  try {
    const s = await shopApi.detail(shopId.value)
    s.images = (s.images || '').split(',')
    s.rating = (s.score || 0) / 10
    shop.value = s
    if (s.queueEnabled !== 0) {
      loadMyQueue()
      loadShopQueue()
    }
  } catch { /* 店铺加载失败 */ }
  await loadVouchers()
  loadRelatedBlogs()
  loadComments()
  ticker = setInterval(() => (now.value = Date.now()), 1000)
})
onBeforeUnmount(() => clearInterval(ticker))

async function loadMyQueue() {
  try {
    queueTicket.value = (await queueApi.my()) || null
  } catch { queueTicket.value = null }
}

async function loadShopQueue() {
  try {
    shopQueue.value = await queueApi.shop(shopId.value)
  } catch { shopQueue.value = null }
}

async function takeNumber() {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  queueLoading.value = true
  try {
    const t = await queueApi.take(shopId.value, queuePeople.value)
    queueTicket.value = t
    taking.value = t.queueNumber
    ElMessage.success(`取号成功，您是 ${t.queueNumber} 号`)
    loadShopQueue()
    setTimeout(() => (taking.value = null), 800)
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '取号失败')
  } finally {
    queueLoading.value = false
  }
}

async function cancelQueue() {
  if (!queueTicket.value) return
  queueCancelling.value = true
  try {
    await queueApi.cancel(queueTicket.value.ticketId)
    ElMessage.success('已取消排队')
    queueTicket.value = null
    loadShopQueue()
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '取消失败')
  } finally {
    queueCancelling.value = false
  }
}

function stateOf(v) {
  return seckillState(v, now.value)
}

function seckillText(v) {
  const st = stateOf(v)
  if (st === 'not_begin') return `距开抢 ${countdownText(v.beginTime)}`
  if (st === 'ended') return '已结束'
  return `距结束 ${countdownText(v.endTime)}`
}

function countdownText(target) {
  const diff = Math.max(0, new Date(target).getTime() - now.value)
  const s = Math.floor(diff / 1000)
  if (s <= 0) return '00:00:00'
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const p = (n) => String(n).padStart(2, '0')
  return `${p(h)}:${p(m)}:${p(sec)}`
}

async function seckill(v) {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  const st = stateOf(v)
  if (st === 'not_begin') return ElMessage.error('优惠券抢购尚未开始！')
  if (st === 'ended') return ElMessage.error('优惠券抢购已经结束！')
  if (v.stock < 1) return ElMessage.error('库存不足！')
  if (v.bought) return ElMessage.info('您已购买过该秒杀券')
  try {
    await voucherOrderApi.seckill(v.id)
    v.bought = true
    ElMessage.success('秒杀成功，请到「我的-订单」完成支付')
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '抢购失败')
  }
}

async function loadVouchers() {
  try {
    vouchers.value = (await voucherApi.list(shopId.value)) || []
    // 登录用户：标记已购买（存在订单记录）的券 → 置灰按钮
    // 后端 seckillStatus 返回 count(user_id,voucher_id)>0，因 uk_user_voucher 唯一索引，有订单即不可再买
    if (userStore.isLoggedIn) {
      await Promise.all(
        vouchers.value.map(async (v) => {
          try {
            v.bought = !!(await voucherOrderApi.seckillStatus(v.id))
          } catch {
            v.bought = false
          }
        })
      )
    }
  } catch {
    vouchers.value = []
  }
}

async function loadRelatedBlogs() {
  try {
    relatedBlogs.value = ((await blogApi.ofShop(shopId.value)) || []).map((b) => ({
      ...b,
      img: (b.images || '').split(',')[0]
    }))
  } catch {
    relatedBlogs.value = []
  }
}

async function buy(v) {
  if (!userStore.isLoggedIn) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  if (v.bought) return ElMessage.info('您已购买过该券')
  try {
    const orderId = await voucherOrderApi.buy(v.id)
    payOrderId.value = orderId
    payVisible.value = true
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '下单失败')
  }
}

function onPaid() {
  loadVouchers()
}

async function loadComments(reset = false) {
  if (reset) {
    commentCurrent.value = 1
    comments.value = []
  }
  try {
    const res = (await shopCommentApi.list(shopId.value, commentCurrent.value)) || {}
    commentTotal.value = res.total || 0
    commentHasMore.value = !!res.hasMore
    comments.value = reset ? res.list || [] : comments.value.concat(res.list || [])
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
    await shopCommentApi.add({ shopId: shopId.value, content: text, rating: commentRating.value })
    commentText.value = ''
    commentRating.value = 5
    ElMessage.success('评论成功')
    loadComments(true)
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : '评论失败')
  } finally {
    commentSubmitting.value = false
  }
}
</script>

<template>
  <div class="shop-detail page no-tabbar">
    <AppHeader title="店铺详情" />

    <template v-if="shop">
      <!-- 基本信息 -->
      <section class="sd__basic">
        <h1 class="sd__name">{{ shop.name }}</h1>
        <div class="sd__rate">
          <span class="sd__score num">{{ shop.rating.toFixed(1) }}</span>
          <div class="sd__bar" :style="{ '--w': `${Math.max(6, shop.rating * 20)}%` }" />
          <span class="sd__dim">{{ shop.comments }}条评价</span>
          <span class="sd__dim">{{ shop.openHours }}</span>
        </div>
        <div class="sd__tags">
          <span class="sd__tag">口味 {{ shop.rating.toFixed(1) }}</span>
          <span class="sd__tag">环境 {{ shop.rating.toFixed(1) }}</span>
          <span class="sd__tag">服务 {{ shop.rating.toFixed(1) }}</span>
          <span class="sd__rank stamp">好评榜</span>
        </div>

        <div class="sd__gallery">
          <img
            v-for="(img, i) in shop.images"
            :key="i"
            :src="img"
            alt=""
            loading="lazy"
          />
        </div>

        <div class="sd__address">
          <AppIcon name="pin" :size="16" />
          <span class="ellipsis">{{ shop.address }}</span>
          <span class="sd__price">¥{{ shop.avgPrice }}/人</span>
        </div>
      </section>

      <!-- 排队取号：签名区块（部分商家不开放） -->
      <section v-if="shop.queueEnabled !== 0" class="sd__section">
        <h2 class="sd__section-title">
          <span class="stamp">号</span>
          排队取号
        </h2>

        <div class="queue-card">
          <!-- 未取号 -->
          <template v-if="!queueTicket || queueTicket.shopId !== shopId">
            <p class="queue-desc">到店前先取个号，到店不用等太久</p>
            <div class="queue-people">
              <button
                v-for="n in [1, 2, 3, 4, 5]"
                :key="n"
                :class="{ 'is-active': queuePeople === n }"
                @click="queuePeople = n"
              >{{ n === 5 ? '5+' : n }}人</button>
            </div>
            <button class="queue-take" :disabled="queueLoading" @click="takeNumber">
              {{ queueLoading ? '取号中…' : '立即取号' }}
            </button>
            <p v-if="queueTicket && queueTicket.shopId !== shopId" class="queue-other">
              你已在另一家店排队，取号会覆盖原记录
            </p>
          </template>

          <!-- 已取号：号票 -->
          <template v-else>
            <div class="queue-ticket" :class="{ 'num-punch': taking === queueTicket.queueNumber }">
              <span class="queue-ticket__label">您的排队号</span>
              <span class="queue-ticket__num num">{{ queueTicket.queueNumber }}</span>
              <div class="queue-ticket__meta">
                <span>{{ queueTicket.peopleCount }}人用餐</span>
                <span class="queue-ticket__dot">·</span>
                <span>预计等待 {{ queueTicket.estimatedWait }}</span>
              </div>
              <button class="queue-cancel" @click="cancelQueue">
                {{ queueCancelling ? '取消中…' : '取消排队' }}
              </button>
            </div>
          </template>

          <div v-if="shopQueue" class="queue-status">
            <span>当前叫号 <b class="num">{{ shopQueue.currentNumber || 0 }}</b></span>
            <span>前方等待 <b class="num jade">{{ shopQueue.waitingCount || 0 }}</b> 桌</span>
          </div>
        </div>
      </section>

      <!-- 团购商品 -->
      <section v-if="vouchers.length" class="sd__section">
        <h2 class="sd__section-title">
          <span class="stamp stamp--amber">购</span>
          团购
        </h2>
        <div class="sd__deals">
          <div v-for="v in vouchers" :key="v.id" class="deal">
            <img class="deal__img" :src="v.image" alt="" loading="lazy" />
            <div class="deal__body">
              <h3 class="deal__title">{{ v.title }}</h3>
              <p v-if="v.subTitle" class="deal__sub">{{ v.subTitle }}</p>
              <div class="deal__price">
                <span class="deal__pay num">¥{{ fenToYuan(v.payValue) }}</span>
                <span class="deal__orig num">¥{{ fenToYuan(v.actualValue) }}</span>
                <span v-if="!v.type" class="deal__fold">{{ discount(v.payValue, v.actualValue) }}</span>
              </div>
              <button
                v-if="v.type"
                class="deal__btn"
                :class="{ 'is-off': v.bought || stateOf(v) !== 'active' || v.stock < 1 }"
                :disabled="v.bought || stateOf(v) !== 'active' || v.stock < 1"
                @click="seckill(v)"
              >{{ v.bought ? '已购买' : stateOf(v) === 'ended' ? '已结束' : `限时秒杀${v.stock >= 0 ? ' · 余' + v.stock : ''}` }}</button>
              <button
                v-else
                class="deal__btn"
                :class="{ 'is-off': v.bought }"
                :disabled="v.bought"
                @click="buy(v)"
              >{{ v.bought ? '已购买' : '去购买' }}</button>
            </div>
          </div>
        </div>
      </section>

      <!-- 相关探店 -->
      <section v-if="relatedBlogs.length" class="sd__section">
        <h2 class="sd__section-title">
          <span class="stamp">探</span>
          相关探店
        </h2>
        <div class="sd__blogs">
          <article v-for="b in relatedBlogs" :key="b.id" class="sd-blog" @click="router.push(`/blog/${b.id}`)">
            <img class="sd-blog__img" :src="b.img" alt="" loading="lazy" />
            <h3 class="sd-blog__title ellipsis">{{ b.title }}</h3>
            <footer class="sd-blog__foot">
              <img class="sd-blog__avatar" :src="b.icon || '/imgs/icons/default-icon.png'" alt="" />
              <span class="sd-blog__author ellipsis">{{ b.name }}</span>
              <span class="sd-blog__likes num">{{ b.liked }}</span>
            </footer>
          </article>
        </div>
      </section>

      <!-- 网友评价 -->
      <section class="sd__section">
        <h2 class="sd__section-title">
          <span class="stamp stamp--jade">评</span>
          网友评价 <span class="num">({{ commentTotal }})</span>
        </h2>

        <div v-if="comments.length" class="sd__comments">
          <div v-for="c in comments" :key="c.id" class="comment">
            <img class="comment__avatar" :src="c.icon || '/imgs/icons/default-icon.png'" alt="" />
            <div class="comment__body">
              <div class="comment__head">
                <b class="comment__name">{{ c.nickName }}</b>
                <span class="comment__stars">{{ '★'.repeat(c.rating || 5) }}</span>
              </div>
              <p class="comment__content">{{ c.content }}</p>
              <span class="comment__time">{{ relativeTime(c.createTime) }}</span>
            </div>
          </div>
          <button v-if="commentHasMore" class="comment__more" @click="commentCurrent++; loadComments()">
            加载更多
          </button>
        </div>
        <p v-else class="comment__empty">还没有评论，来抢沙发～</p>

        <div class="comment__rate">
          <span>评分：</span>
          <button
            v-for="n in 5"
            :key="n"
            class="comment__star"
            :class="{ 'is-active': n <= commentRating }"
            @click="commentRating = n"
          >{{ n <= commentRating ? '★' : '☆' }}</button>
        </div>
        <div class="comment__input">
          <input
            v-model="commentText"
            placeholder="说点什么…"
            @keyup.enter="submitComment"
          />
          <button :disabled="commentSubmitting" @click="submitComment">
            {{ commentSubmitting ? '发送中…' : '发表' }}
          </button>
        </div>
      </section>
    </template>

    <div v-else class="empty"><AppIcon name="ticket" :size="30" /><p>店铺不存在或加载失败</p></div>

    <PayDialog v-model="payVisible" :order-id="payOrderId" @paid="onPaid" />
  </div>
</template>

<style scoped>
.sd__basic {
  padding: var(--gap-md);
}
.sd__name { margin: 0; font-size: var(--text-xl); }
.sd__rate { display: flex; align-items: center; gap: 8px; margin: 6px 0; }
.sd__score { color: var(--amber); font-size: var(--text-lg); }
.sd__bar {
  width: 64px;
  height: 5px;
  border-radius: 3px;
  background: var(--line);
  overflow: hidden;
  position: relative;
}
.sd__bar::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: var(--w);
  background: var(--amber);
  border-radius: 3px;
}
.sd__dim { color: var(--ink-3); font-size: var(--text-xs); }
.sd__tags { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sd__tag { font-size: var(--text-xs); color: var(--ink-2); }
.sd__rank { font-size: var(--text-xs); margin-left: auto; }
.sd__gallery {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  margin: var(--gap-md) 0;
  scrollbar-width: none;
}
.sd__gallery::-webkit-scrollbar { display: none; }
.sd__gallery img {
  width: 96px;
  height: 96px;
  flex: none;
  border-radius: var(--radius-sm);
  object-fit: cover;
  background: var(--line);
}
.sd__address {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-2);
  font-size: var(--text-sm);
}
.sd__address .sd__price { margin-left: auto; color: var(--amber); font-weight: 700; font-family: var(--font-display); }

.sd__section {
  margin-top: var(--gap-lg);
  padding: var(--gap-lg) var(--gap-md);
  background: var(--card);
  border-block: 1px solid var(--line);
}
.sd__section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 var(--gap-md);
  font-size: var(--text-lg);
}

/* 排队卡 */
.queue-card { position: relative; }
.queue-desc { margin: 0 0 10px; color: var(--ink-3); font-size: var(--text-sm); }
.queue-people { display: flex; gap: 8px; }
.queue-people button {
  flex: 1;
  padding: 8px 0;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--ink-2);
  font-size: var(--text-sm);
  cursor: pointer;
}
.queue-people button.is-active {
  background: var(--vermilion);
  border-color: var(--vermilion);
  color: #fff;
  font-weight: 700;
}
.queue-take {
  width: 100%;
  height: 46px;
  margin-top: 12px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--vermilion);
  color: #fff;
  font-size: var(--text-md);
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-card);
}
.queue-take:active { background: var(--vermilion-deep); }
.queue-take:disabled { opacity: 0.6; }
.queue-other { margin: 8px 0 0; color: var(--amber); font-size: var(--text-xs); }

.queue-ticket {
  text-align: center;
  padding: var(--gap-lg);
  border: 2px dashed var(--vermilion);
  border-radius: var(--radius-lg);
  background: var(--vermilion-soft);
}
.queue-ticket__label { display: block; color: var(--vermilion); font-size: var(--text-sm); }
.queue-ticket__num {
  display: block;
  font-size: var(--num-hero);
  color: var(--vermilion);
  margin: 4px 0 8px;
}
.queue-ticket__meta { color: var(--ink-2); font-size: var(--text-sm); }
.queue-ticket__dot { margin: 0 6px; color: var(--ink-3); }
.queue-cancel {
  margin-top: 12px;
  border: 1px solid var(--vermilion);
  color: var(--vermilion);
  background: none;
  border-radius: var(--radius-pill);
  padding: 6px 16px;
  font-size: var(--text-xs);
  cursor: pointer;
}
.queue-status {
  display: flex;
  justify-content: space-around;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  color: var(--ink-2);
  font-size: var(--text-sm);
}
.queue-status b { font-size: var(--text-lg); margin-left: 2px; }
.queue-status b.jade { color: var(--jade); }

.sd__coupons { display: flex; flex-direction: column; gap: var(--gap-md); }

/* 团购商品 */
.sd__deals { display: flex; flex-direction: column; gap: var(--gap-md); }
.deal {
  display: flex;
  gap: var(--gap-md);
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.deal__img {
  width: 104px;
  height: 104px;
  flex: none;
  object-fit: cover;
  background: var(--line);
}
.deal__body { flex: 1; min-width: 0; padding: 10px 12px; display: flex; flex-direction: column; justify-content: space-between; }
.deal__title { margin: 0; font-size: var(--text-md); font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.deal__sub { margin: 2px 0 0; color: var(--ink-3); font-size: var(--text-xs); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.deal__price { display: flex; align-items: baseline; gap: 6px; margin: 6px 0; }
.deal__pay { color: var(--vermilion); font-size: var(--text-lg); }
.deal__orig { color: var(--ink-3); font-size: var(--text-xs); text-decoration: line-through; }
.deal__fold { color: var(--amber); font-size: var(--text-xs); font-weight: 700; }
.deal__btn {
  align-self: flex-start;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--vermilion);
  color: #fff;
  padding: 7px 18px;
  font-size: var(--text-sm);
  font-weight: 700;
  cursor: pointer;
}
.deal__btn.is-off { background: var(--line); color: var(--ink-3); cursor: not-allowed; }

/* 相关探店 */
.sd__blogs { display: flex; gap: var(--gap-md); overflow-x: auto; scrollbar-width: none; }
.sd__blogs::-webkit-scrollbar { display: none; }
.sd-blog {
  flex: none;
  width: 150px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
}
.sd-blog__img { width: 150px; height: 100px; object-fit: cover; background: var(--line); }
.sd-blog__title { margin: 8px 10px 6px; font-size: var(--text-sm); }
.sd-blog__foot { display: flex; align-items: center; gap: 6px; padding: 0 10px 10px; }
.sd-blog__avatar { width: 20px; height: 20px; border-radius: 50%; object-fit: cover; }
.sd-blog__author { flex: 1; color: var(--ink-3); font-size: var(--text-xs); }
.sd-blog__likes { color: var(--ink-3); font-size: var(--text-xs); }

/* 网友评价 */
.sd__comments { display: flex; flex-direction: column; gap: var(--gap-md); }
.comment { display: flex; gap: 10px; }
.comment__avatar { width: 36px; height: 36px; flex: none; border-radius: 50%; object-fit: cover; background: var(--line); }
.comment__body { flex: 1; min-width: 0; }
.comment__head { display: flex; align-items: center; gap: 8px; }
.comment__name { font-size: var(--text-sm); }
.comment__stars { color: var(--amber); font-size: var(--text-xs); letter-spacing: 2px; }
.comment__content { margin: 4px 0; font-size: var(--text-sm); color: var(--ink-2); }
.comment__time { color: var(--ink-3); font-size: var(--text-xs); }
.comment__more {
  width: 100%;
  border: 1px dashed var(--line);
  background: none;
  color: var(--ink-2);
  border-radius: var(--radius-pill);
  padding: 8px 0;
  font-size: var(--text-xs);
  cursor: pointer;
}
.comment__empty { color: var(--ink-3); font-size: var(--text-sm); text-align: center; padding: 12px 0; }
.comment__rate { display: flex; align-items: center; gap: 4px; margin-bottom: 8px; }
.comment__rate span { font-size: var(--text-xs); color: var(--ink-3); margin-right: 4px; }
.comment__star {
  border: none;
  background: none;
  font-size: var(--text-lg);
  color: var(--line-strong, #d9d4cc);
  cursor: pointer;
  padding: 2px;
}
.comment__star.is-active { color: var(--amber); }
.comment__input { display: flex; gap: 8px; margin-top: var(--gap-sm); }
.comment__input input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--paper);
  font-size: var(--text-sm);
  outline: none;
}
.comment__input input:focus { border-color: var(--vermilion); }
.comment__input button {
  flex: none;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--vermilion);
  color: #fff;
  padding: 0 18px;
  font-size: var(--text-sm);
  cursor: pointer;
}
.comment__input button:disabled { opacity: 0.6; }
</style>
