import { describe, it, expect } from 'vitest'
import { pushUserMessage, reduceSSEEvent } from '../stores/chatMachine'

// 回归测试：兜底补卡（ensureShopCards）按 anchor（店名）拆分 text 块时，
// 必须吸收店名后的 markdown 闭合标记（** / _ / `），否则 `**店名**` 被劈成两半，
// 两个 text 块各自渲染时星号原样显示（md 渲染错乱）。
describe('chatMachine anchor 拆分与 markdown 闭合', () => {
  it('anchor 拆分不劈开加粗闭合标记', () => {
    let msgs = pushUserMessage([], '推荐快餐')
    msgs = reduceSSEEvent(msgs, {
      type: 'answer_chunk',
      content: '1. **牛约堡牛约汉堡(古运路店)** 评分4.6，人均84元'
    })
    msgs = reduceSSEEvent(msgs, {
      type: 'cards',
      cards: [{ id: 456, name: '牛约堡牛约汉堡(古运路店)', anchor: '牛约堡牛约汉堡(古运路店)' }]
    })
    // 拆分后 before 含完整加粗闭合（以 ** 结尾），after 以空格开头（不再有裸星号）
    expect(msgs[1].blocks[0].value).toBe('1. **牛约堡牛约汉堡(古运路店)**')
    expect(msgs[1].blocks[1].type).toBe('cards')
    expect(msgs[1].blocks[2].value).toBe(' 评分4.6，人均84元')
  })

  it('斜体/代码闭合标记同样吸收', () => {
    let msgs = pushUserMessage([], '推荐')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '_一家大饼_ 好评' })
    msgs = reduceSSEEvent(msgs, {
      type: 'cards',
      cards: [{ id: 369, name: '一家大饼', anchor: '一家大饼' }]
    })
    expect(msgs[1].blocks[0].value).toBe('_一家大饼_')
    expect(msgs[1].blocks[2].value).toBe(' 好评')
  })

  it('无 markdown 包裹时拆分点不变（店名后直接是内容）', () => {
    let msgs = pushUserMessage([], '推荐')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '推荐 一家大饼，人均46' })
    msgs = reduceSSEEvent(msgs, {
      type: 'cards',
      cards: [{ id: 369, name: '一家大饼', anchor: '一家大饼' }]
    })
    expect(msgs[1].blocks[0].value).toBe('推荐 一家大饼')
    expect(msgs[1].blocks[2].value).toBe('，人均46')
  })
})
