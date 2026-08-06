import { describe, it, expect } from 'vitest'
import { extractSSEEvents } from '../utils/sse'

describe('extractSSEEvents', () => {
  it('解析多条 data: 事件', () => {
    const { events, remainder } = extractSSEEvents(
      'data: {"type":"thinking","content":"x"}\ndata: {"type":"answer","content":"y"}\n'
    )
    expect(events).toHaveLength(2)
    expect(events[0].type).toBe('thinking')
    expect(events[1].type).toBe('answer')
    expect(remainder).toBe('')
  })

  it('跨块拼接：末行不完整时留待下次', () => {
    const first = extractSSEEvents('data: {"type":"answer_chunk","content":"')
    expect(first.events).toHaveLength(0)
    expect(first.remainder).not.toBe('')

    const second = extractSSEEvents('你"}\n', first.remainder)
    expect(second.events).toHaveLength(1)
    expect(second.events[0]).toMatchObject({ type: 'answer_chunk', content: '你' })
  })

  it('忽略心跳行、空对象与无法解析的行', () => {
    const { events } = extractSSEEvents(':connected\ndata: {}\ndata: not-json\ndata: {"type":"done"}\n')
    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('done')
  })

  it('普通注释/空行不产生事件', () => {
    const { events } = extractSSEEvents('   \n\n')
    expect(events).toHaveLength(0)
  })
})
