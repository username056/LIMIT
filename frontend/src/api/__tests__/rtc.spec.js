import { afterEach, describe, expect, it, vi } from 'vitest'
import { createChatRoom, issueRtcJoinToken, requestRtcCall, signalingSocketUrl } from '../rtc'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const ok = (data) => ({ ok: true, json: vi.fn().mockResolvedValue({ data, meta: null }) })

describe('rtc api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a chat room and requests a call', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(ok({ roomId: 10 }))
      .mockResolvedValueOnce(ok({ callId: 20 }))
    vi.stubGlobal('fetch', fetchMock)

    await createChatRoom(1001)
    await requestRtcCall(10, { memo: '배터리 확인' })

    expect(fetchMock).toHaveBeenNthCalledWith(1, `${API_BASE_URL}/listings/1001/chat-rooms`, expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, `${API_BASE_URL}/chat-rooms/10/calls`, expect.objectContaining({ method: 'POST' }))
  })

  it('issues a reentry token and builds a websocket url', async () => {
    const join = { signalingUrl: '/ws/rtc', joinToken: '<one-time-token>' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok(join)))

    await expect(issueRtcJoinToken(55)).resolves.toEqual(join)
    expect(signalingSocketUrl(join)).toContain('/ws/rtc?token=%3Cone-time-token%3E')
  })
})
