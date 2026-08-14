const AI_BASE_URL = (import.meta.env.VITE_AI_BASE_URL || '/ai-api').replace(/\/$/, '')

const request = async (path, options = {}) => {
  const response = await fetch(`${AI_BASE_URL}${path}`, options)
  if (!response.ok) {
    let message = `AI 服务请求失败（${response.status}）`
    try {
      const error = await response.json()
      message = error.message || error.detail || message
    } catch {
      // 非 JSON 错误响应沿用状态码提示
    }
    throw new Error(message)
  }
  return response
}

export const ragChat = async (payload) => {
  const response = await request('/api/rag/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  return response.json()
}

export const ragChatStream = async (payload, { signal, onEvidence, onToken } = {}) => {
  const response = await request('/api/rag/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(payload),
    signal
  })

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (block) => {
    if (!block.trim()) return
    let eventName = 'message'
    const dataLines = []

    block.split(/\r?\n/).forEach((line) => {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    })

    if (!dataLines.length) return
    const rawData = dataLines.join('\n')
    let data = rawData
    try {
      data = JSON.parse(rawData)
    } catch {
      // token 也允许后端直接返回纯文本
    }

    if (eventName === 'evidence') onEvidence?.(Array.isArray(data) ? data : [])
    if (eventName === 'token') onToken?.(typeof data === 'string' ? data : data.content || '')
  }

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    events.forEach(dispatch)
    if (done) break
  }

  dispatch(buffer)
}

export const diagnosePlant = async ({ image, userId, plantInstanceId, canonicalPlantId, query }) => {
  const formData = new FormData()
  formData.append('image', image)
  formData.append('userId', userId)
  formData.append('plantInstanceId', plantInstanceId)
  if (canonicalPlantId) formData.append('canonicalPlantId', canonicalPlantId)
  if (query) formData.append('query', query)

  const response = await request('/api/rag/diagnose', {
    method: 'POST',
    body: formData
  })
  return response.json()
}

export const searchEvidence = async ({ query, canonicalPlantId }) => {
  const params = new URLSearchParams({ q: query })
  if (canonicalPlantId) params.set('canonicalPlantId', canonicalPlantId)
  const response = await request(`/api/search?${params.toString()}`)
  return response.json()
}
