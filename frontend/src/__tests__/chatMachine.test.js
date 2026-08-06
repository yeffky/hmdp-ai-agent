import { describe, it, expect } from 'vitest'
import { pushUserMessage, pushAssistantMessage, reduceSSEEvent } from '../stores/chatMachine'

describe('chatMachine', () => {
  it('pushUserMessage 追加一条用户消息', () => {
    const msgs = pushUserMessage([], '你好')
    expect(msgs).toHaveLength(1)
    expect(msgs[0]).toMatchObject({ role: 'user', content: '你好' })
  })

  it('pushAssistantMessage 追加助手消息', () => {
    const msgs = pushAssistantMessage([], '网络异常，请稍后再试')
    expect(msgs[0]).toMatchObject({ role: 'assistant', content: '网络异常，请稍后再试' })
  })

  it('thinking 事件追加过程消息', () => {
    let msgs = pushUserMessage([], '你好')
    msgs = reduceSSEEvent(msgs, { type: 'thinking', content: '正在分析' })
    expect(msgs[1]).toMatchObject({ role: 'thinking', content: '正在分析' })
  })

  it('tool 事件覆盖上一条 thinking，不堆积', () => {
    let msgs = pushUserMessage([], '取号')
    msgs = reduceSSEEvent(msgs, { type: 'thinking', content: '分析中' })
    msgs = reduceSSEEvent(msgs, { type: 'tool', content: '调用取号工具', toolName: 'QueueTicketTool' })
    expect(msgs).toHaveLength(2)
    expect(msgs[1]).toMatchObject({ role: 'tooling', toolName: 'QueueTicketTool' })
  })

  it('retry 事件同样覆盖过程消息', () => {
    let msgs = pushUserMessage([], '查询')
    msgs = reduceSSEEvent(msgs, { type: 'tool', content: '调用查询' })
    msgs = reduceSSEEvent(msgs, { type: 'retry', content: '重试中', tool: 'OrderQueryTool' })
    expect(msgs).toHaveLength(2)
    expect(msgs[1]).toMatchObject({ role: 'tooling', toolName: 'OrderQueryTool' })
  })

  it('answer_chunk 拼接成流式消息', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '你' })
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '好' })
    expect(msgs[1]).toMatchObject({ role: 'assistant_streaming', content: '你好' })
  })

  it('answer 将流式消息转为正式 assistant', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'answer_chunk', content: '你好' })
    msgs = reduceSSEEvent(msgs, { type: 'answer', content: '你好' })
    expect(msgs[1]).toMatchObject({ role: 'assistant', content: '你好' })
  })

  it('预设回答（无流式过程）直接追加 assistant', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'answer', content: '预设回答' })
    expect(msgs[1]).toMatchObject({ role: 'assistant', content: '预设回答' })
  })

  it('done / 未知事件不改变消息', () => {
    let msgs = pushUserMessage([], 'hi')
    msgs = reduceSSEEvent(msgs, { type: 'done' })
    msgs = reduceSSEEvent(msgs, { type: 'unknown', content: 'x' })
    expect(msgs).toHaveLength(1)
  })
})
