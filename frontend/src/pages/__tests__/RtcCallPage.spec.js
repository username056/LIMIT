import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RtcCallPage from '../RtcCallPage.vue'
import { getChatMessages, getChatRooms } from '../../api/chat'
import { createChatSocket } from '../../api/chatSocket'
import { endRtcSession, getRtcCall, getRtcSession, issueRtcJoinToken } from '../../api/rtc'
import { createReinspectionRequest } from '../../api/products'
import { clearAuthSession, setAuthSession } from '../../auth/session'

const routerPushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { callId: '20' } }),
  useRouter: () => ({ push: routerPushMock }),
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
let rtcSocket
let peer

class MockWebSocket {
  static OPEN = 1

  constructor() {
    this.readyState = MockWebSocket.OPEN
    this.send = vi.fn()
    this.close = vi.fn()
    rtcSocket = this
  }
}

class MockPeerConnection {
  constructor() {
    this.addTrack = vi.fn()
    this.addTransceiver = vi.fn()
    this.close = vi.fn()
    this.connectionState = 'new'
    peer = this
  }
}

describe('RtcCallPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chatSocketOptions = null
    rtcSocket = null
    peer = null
    vi.stubGlobal('WebSocket', MockWebSocket)
    vi.stubGlobal('RTCPeerConnection', MockPeerConnection)
    Object.defineProperty(HTMLMediaElement.prototype, 'srcObject', {
      configurable: true,
      writable: true,
      value: null,
    })
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn() },
    })
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
    issueRtcJoinToken.mockResolvedValue({
      signalingUrl: 'ws://localhost/rtc',
      token: '${TEST_RTC_TOKEN}',
      offerer: false,
      iceServers: [],
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

  it('실시간 사용자 메시지를 받으면 채팅 목록을 최신 메시지로 스크롤한다', async () => {
    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()
    const messageContainer = wrapper.get('[data-testid="rtc-chat-messages"]').element
    Object.defineProperty(messageContainer, 'scrollHeight', {
      configurable: true,
      value: 480,
    })
    messageContainer.scrollTop = 0

    await chatSocketOptions.onEvent({
      type: 'MESSAGE',
      message: {
        messageId: 5,
        roomSequence: 5,
        senderId: 2,
        type: 'TEXT',
        content: '키보드 부분을 가까이 보여 주세요.',
        sentAt: '2026-07-29T10:04:00',
      },
    })
    await flushPromises()

    expect(messageContainer.scrollTop).toBe(480)

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

  it('판매자는 카메라만 필수로 연결하고 마이크 거부를 허용한다', async () => {
    const videoTrack = { stop: vi.fn() }
    navigator.mediaDevices.getUserMedia
      .mockResolvedValueOnce({ getTracks: () => [videoTrack] })
      .mockRejectedValueOnce(new DOMException('denied', 'NotAllowedError'))
    getRtcSession.mockResolvedValue({
      sessionId: 30,
      sellerId: 1,
      buyerId: 2,
      status: 'ACTIVE',
      checklistItems: [],
    })

    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()

    expect(navigator.mediaDevices.getUserMedia).toHaveBeenNthCalledWith(
      1,
      { video: true, audio: false },
    )
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenNthCalledWith(
      2,
      { video: false, audio: true },
    )
    expect(peer.addTrack).toHaveBeenCalledWith(videoTrack, expect.anything())
    expect(wrapper.text()).not.toContain('통화 연결 중 오류가 발생했습니다.')

    wrapper.unmount()
  })

  it('구매자는 카메라와 마이크 권한 없이 판매자 미디어를 수신한다', async () => {
    setAuthSession({ member: { memberId: 2, nickname: '구매자' } })
    getRtcSession.mockResolvedValue({
      sessionId: 30,
      sellerId: 1,
      buyerId: 2,
      status: 'ACTIVE',
      checklistItems: [],
    })

    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()

    expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
    expect(peer.addTransceiver).toHaveBeenCalledWith('video', { direction: 'recvonly' })
    expect(peer.addTransceiver).toHaveBeenCalledWith('audio', { direction: 'recvonly' })

    wrapper.unmount()
  })

  it('세션 종료 시 영상을 비우고 바로 채팅으로 이동한다', async () => {
    const videoTrack = { stop: vi.fn() }
    navigator.mediaDevices.getUserMedia
      .mockResolvedValueOnce({ getTracks: () => [videoTrack] })
      .mockRejectedValueOnce(new DOMException('denied', 'NotAllowedError'))
    getRtcSession.mockResolvedValue({
      sessionId: 30,
      sellerId: 1,
      buyerId: 2,
      status: 'ACTIVE',
      checklistItems: [],
    })
    endRtcSession.mockResolvedValue({
      sessionId: 30,
      sellerId: 1,
      buyerId: 2,
      status: 'ENDED',
      checklistItems: [],
    })
    const wrapper = mount(RtcCallPage, {
      global: { stubs: { DefaultLayout: layoutStub } },
    })
    await flushPromises()
    const video = wrapper.get('video').element
    expect(video.srcObject).not.toBeNull()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(endRtcSession).toHaveBeenCalled()
    expect(videoTrack.stop).toHaveBeenCalled()
    expect(video.srcObject).toBeNull()
    expect(routerPushMock).toHaveBeenCalledWith({ name: 'chat', params: { roomId: 10 } })

    wrapper.unmount()
  })

  afterEach(() => {
    clearAuthSession()
    vi.unstubAllGlobals()
  })
})
