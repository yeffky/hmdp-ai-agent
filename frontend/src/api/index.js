import http from './http'

// 后端统一 Result 包装，这里统一解开 data 字段
async function unwrap(promise) {
  const res = await promise
  return res ? res.data : res
}

export const userApi = {
  sendCode: (phone) => http.post(`/user/code?phone=${phone}`),
  login: (form) => unwrap(http.post('/user/login', form)),
  logout: () => http.post('/user/logout'),
  me: () => unwrap(http.get('/user/me')),
  info: (id) => unwrap(http.get(`/user/info/${id}`)),
  updateInfo: (body) => unwrap(http.put('/user/info', body)),
  user: (id) => unwrap(http.get(`/user/${id}`)),
  sign: () => http.post('/user/sign'),
  signCount: () => unwrap(http.get('/user/sign/count')),
  signToday: () => unwrap(http.get('/user/sign/today'))
}

export const shopApi = {
  detail: (id) => unwrap(http.get(`/shop/${id}`)),
  ofType: (params) => unwrap(http.get('/shop/of/type', { params })),
  ofName: (params) => unwrap(http.get('/shop/of/name', { params })),
  forMap: (params) => unwrap(http.get('/shop/map', { params })),
  allByTypes: async (typeIds = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]) => {
    const lists = await Promise.all(
      typeIds.map((typeId) =>
        http
          .get('/shop/of/type/no-geo', { params: { typeId, current: 1 } })
          .then((r) => (r && r.data) || [])
          .catch(() => [])
      )
    )
    const seen = new Set()
    const shops = []
    lists.forEach((list) =>
      list.forEach((s) => {
        if (!seen.has(s.id)) {
          seen.add(s.id)
          shops.push(s)
        }
      })
    )
    return shops.sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'zh'))
  }
}

export const shopTypeApi = {
  list: () => unwrap(http.get('/shop-type/list'))
}

// 城市/地区（首页定位选择器 + 地图初始化）
export const regionApi = {
  list: () => unwrap(http.get('/region/list'))
}

export const blogApi = {
  create: (blog) => unwrap(http.post('/blog', blog)),
  hot: (current) => unwrap(http.get('/blog/hot', { params: { current } })),
  ofMe: (current) => unwrap(http.get('/blog/of/me', { params: { current } })),
  ofShop: (shopId, current = 1) => unwrap(http.get(`/blog/of/shop/${shopId}`, { params: { current } })),
  detail: (id) => unwrap(http.get(`/blog/${id}`)),
  likes: (id) => unwrap(http.get(`/blog/likes/${id}`)),
  ofFollow: (lastId, offset) => unwrap(http.get('/blog/of/follow', { params: { lastId, offset } })),
  ofUser: (id, current) => unwrap(http.get('/blog/of/user', { params: { id, current } })),
  toggleLike: (id) => http.put(`/blog/like/${id}`)
}

export const followApi = {
  toggle: (id, isFollow) => http.put(`/follow/${id}/${isFollow}`),
  isFollowing: (id) => unwrap(http.get(`/follow/or/not/${id}`)),
  common: (id) => unwrap(http.get(`/follow/common/${id}`))
}

export const shopCommentApi = {
  list: (shopId, current = 1, size = 5) =>
    unwrap(http.get(`/shop-comment/list/${shopId}`, { params: { current, size } })),
  add: (body) => unwrap(http.post('/shop-comment', body))
}

export const blogCommentApi = {
  list: (blogId, current = 1, size = 5) =>
    unwrap(http.get(`/blog-comments/list/${blogId}`, { params: { current, size } })),
  add: (body) => unwrap(http.post('/blog-comments', body))
}

export const voucherApi = {
  list: (shopId) => unwrap(http.get(`/voucher/list/${shopId}`))
}

export const voucherOrderApi = {
  seckill: (id) => unwrap(http.post(`/voucher-order/seckill/${id}`)),
  seckillStatus: (voucherId) => unwrap(http.get(`/voucher-order/seckill/status/${voucherId}`)),
  buy: (voucherId) => unwrap(http.post(`/voucher-order/buy/${voucherId}`)),
  pay: (orderId, payType = 1) => unwrap(http.put(`/voucher-order/pay/${orderId}`, null, { params: { payType } })),
  cancel: (orderId) => unwrap(http.put(`/voucher-order/cancel/${orderId}`)),
  my: () => unwrap(http.get('/voucher-order/my'))
}

export const uploadApi = {
  blog: (file) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap(http.post('/upload/blog', form, { headers: { 'Content-Type': 'multipart/form-data' } }))
  }
}

export const queueApi = {
  take: (shopId, peopleCount, remark) =>
    unwrap(http.post('/queue-ticket/take', { shopId, peopleCount, remark })),
  my: () => unwrap(http.get('/queue-ticket/my')),
  shop: (shopId) => unwrap(http.get(`/queue-ticket/shop/${shopId}`)),
  cancel: (ticketId) => unwrap(http.put(`/queue-ticket/cancel/${ticketId}`)),
  call: (shopId) => unwrap(http.put(`/queue-ticket/call/${shopId}`))
}

export const chatApi = {
  rag: (sessionId, message) => unwrap(http.post('/chat/rag', { sessionId, message }, { timeout: 60000 })),
  react: (sessionId, message) => unwrap(http.post('/chat/react', { sessionId, message }, { timeout: 60000 })),
  history: (params) => unwrap(http.get('/chat/history', { params }))
}

export const kbApi = {
  stats: () => unwrap(http.get('/kb/stats')),
  documents: () => unwrap(http.get('/kb/documents')),
  chunks: (source, title) => unwrap(http.get('/kb/documents/chunks', { params: { source, title } })),
  deleteDocument: (source, title) => unwrap(http.delete('/kb/document', { params: { source, title } })),
  deleteSource: (source) => unwrap(http.delete(`/kb/source/${source}`)),
  ingest: (body) => unwrap(http.post('/kb/ingest', body)),
  previewSplit: (content) => unwrap(http.post('/kb/preview-split', { content })),
  search: (q, topK = 5) => unwrap(http.get('/kb/search', { params: { q, topK } })),
  health: () => unwrap(http.get('/kb/health'))
}

export const deadLetterApi = {
  list: (page, size) => unwrap(http.get('/dead-letter/list', { params: { page, size } })),
  redeliver: (id) => http.post(`/dead-letter/${id}/redeliver`),
  discard: (id) => http.post(`/dead-letter/${id}/discard`)
}
