import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RtcCallPage from '../RtcCallPage.vue'
import { getChatMessages, getChatRooms } from '../../api/chat'
import { createChatSocket } from '../../api/chatSocket'
import { getRtcCall, getRtcSession } from '../../api/rtc'
import { clearAuthSession, setAuthSession } from '../../auth/session'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { callId: '20' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../../api/chat', () => ({
  getChatMessages: vi.fn(),
  getChatRooms: vi.fn(),
}))

vi.mock('../../api/chatSocket', () => ({
  createChatSocket: vi.fn(),
}))

vi.mock('../../api/rtc', () => ({
  endRtcSession: vi.fn(),
  getRtcCall: vi.fn(),
  getRtcSession: vi.fn(),
  issueRtcJoinToken: vi.fn(),
  markRtcConnected: vi.fn(),
  respondRtcCall: vi.fn(),
  signalingSocketUrl: vi.fn(),
}))

const layoutStub = { template: '<main><slot /></main>' }

describe('RtcCallPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAuthSession({ member: { memberId: 1, nickname: '판매자' } })
    getRtcCall.mockResolvedValue({
      callId: 20,
      chatRoomId: 10,
      rtcSessionId: 30,
      status: 'ACCEPTED',
    })
    getRtcSession.mockResolvedValue({
      sessionId: 30,
      sellerId: 1,
      buyerId: 2,
      status: 'ENDED',
      checklistItems: [
        {
          checklistItemId: 100,
          name: '제품 외관 전체 확인',
          captureGuide: '모서리와 흠집을 확인해 주세요.',
          confirmed: false,
          note: null,
        },
      ],
    })
    getChatMessages.mockResolvedValue({
      content: [
        {
          messageId: 1,
          roomSequence: 1,
          senderId: 2,
          type: 'TEXT',
          content: '제품 모서리를 보여 주세요.',
          sentAt: '2026-07-29T10:00:00',
        },
      ],
    })
    getChatRooms.mockResolvedValue({
      content: [{ roomId: 10, counterpartId: 2, counterpartNickname: '구매자닉네임' }],
    })
    createChatSocket.mockImplementation(({ onOpen }) => {
      queueMicrotask(onOpen)
      return {
        close: vi.fn(),
        markRead: vi.fn(),
        sendMessage: vi.fn(),
      }
    })
  })

  it('판매자 영상 하나와 읽기 전용 체크리스트, 실시간 채팅을 표시한다', async () => {
    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()

    expect(wrapper.findAll('video')).toHaveLength(1)
    expect(wrapper.text()).toContain('판매자 라이브 화면')
    expect(wrapper.text()).toContain('제품 외관 전체 확인')
    expect(wrapper.text()).toContain('모서리와 흠집을 확인해 주세요.')
    expect(wrapper.get('[data-testid="counterpart-nickname"]').text()).toBe('구매자닉네임')
    expect(wrapper.text()).not.toContain('실시간 연결됨')
    expect(wrapper.text()).toContain('제품 모서리를 보여 주세요.')
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    expect(createChatSocket).toHaveBeenCalledWith(
      expect.objectContaining({ roomId: 10 }),
    )

    wrapper.unmount()
  })

  it('구매자에게는 판매자 프로필을 표시한다', async () => {
    setAuthSession({ member: { memberId: 2, nickname: '구매자' } })
    getChatRooms.mockResolvedValue({
      content: [{ roomId: 10, counterpartId: 1, counterpartNickname: '판매자닉네임' }],
    })

    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="counterpart-nickname"]').text()).toBe('판매자닉네임')

    wrapper.unmount()
  })

  afterEach(() => {
    clearAuthSession()
  })
})
