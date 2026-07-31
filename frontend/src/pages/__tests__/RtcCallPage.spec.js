import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RtcCallPage from '../RtcCallPage.vue'
import { getChatMessages, getChatRooms } from '../../api/chat'
import { createChatSocket } from '../../api/chatSocket'
import { getRtcCall, getRtcSession } from '../../api/rtc'
import { createReinspectionRequest } from '../../api/products'
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

vi.mock('../../api/products', () => ({
  createReinspectionRequest: vi.fn(),
}))

const layoutStub = { template: '<main><slot /></main>' }
let chatSocketOptions

describe('RtcCallPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chatSocketOptions = null
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
    createChatSocket.mockImplementation((options) => {
      chatSocketOptions = options
      const { onOpen } = options
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

  it('실시간 채팅에서는 약속 시스템 알림을 숨기고 사용자 메시지만 표시한다', async () => {
    getChatMessages.mockResolvedValue({
      content: [
        {
          messageId: 2,
          roomSequence: 2,
          senderId: 1,
          type: 'SYSTEM',
          content: '판매자 님이 실시간 검증 약속을 설정했습니다.',
          sentAt: '2026-07-29T10:01:00',
        },
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
    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('제품 모서리를 보여 주세요.')
    expect(wrapper.text()).not.toContain('실시간 검증 약속을 설정했습니다.')

    await chatSocketOptions.onEvent({
      type: 'MESSAGE',
      message: {
        messageId: 3,
        roomSequence: 3,
        senderId: 1,
        type: 'SYSTEM',
        content: '판매자 님이 실시간 검증 약속을 변경했습니다.',
        sentAt: '2026-07-29T10:02:00',
      },
    })
    await chatSocketOptions.onEvent({
      type: 'MESSAGE',
      message: {
        messageId: 4,
        roomSequence: 4,
        senderId: 2,
        type: 'TEXT',
        content: '이번에는 뒷면을 보여 주세요.',
        sentAt: '2026-07-29T10:03:00',
      },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('실시간 검증 약속을 변경했습니다.')
    expect(wrapper.text()).toContain('이번에는 뒷면을 보여 주세요.')

    wrapper.unmount()
  })

  // 재촬영 요청은 잠시 내려 두었습니다. 채팅으로 말하는 편이 빠릅니다.
  it('통화 화면에 재촬영 요청 UI를 두지 않는다', async () => {
    setAuthSession({ member: { memberId: 2, nickname: '구매자' } })
    getRtcSession.mockResolvedValue({
      sessionId: 30,
      listingId: 777,
      sellerId: 1,
      buyerId: 2,
      status: 'ENDED',
      checklistItems: [{
        checklistItemId: 100,
        name: '제품 외관 전체 확인',
        captureGuide: '모서리와 흠집을 확인해 주세요.',
        confirmed: false,
        note: null,
      }],
    })
    getChatRooms.mockResolvedValue({
      content: [{ roomId: 10, counterpartId: 1, counterpartNickname: '판매자닉네임' }],
    })

    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('재촬영 요청 보내기')
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    expect(createReinspectionRequest).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  // 체크리스트는 판매글에 스냅샷된 항목(listing_checklist_item)을 그대로 읽습니다.
  it('판매글에 등록된 체크리스트 항목을 그대로 보여준다', async () => {
    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('제품 외관 전체 확인')
    expect(wrapper.text()).toContain('모서리와 흠집을 확인해 주세요.')
    expect(wrapper.text()).toContain('판매글에 등록된 검증 항목과 같은 목록입니다')

    wrapper.unmount()
  })

  afterEach(() => {
    clearAuthSession()
  })
})
