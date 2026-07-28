import { getAccessToken } from '../auth/session'

function socketUrl() {
  const configured = import.meta.env.VITE_WS_BASE_URL
  const apiBase = import.meta.env.VITE_API_BASE_URL
  const origin = configured || (apiBase?.startsWith('http') ? new URL(apiBase).origin : window.location.origin)
  const url = new URL('/ws', origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function buildFrame(command, headers = {}, body = '') {
  const lines = [command, ...Object.entries(headers).map(([key, value]) => `${key}:${value}`)]
  return `${lines.join('\n')}\n\n${body}\0`
}

function parseFrame(raw) {
  const [head, body = ''] = raw.split('\n\n')
  const [command, ...headerLines] = head.split('\n')
  const headers = Object.fromEntries(headerLines.filter(Boolean).map((line) => {
    const index = line.indexOf(':')
    if (index < 0) return [line, '']
    return [line.slice(0, index), line.slice(index + 1)]
  }))
  return { command, headers, body }
}

function parsePayload(body) {
  try {
    return JSON.parse(body || '{}')
  } catch {
    return {}
  }
}

export function createChatSocket({ roomId, onOpen, onEvent, onAck, onError, onClose }) {
  const accessToken = getAccessToken()
  const socket = new WebSocket(socketUrl())
  let connected = false

  function sendFrame(command, headers = {}, body = '') {
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(buildFrame(command, headers, body))
    }
  }

  socket.onopen = () => {
    sendFrame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '10000,10000',
      Authorization: `Bearer ${accessToken || ''}`,
    })
  }

  socket.onmessage = (event) => {
    String(event.data).split('\0').filter((chunk) => chunk.trim()).forEach((chunk) => {
      const frame = parseFrame(chunk)
      if (frame.command === 'CONNECTED') {
        connected = true
        sendFrame('SUBSCRIBE', { id: 'room-events', destination: `/sub/chat-rooms/${roomId}`, ack: 'auto' })
        sendFrame('SUBSCRIBE', { id: 'chat-acks', destination: '/user/queue/chat-acks', ack: 'auto' })
        sendFrame('SUBSCRIBE', { id: 'chat-errors', destination: '/user/queue/errors', ack: 'auto' })
        onOpen?.()
        return
      }
      if (frame.command === 'MESSAGE') {
        const payload = parsePayload(frame.body)
        const destination = frame.headers.destination || ''
        if (destination.includes('/queue/chat-acks')) onAck?.(payload)
        else if (destination.includes('/queue/errors')) onError?.(payload)
        else onEvent?.(payload)
        return
      }
      if (frame.command === 'ERROR') onError?.(parsePayload(frame.body))
    })
  }

  socket.onerror = () => onError?.({ error: { message: '채팅 연결 중 오류가 발생했습니다.' } })
  socket.onclose = () => {
    connected = false
    onClose?.()
  }

  return {
    get connected() {
      return connected
    },
    sendMessage(payload) {
      if (!connected) return
      sendFrame('SEND', {
        destination: `/pub/chat-rooms/${roomId}/messages`,
        'content-type': 'application/json',
      }, JSON.stringify(payload))
    },
    markRead(lastReadSeq) {
      if (!connected) return
      sendFrame('SEND', {
        destination: `/pub/chat-rooms/${roomId}/read`,
        'content-type': 'application/json',
      }, JSON.stringify({ lastReadSeq }))
    },
    close() {
      if (socket.readyState === WebSocket.OPEN) sendFrame('DISCONNECT')
      socket.close()
    },
  }
}
