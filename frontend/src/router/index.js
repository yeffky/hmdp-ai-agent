import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue'), meta: { tabbar: 1 } },
  { path: '/map', name: 'map', component: () => import('../views/MapView.vue'), meta: { tabbar: 2 } },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/shop-list', name: 'shop-list', component: () => import('../views/ShopList.vue') },
  { path: '/shop/:id', name: 'shop-detail', component: () => import('../views/ShopDetail.vue') },
  { path: '/blog/:id', name: 'blog-detail', component: () => import('../views/BlogDetail.vue') },
  { path: '/blog/edit', name: 'blog-edit', component: () => import('../views/BlogEdit.vue') },
  { path: '/me', name: 'me', component: () => import('../views/User/Info.vue'), meta: { tabbar: 4 } },
  { path: '/me/orders', name: 'my-orders', component: () => import('../views/MyOrders.vue') },
  { path: '/me/edit', name: 'edit-info', component: () => import('../views/User/EditInfo.vue') },
  { path: '/user/:id', name: 'other-info', component: () => import('../views/User/OtherInfo.vue') },
  { path: '/admin/kb', name: 'kb-admin', component: () => import('../views/admin/KbAdmin.vue') },
  { path: '/admin/queue', name: 'queue-admin', component: () => import('../views/admin/QueueAdmin.vue') },
  { path: '/rag-demo', name: 'rag-demo', component: () => import('../views/admin/RagDemo.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 生活优选` : '生活优选 · 小优帮你取号'
})

export default router
