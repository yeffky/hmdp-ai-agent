// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ShopDetail from '../views/ShopDetail.vue'
import { shopApi, voucherApi, voucherOrderApi } from '../api'

vi.mock('../api', () => ({
  shopApi: { detail: vi.fn() },
  voucherApi: { list: vi.fn() },
  voucherOrderApi: { seckillStatus: vi.fn(), buy: vi.fn() },
  queueApi: { my: vi.fn(), shop: vi.fn() },
  blogApi: { ofShop: vi.fn() },
  shopCommentApi: { list: vi.fn(), add: vi.fn() }
}))

vi.mock('../stores/user', () => ({
  useUserStore: () => ({ isLoggedIn: true })
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ push: vi.fn() })
}))

const shop = {
  id: 1,
  name: '测试店',
  queueEnabled: 0,
  images: 'a.png',
  score: 45,
  comments: 3,
  openHours: '10:00-22:00',
  address: '鼓楼',
  avgPrice: 80
}

const makeVoucher = (overrides) => ({
  id: 10,
  type: 0,
  title: '双人餐',
  subTitle: '',
  payValue: 9900,
  actualValue: 12800,
  image: '',
  ...overrides
})

async function mountWith(vouchers, statusMap = {}) {
  shopApi.detail.mockResolvedValue({ ...shop })
  voucherApi.list.mockResolvedValue(vouchers)
  voucherOrderApi.seckillStatus.mockImplementation(
    (id) => Promise.resolve(!!statusMap[id])
  )
  const wrapper = mount(ShopDetail, {
    global: { stubs: { AppHeader: true, AppIcon: true, PayDialog: true } }
  })
  await flushPromises()
  return wrapper
}

describe('ShopDetail 团购已购置灰', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('普通券已购买 → 按钮「已购买」+ is-off + disabled', async () => {
    const wrapper = await mountWith([makeVoucher({ id: 11 })], { 11: true })
    const btn = wrapper.get('.deal__btn')
    expect(btn.text()).toBe('已购买')
    expect(btn.classes()).toContain('is-off')
    expect(btn.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('普通券未购买 → 按钮「去购买」且可用', async () => {
    const wrapper = await mountWith([makeVoucher({ id: 12 })], { 12: false })
    const btn = wrapper.get('.deal__btn')
    expect(btn.text()).toBe('去购买')
    expect(btn.classes()).not.toContain('is-off')
    expect(btn.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('秒杀券已购买 → 置灰（回归：原有秒杀逻辑不破坏）', async () => {
    const seckill = makeVoucher({
      id: 13,
      type: 1,
      stock: 10,
      beginTime: new Date(Date.now() - 3600e3).toISOString(),
      endTime: new Date(Date.now() + 3600e3).toISOString()
    })
    const wrapper = await mountWith([seckill], { 13: true })
    const btn = wrapper.get('.deal__btn')
    expect(btn.text()).toBe('已购买')
    expect(btn.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('同一店铺混合：已购普通券置灰、未购秒杀券正常', async () => {
    const vouchers = [
      makeVoucher({ id: 21 }),
      makeVoucher({
        id: 22,
        type: 1,
        stock: 5,
        beginTime: new Date(Date.now() - 3600e3).toISOString(),
        endTime: new Date(Date.now() + 3600e3).toISOString()
      })
    ]
    const wrapper = await mountWith(vouchers, { 21: true, 22: false })
    const btns = wrapper.findAll('.deal__btn')
    expect(btns).toHaveLength(2)
    expect(btns[0].text()).toBe('已购买')
    expect(btns[1].text()).toContain('限时秒杀')
    expect(btns[1].attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
})
