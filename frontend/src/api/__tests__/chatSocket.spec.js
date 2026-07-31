import { afterEach, describe, expect, it, vi } from 'vitest'
import { setAuthSession } from '../../auth/session'
import { createChatListSocket, createChatSocket } from '../chatSocket'

class MockWebSocket {
  static OPEN = 1

  constructor(url) {
    this.url = url
    this.readyState = MockWebSocket.OPEN
    this.sent = []
    MockWebSocket.instances.push(this)
  }

  send(frame) {
    this.sent.push(frame)
  }

  close() {
    this.readyState = 3
    this.onclose?.()
  }
}

MockWebSocket.instances = []

describe('chatSocket', () => {
  afterEach(() => {
    MockWebSocket.instances = []
    setAuthSession(null)
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('connects to /ws and subscribes to room, ack, and error destinations', () => {
    vi.stubEnv('VITE_WS_BASE_URL', 'https://api.example.com')
    vi.stubGlobal('WebSocket', MockWebSocket)
    setAuthSession({ accessToken: '<access-value>' })
    const onOpen = vi.fn()

    createChatSocket({ roomId: 7, onOpen })
    const socket = MockWebSocket.instances[0]

    expect(socket.url).toBe('wss://api.example.com/ws')
    socket.onopen()
    expect(socket.sent[0]).toContain('CONNECT')
    expect(socket.sent[0]).toContain('Authorization:Bearer <access-value>')

    socket.onmessage({ data: 'CONNECTED\nversion:1.2\n\n\0' })

    expect(onOpen).toHaveBeenCalled()
    expect(socket.sent).toContain('SUBSCRIBE\nid:room-events\ndestination:/sub/chat-rooms/7\nack:auto\n\n\0')
    expect(socket.sent).toContain('SUBSCRIBE\nid:chat-acks\ndestination:/user/queue/chat-acks\nack:auto\n\n\0')
    expect(socket.sent).toContain('SUBSCRIBE\nid:chat-errors\ndestination:/user/queue/errors\nack:auto\n\n\0')
  })

  it('subscribes to all chat rooms with one connection for realtime list updates', () => {
    vi.stubGlobal('WebSocket', MockWebSocket)
    const onEvent = vi.fn()

    createChatListSocket({ roomIds: [7, 8, 7], onEvent })
    const socket = MockWebSocket.instances[0]
    socket.onopen()
    socket.onmessage({ data: 'CONNECTED\n\n\0' })

    expect(socket.sent).toContain('SUBSCRIBE\nid:room-events-7\ndestination:/sub/chat-rooms/7\nack:auto\n\n\0')
    expect(socket.sent).toContain('SUBSCRIBE\nid:room-events-8\ndestination:/sub/chat-rooms/8\nack:auto\n\n\0')
    expect(socket.sent.some((frame) => frame.includes('/user/queue/chat-acks'))).toBe(false)

    socket.onmessage({
      data: 'MESSAGE\ndestination:/sub/chat-rooms/8\n\n{"type":"MESSAGE","roomId":8}\0',
    })
    expect(onEvent).toHaveBeenCalledWith({ type: 'MESSAGE', roomId: 8 })
  })

  it('routes STOMP messages and sends chat commands after connection', () => {
    vi.stubGlobal('WebSocket', MockWebSocket)
    const onAck = vi.fn()
    const onEvent = vi.fn()
    const onError = vi.fn()
    const chatSocket = createChatSocket({ roomId: 3, onAck, onEvent, onError })
    const socket = MockWebSocket.instances[0]

    socket.onopen()
    chatSocket.sendMessage({ clientMessageId: 'draft-1', type: 'TEXT', content: 'before connected' })
    expect(socket.sent.some((frame) => frame.includes('before connected'))).toBe(false)

    socket.onmessage({ data: 'CONNECTED\n\n\0\n' })
    chatSocket.sendMessage({ clientMessageId: 'draft-1', type: 'TEXT', content: 'hello' })
    chatSocket.markRead(4)

    expect(socket.sent).toContain(
      'SEND\ndestination:/pub/chat-rooms/3/messages\ncontent-type:application/json\n\n{"clientMessageId":"draft-1","type":"TEXT","content":"hello"}\0',
    )
    expect(socket.sent).toContain('SEND\ndestination:/pub/chat-rooms/3/read\ncontent-type:application/json\n\n{"lastReadSeq":4}\0')

    socket.onmessage({ data: 'MESSAGE\ndestination:/user/queue/chat-acks\n\n{"clientMessageId":"draft-1"}\0' })
    socket.onmessage({ data: 'MESSAGE\ndestination:/sub/chat-rooms/3\n\n{"type":"MESSAGE"}\0' })
    socket.onmessage({ data: 'MESSAGE\ndestination:/user/queue/errors\n\n{"error":{"message":"failed"}}\0' })

    expect(onAck).toHaveBeenCalledWith({ clientMessageId: 'draft-1' })
    expect(onEvent).toHaveBeenCalledWith({ type: 'MESSAGE' })
    expect(onError).toHaveBeenCalledWith({ error: { message: 'failed' } })
  })
})
