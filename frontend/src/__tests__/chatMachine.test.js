import { describe, it, expect } from 'vitest'
import { pushUserMessage, pushAssistantMessage, reduceSSEEvent } from '../stores/chatMachine'

// assistant 消息结构：{ role, blocks: [{type:'text',value} | {type:'cards',cards}] }
function textOf(m) {
  const t = (m.blocks || []).find((b) => b.type === 'text')
  return t ? t.value : undefined
}

describe('chatMachine', () => {
  it('pushUserMessage 追加一条用户消息', () => {
    const msgs = pushUserMessage([], '你好')
    expect(msgs).toHaveLength(1)
    expect(msgs[0]).toMatchObject({ role: 'user', content: '你好' })
  })

  it('pushAssistantMessage 追加助手消息（blocks）', () => {
    const msgs = pushAssistantMessage([], '网络异常，请稍后再试')
    expect(msgs[0]).toMatchObject({ role: 'assistant' })
    expect(textOf(msgs[0])).toBe('网络异常，请稍后再试')
  })

  it('thinking 事件累积进调用链（toolchain），默认折叠', () => {
    let msgs = pushUserMessage([], '你好')
    msgs = reduceSSEEvent(msgs, { type: 'thinking', content: '正在分析' })
    expect(msgs).toHaveLength(2)
    expect(msgs[1]).toMatchObject({ role: 'toolchain' })
    expect(msgs[1].steps[0]).toMatchObject({ kind: 'thinking', content: '正在分析' })
  })

  it('tool 事件追加到同一条调用链，不新增消息', () => {
    let msgs = pushUserMessage([], '取号')
    msgs = reduceSSEEvent(msgs, { type: 'thinking', content: '分析中' })
    msgs = reduceSSEEvent(msgs, { type: 'tool', content: '调用取号工具', toolName: 'takeQueueNumber' })
    expect(msgs).toHaveLength(2)
    expect(msgs[1].role).toBe('toolchain')
    expect(msgs[1].steps).toHaveLength(2)
    expect(msgs[1].steps[1]).toMatchObject({ kind: 'tool', toolName: 'takeQueueNumber' })
  })

  it('tool 事件内联 cards 时，卡片挂在调用链 step（折叠态缩略图数据源）', () => {
    let msgs = pushUserMessage([], '推荐火锅')
    msgs = reduceSSEEvent(msgs, {
      type: 'tool',
      toolName: 'searchShops',
      content: '调用 searchShops',
      cards: [{ id: 1, name: '蜀大侠', image: '/a.png', reason: '评分最高（4.8）' }]
    })
    expect(msgs[1].role).toBe('toolchain')
    expect(msgs[1].steps[0].cards).toEqual([
      { id: 1, name: '蜀大侠', image: '/a.png', reason: '评分最高（4.8）' }
    ])
  })

  it('answer_chunk 拼接成流式消息（text block）', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '你' })
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '好' })
    expect(msgs[1]).toMatchObject({ role: 'assistant_streaming' })
    expect(textOf(msgs[1])).toBe('你好')
  })

  it('answer_chunk 短 chunk（标点/数字/单字）不吞字', () => {
    let msgs = pushUserMessage([], 'hi')
    // 模拟逐 token 流式：标点、数字、单字作为独立 chunk（此前 includes 误判会吞掉它们）
    const tokens = ['推荐', '蜜', '雪', '冰', '城', '(', '东', '街', '口', '店', ')', ' ', '评分', '4', '.', '5', '，', '人均', '1', '0', '7', '元']
    for (const t of tokens) {
      msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: t })
    }
    expect(textOf(msgs[1])).toBe('推荐蜜雪冰城(东街口店) 评分4.5，人均107元')
  })

  it('answer_chunk 直接拼接：内容重复不吞字（去重交给发送端）', () => {
    let msgs = pushUserMessage([], '推荐火锅')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '推荐朱富贵火锅，评分最高' })
    // 新 chunk 虽含已显示短语，前端不做内容级去重 → 原样追加（后端 ResumeDedupe 保证增量）
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '评分最高，适合聚餐' })
    expect(textOf(msgs[1])).toBe('推荐朱富贵火锅，评分最高评分最高，适合聚餐')
  })

  it('SSE Last-Event-ID 防护：id 未前进的 chunk 忽略', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '推荐朱富贵火锅', _eventId: '5' })
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '，评分高', _eventId: '8' })
    // 后端异常重发同一 id → 忽略（按 id，非按内容）
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '，评分高', _eventId: '8' })
    expect(textOf(msgs[1])).toBe('推荐朱富贵火锅，评分高')
  })

  it('answer 将流式消息转为正式 assistant', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '你好' })
    msgs = reduceSSEEvent(msgs, { type: 'answer', content: '你好' })
    expect(msgs[1]).toMatchObject({ role: 'assistant' })
    expect(textOf(msgs[1])).toBe('你好')
  })

  it('预设回答（无流式过程）直接追加 assistant', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'answer', content: '预设回答' })
    expect(msgs[1]).toMatchObject({ role: 'assistant' })
    expect(textOf(msgs[1])).toBe('预设回答')
  })

  it('cards 事件追加 cards block，穿插在文本流（对应商家位置）', () => {
    let msgs = pushUserMessage([], '推荐火锅')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '推荐朱富贵火锅' })
    msgs = reduceSSEEvent(msgs, { type: 'cards', cards: [{ id: 616, name: '朱富贵火锅' }] })
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '，评分高。' })
    expect(msgs[1].role).toBe('assistant_streaming')
    expect(msgs[1].blocks.map((b) => b.type)).toEqual(['text', 'cards', 'text'])
    expect(msgs[1].blocks[0].value).toBe('推荐朱富贵火锅')
    expect(msgs[1].blocks[1].cards).toEqual([{ id: 616, name: '朱富贵火锅' }])
    expect(msgs[1].blocks[2].value).toBe('，评分高。')
  })


  it('兜底补卡带 anchor：单个 text 块内按店名拆分插入，不堆积在末尾', () => {
    // 后端 ensureShopCards 补发时卡片带 anchor（命中的店名）；流式文本是单个 text 块，
    // appendCards 应在块内按 anchor 拆分，把卡片插到店名之后
    let msgs = pushUserMessage([], '推荐快餐')
    msgs = reduceSSEEvent(msgs, {
      type: 'answer_chunk',
      content: '为您推荐：1. 牛约堡牛约汉堡，评分4.6 2. 一家大饼，人均46'
    })
    msgs = reduceSSEEvent(msgs, {
      type: 'cards',
      cards: [
        { id: 456, name: '牛约堡牛约汉堡(古运路店)', anchor: '牛约堡牛约汉堡' },
        { id: 369, name: '一家大饼(荷叶园店)', anchor: '一家大饼' }
      ]
    })
    msgs = reduceSSEEvent(msgs, { type: 'answer', content: '为您推荐：1. 牛约堡牛约汉堡，评分4.6 2. 一家大饼，人均46' })
    expect(msgs[1].role).toBe('assistant')
    // 两处 anchor 各自拆分 → text/cards/text/cards/text
    expect(msgs[1].blocks.map((b) => b.type)).toEqual(['text', 'cards', 'text', 'cards', 'text'])
    expect(msgs[1].blocks[1].cards[0]).toMatchObject({ id: 456 })
    expect(msgs[1].blocks[3].cards[0]).toMatchObject({ id: 369 })
    // anchor 已剥离，不残留到渲染数据
    expect(msgs[1].blocks[1].cards[0].anchor).toBeUndefined()
    // 文本按锚点拆成三段，顺序拼接不回退
    expect(msgs[1].blocks[0].value).toBe('为您推荐：1. 牛约堡牛约汉堡')
    expect(msgs[1].blocks[2].value).toBe('，评分4.6 2. 一家大饼')
    expect(msgs[1].blocks[4].value).toBe('，人均46')
  })

  it('兜底补卡 anchor 未命中时回退追加末尾', () => {
    let msgs = pushUserMessage([], '推荐快餐')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '没有找到合适的店' })
    msgs = reduceSSEEvent(msgs, { type: 'cards', cards: [{ id: 456, name: '牛约堡牛约汉堡', anchor: '牛约堡' }] })
    expect(msgs[1].blocks.map((b) => b.type)).toEqual(['text', 'cards'])
  })

  it('answer 不兜底追加卡片：无占位符 cards 事件则不渲染卡片', () => {
    let msgs = pushUserMessage([], '推荐火锅')
    msgs = reduceSSEEvent(msgs, {
      type: 'answer',
      content: '为您推荐蜀大侠' // answer 事件本身不带 cards → 只有 text
    })
    expect(msgs[1].role).toBe('assistant')
    expect(msgs[1].blocks.map((b) => b.type)).toEqual(['text'])
    expect(msgs[1].blocks[0].value).toBe('为您推荐蜀大侠')
  })

  it('answer 时若 cards 堆在末尾，按店名引用位置前插穿插', () => {
    let msgs = pushUserMessage([], '推荐火锅')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '推荐朱富贵火锅，评分最高。还有高兴壹锅也不错。' })
    // LLM 把 showCards 堆在末尾 → 两张卡片连续 append 在 blocks 尾部
    msgs = reduceSSEEvent(msgs, { type: 'cards', cards: [{ id: 630, name: '朱富贵火锅' }] })
    msgs = reduceSSEEvent(msgs, { type: 'cards', cards: [{ id: 579, name: '高兴壹锅' }] })
    msgs = reduceSSEEvent(msgs, { type: 'answer', content: '推荐朱富贵火锅，评分最高。还有高兴壹锅也不错。' })
    // intersperseCards 按店名出现位置把卡片插到对应店名后
    expect(msgs[1].role).toBe('assistant')
    expect(msgs[1].blocks.map((b) => b.type)).toEqual(['text', 'cards', 'cards'])
    expect(msgs[1].blocks[1].cards[0]).toMatchObject({ id: 630 })
    expect(msgs[1].blocks[2].cards[0]).toMatchObject({ id: 579 })
  })

  it('answer 时卡片已穿插（流式中逐张 append），不做兜底重排', () => {
    let msgs = pushUserMessage([], '推荐火锅')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '推荐朱富贵火锅' })
    msgs = reduceSSEEvent(msgs, { type: 'cards', cards: [{ id: 630, name: '朱富贵火锅' }] })
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '，评分高。' })
    msgs = reduceSSEEvent(msgs, { type: 'answer', content: '推荐朱富贵火锅，评分高。' })
    // 卡片已在中间，末尾无 cards 块 → 保持原样
    expect(msgs[1].blocks.map((b) => b.type)).toEqual(['text', 'cards', 'text'])
  })

  it('done / 未知事件不改变消息', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'done' })
    msgs = reduceSSEEvent(msgs, { type: 'unknown', content: 'x' })
    expect(msgs).toHaveLength(1)
  })

  it('actions 事件追加操作按钮消息', () => {
    let msgs = []
    msgs = reduceSSEEvent(msgs, {
      type: 'actions',
      hint: '即将执行取号',
      actions: [{ label: '确认', value: '确认' }, { label: '取消', value: '取消' }]
    })
    expect(msgs).toHaveLength(1)
    expect(msgs[0].role).toBe('actions')
    expect(msgs[0].actions).toHaveLength(2)
    expect(msgs[0].actions[0]).toMatchObject({ label: '确认', value: '确认' })
  })
})
