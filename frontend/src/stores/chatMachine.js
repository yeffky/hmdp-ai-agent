// AI 聊天消息流的纯函数归约器 —— 便于单测，无 DOM 依赖。
// 消息角色：user / assistant / assistant_streaming / toolchain / actions
// assistant 消息用 blocks 分段：[{type:'text',value} | {type:'cards',cards}]，
// 卡片随独立 cards 事件按到达顺序穿插进文本流（对应商家位置渲染）。

let seq = 0
function uid() {
  seq += 1
  return `m${Date.now().toString(36)}${seq}`
}
function textBlock(value) {
  return { type: 'text', value }
}
function cardsBlock(cards) {
  return { type: 'cards', cards }
}

export function pushUserMessage(messages, content) {
  return [...messages, { id: uid(), role: 'user', content }]
}

export function pushAssistantMessage(messages, content) {
  return [...messages, { id: uid(), role: 'assistant', blocks: [textBlock(content)] }]
}

function withoutStreaming(messages) {
  return messages.filter((m) => m.role !== 'assistant_streaming')
}

// thinking / tool 事件 → 累积进同一条「调用链」折叠消息（默认收起，展开看过程，折叠态展示卡片缩略图）
function pushStep(messages, step) {
  const list = withoutStreaming(messages)
  const last = list[list.length - 1]
  if (last && last.role === 'toolchain') {
    return [...list.slice(0, -1), { ...last, steps: [...last.steps, step] }]
  }
  return [...list, { id: uid(), role: 'toolchain', steps: [step] }]
}

// answer_chunk：追加到流式消息的最后一个 text block
// 不重复由发送端（后端 ResumeDedupe + 增量发送 + SSE 事件 id）保证，前端不做内容级去重；
// 这里仅按 SSE Last-Event-ID 语义做异常兜底：id 未前进（重复/倒退）的 chunk 忽略。
function appendChunk(messages, chunk, eventId) {
  if (!chunk) return messages
  const list = messages.slice()
  const last = list[list.length - 1]
  if (last && last.role === 'assistant_streaming') {
    if (eventId != null && last._eventId != null && Number(eventId) <= Number(last._eventId)) {
      return messages // 后端异常重发已发送过的 chunk → 忽略
    }
    const blocks = last.blocks && last.blocks.length ? last.blocks.slice() : [textBlock('')]
    const lastBlock = blocks[blocks.length - 1]
    if (lastBlock.type === 'text') blocks[blocks.length - 1] = textBlock(lastBlock.value + chunk)
    else blocks.push(textBlock(chunk))
    list[list.length - 1] = { ...last, blocks, _eventId: eventId != null ? Number(eventId) : last._eventId }
    return list
  }
  return [...list, { id: uid(), role: 'assistant_streaming', blocks: [textBlock(chunk)], _eventId: eventId != null ? Number(eventId) : undefined }]
}

// cards 事件（独立 chunk）：追加 cards block 到当前 assistant 消息，穿插在文本流中。
// 兜底补发的卡片带 anchor（后端按店名匹配命中的店名串）：在最后一个 text 块内按 anchor 定位，
// 拆分 text 块把卡片插到店名之后（保证「在引用处渲染」，避免堆积在末尾）。
function appendCards(messages, cards) {
  if (!cards || !cards.length) return messages
  const list = messages.slice()
  const last = list[list.length - 1]
  if (last && (last.role === 'assistant_streaming' || last.role === 'assistant')) {
    let blocks = (last.blocks || []).slice()
    let changed = false
    for (const card of cards) {
      const anchor = card && card.anchor
      if (!anchor) continue
      let lastTextIdx = -1
      for (let i = blocks.length - 1; i >= 0; i--) {
        if (blocks[i].type === 'text') { lastTextIdx = i; break }
      }
      if (lastTextIdx < 0) break
      const t = blocks[lastTextIdx]
      const pos = typeof t.value === 'string' ? t.value.indexOf(anchor) : -1
      if (pos < 0) continue
      // Split point: absorb trailing markdown closers (** bold / _ italic / ` code) into before,
      // so a "**name**" is not cut in half - otherwise each text block renders literal asterisks.
      let cut = pos + anchor.length
      while (cut < t.value.length) {
        const ch = t.value[cut]
        if (ch === '*' || ch === '_' || ch === '`') cut++
        else break
      }
      const before = t.value.slice(0, cut)
      const after = t.value.slice(cut)
      const cleanCard = { ...card }
      delete cleanCard.anchor
      const newBlocks = []
      for (let i = 0; i < blocks.length; i++) {
        if (i === lastTextIdx) {
          newBlocks.push(textBlock(before))
          newBlocks.push({ type: 'cards', cards: [cleanCard] })
          if (after) newBlocks.push(textBlock(after))
        } else {
          newBlocks.push(blocks[i])
        }
      }
      blocks = newBlocks
      changed = true
    }
    if (changed) {
      list[list.length - 1] = { ...last, blocks }
      return list
    }
    list[list.length - 1] = { ...last, blocks: [...blocks, cardsBlock(cards)] }
    return list
  }
  return [...list, { id: uid(), role: 'assistant', blocks: [cardsBlock(cards)] }]
}

function intersperseCards(blocks) {
  if (!Array.isArray(blocks) || !blocks.length) return blocks
  // 定位末尾连续的 cards 块
  let tailStart = blocks.length
  while (tailStart > 0 && blocks[tailStart - 1].type === 'cards') tailStart--
  if (tailStart === blocks.length) return blocks // 卡片已穿插（流式时逐张 append），无需重排
  const allCards = blocks.slice(tailStart).flatMap((b) => b.cards || [])
  const placed = blocks.slice(0, tailStart)
  // 每张卡定位「店名引用位置」：优先精确店名，退而求括号前主干；找不到则归末尾
  const located = allCards.map((card) => {
    const name = card && card.name
    const key = (name || '').split('(')[0]
    for (let i = 0; i < placed.length; i++) {
      const t = placed[i].type === 'text' ? placed[i].value : ''
      let pos = -1
      if (name && t.includes(name)) pos = t.indexOf(name)
      else if (key && key.length >= 3) pos = t.indexOf(key)
      if (pos >= 0) return { card, textIdx: i, offset: pos }
    }
    return { card, textIdx: -1, offset: Infinity }
  })
  // 同一 text 块内按店名出现位置升序；从后往前插入避免下标错位
  located.sort((a, b) => a.textIdx - b.textIdx || a.offset - b.offset)
  for (let k = located.length - 1; k >= 0; k--) {
    const { card, textIdx } = located[k]
    if (textIdx >= 0) placed.splice(textIdx + 1, 0, { type: 'cards', cards: [card] })
    else placed.push({ type: 'cards', cards: [card] })
  }
  return placed
}

// answer：流式消息转正式。卡片穿插由 cards 事件（showCards 工具调用）驱动；
// 若 LLM 把卡片堆在末尾，intersperseCards 按店名引用位置前插兜底
function finalizeAnswer(messages, content) {
  const list = messages.slice()
  const last = list[list.length - 1]
  if (last && last.role === 'assistant_streaming') {
    let blocks = last.blocks || []
    blocks = intersperseCards(blocks)
    list[list.length - 1] = { ...last, role: 'assistant', blocks }
    return list
  }
  if (content) {
    return [...list, { id: uid(), role: 'assistant', blocks: [textBlock(content)] }]
  }
  return list
}

export function reduceSSEEvent(messages, evt) {
  switch (evt && evt.type) {
    case 'thinking':
      // 思考步骤（含计划）→ 进调用链
      return pushStep(messages, {
        kind: 'thinking',
        content: evt.content || '思考中…',
        plan: evt.plan
      })
    case 'tool':
      // 工具调用（含结构化卡片，供折叠态缩略图）→ 进调用链
      return pushStep(messages, {
        kind: 'tool',
        content: evt.content || '正在调用工具…',
        toolName: evt.toolName,
        toolResult: evt.toolResult,
        cards: Array.isArray(evt.cards) ? evt.cards : []
      })
    case 'retry':
      return pushStep(messages, {
        kind: 'tool',
        content: evt.content || '正在重试…',
        toolName: evt.tool,
        toolResult: evt.toolResult
      })
    case 'answer_chunk':
      return appendChunk(messages, evt.content || '', evt._eventId)
    case 'cards':
      // 独立卡片 chunk（LLM 输出 [[CARD:id]]，后端剥离转事件）：穿插到文本流
      return appendCards(messages, evt.cards)
    case 'answer':
      return finalizeAnswer(messages, evt.content || '')
    case 'actions':
      // 操作按钮（写操作确认/选择），点击即发送；可能附带确认卡片（如取号：店名+人数）
      return [...messages, {
        id: uid(),
        role: 'actions',
        hint: evt.hint || '',
        actions: Array.isArray(evt.actions) ? evt.actions : [],
        card: evt.card || null
      }]
    case 'done':
      return messages
    case 'error':
      // 后端 error 事件（连接池/服务异常）→ 追加一条错误提示，避免静默无响应
      return [...messages, {
        id: uid(),
        role: 'assistant',
        error: true,
        blocks: [{ type: 'text', value: evt.message || '服务繁忙，请稍后重试。' }]
      }]
    default:
      return messages
  }
}

export default { pushUserMessage, pushAssistantMessage, reduceSSEEvent }
