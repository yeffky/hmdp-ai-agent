// 纯函数：把一块 SSE 文本切成事件。返回 { events, remainder }
// remainder 是可能被截断的未完成行，留给下次拼接。
export function extractSSEEvents(chunk, buffer = '') {
  const acc = buffer + chunk
  const lines = acc.split('\n')
  const remainder = lines.pop() || ''
  const events = []
  for (const line of lines) {
    const t = line.trim()
    if (!t.startsWith('data:')) continue
    const jsonStr = t.slice(5).trim()
    if (!jsonStr || jsonStr === '{}') continue
    try {
      events.push(JSON.parse(jsonStr))
    } catch {
      /* 忽略无法解析的片段 */
    }
  }
  return { events, remainder }
}

// 解析 /chat/react/stream 的 SSE 事件
// onEvent(data) 在每条 data: 事件解析后调用；HTTP 401 抛 {code:401}
export async function streamChat({ message, token, onEvent, signal }) {
  const res = await fetch('/chat/react/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', authorization: token || '' },
    body: JSON.stringify({ message }),
    signal
  })
  if (res.status === 401) {
    const err = new Error('请先登录')
    err.code = 401
    throw err
  }
  if (!res.ok) {
    const err = new Error(`请求失败(${res.status})`)
    err.code = res.status
    throw err
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const { events, remainder } = extractSSEEvents(buffer, '')
    buffer = remainder
    events.forEach((data) => onEvent(data))
  }
}

export default streamChat
