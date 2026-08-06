// AI 聊天消息流的纯函数归约器 —— 便于单测，无 DOM 依赖。
// 消息角色：user / assistant / assistant_streaming / thinking / tooling

let seq = 0
function uid() {
  seq += 1
  return `m${Date.now().toString(36)}${seq}`
}

export function pushUserMessage(messages, content) {
  return [...messages, { id: uid(), role: 'user', content }]
}

export function pushAssistantMessage(messages, content) {
  return [...messages, { id: uid(), role: 'assistant', content }]
}

function withoutStreaming(messages) {
  return messages.filter((m) => m.role !== 'assistant_streaming')
}

// thinking / tooling 事件：替换掉上一条过程消息，否则追加
function replaceOrPush(messages, msg) {
  const list = withoutStreaming(messages)
  const last = list[list.length - 1]
  if (last && (last.role === 'thinking' || last.role === 'tooling')) {
    return [...list.slice(0, -1), { ...msg, id: last.id }]
  }
  return [...list, { ...msg, id: uid() }]
}

// answer_chunk：追加到流式消息，没有则新建
function appendChunk(messages, chunk) {
  const list = messages.slice()
  const last = list[list.length - 1]
  if (last && last.role === 'assistant_streaming') {
    list[list.length - 1] = { ...last, content: last.content + chunk }
    return list
  }
  return [...list, { id: uid(), role: 'assistant_streaming', content: chunk }]
}

// answer：流式消息转正式；若为预设回答（无流式过程）则直接追加
function finalizeAnswer(messages, content) {
  const list = messages.slice()
  const last = list[list.length - 1]
  if (last && last.role === 'assistant_streaming') {
    list[list.length - 1] = { ...last, role: 'assistant' }
    return list
  }
  if (content) {
    return [...list, { id: uid(), role: 'assistant', content }]
  }
  return list
}

export function reduceSSEEvent(messages, evt) {
  switch (evt && evt.type) {
    case 'thinking':
      return replaceOrPush(messages, {
        role: 'thinking',
        content: evt.content || '小优正在思考…',
        plan: evt.plan
      })
    case 'tool':
      return replaceOrPush(messages, {
        role: 'tooling',
        content: evt.content || '正在调用工具…',
        toolName: evt.toolName,
        toolResult: evt.toolResult
      })
    case 'retry':
      return replaceOrPush(messages, {
        role: 'tooling',
        content: evt.content || '正在重试…',
        toolName: evt.tool
      })
    case 'answer_chunk':
      return appendChunk(messages, evt.content || '')
    case 'answer':
      return finalizeAnswer(messages, evt.content)
    case 'done':
    default:
      return messages
  }
}

export default { pushUserMessage, pushAssistantMessage, reduceSSEEvent }
