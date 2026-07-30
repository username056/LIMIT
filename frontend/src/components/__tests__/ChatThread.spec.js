import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChatThread from '../ChatThread.vue'
import { getChatMessages } from '../../api/chat'
import { createChatSocket } from '../../api/chatSocket'
import { getProduct, getProductChecklist } from '../../api/products'
import { getMyRtcCalls, requestRtcCall, respondRtcCall } from '../../api/rtc'

vi.mock('../../api/chat', () => ({
  getChatMediaBlob: vi.fn(),
  getChatMessages: vi.fn(),
  uploadChatMedia: vi.fn(),
}))
vi.mock('../../api/chatSocket', () => ({ createChatSocket: vi.fn() }))
vi.mock('../../api/products', () => ({ getProduct: vi.fn(), getProductChecklist: vi.fn() }))
vi.mock('../../api/rtc', () => ({
  getMyRtcCalls: vi.fn(),
  requestRtcCall: vi.fn(),
  respondRtcCall: vi.fn(),
}))
vi.mock('../../auth/session', () => ({
  useAuthSession: () => ({ value: { member: { memberId: 1 } } }),
}))

let socketHandlers

const room = {
  roomId: 10,
  listingId: 1,
  counterpartId: 2,
  counterpartNickname: '상대방',
  counterpartLastReadSequence: 2,
}

function mountThread() {
  return mount(ChatThread, {
    props: { room },
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

describe('ChatThread', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    socketHandlers = null
    createChatSocket.mockImplementation((handlers) => {
      socketHandlers = handlers
      return {
        close: vi.fn(),
        markRead: vi.fn(),
        sendMessage: vi.fn(),
      }
    })
    getProduct.mockResolvedValue({ name: 'Galaxy S24', price: 650000 })
    getMyRtcCalls.mockResolvedValue([])
    getChatMessages.mockResolvedValue({
      content: [
        {
          messageId: 2,
          roomSequence: 3,
          senderId: 1,
          clientMessageId: 'message-2',
          type: 'TEXT',
          content: '아직 안 읽은 메시지',
          sentAt: '2026-07-28T10:01:00',
          media: [],
        },
        {
          messageId: 1,
          roomSequence: 2,
          senderId: 1,
          clientMessageId: 'message-1',
          type: 'TEXT',
          content: '읽은 메시지',
          sentAt: '2026-07-28T10:00:00',
          media: [],
        },
      ],
    })
  })

  it('상품의 체크리스트를 펼쳐서 항목과 진행 상태를 보여준다', async () => {
    getProductChecklist.mockResolvedValue([
      { checklistItemId: 7001, name: '전면·후면·측면 외관', evidenceType: 'PHOTO', isRequired: true, status: 'COMPLETED' },
      { checklistItemId: 7002, name: '화면 밝기', evidenceType: 'VIDEO', isRequired: true, status: 'PENDING' },
    ])

    const wrapper = mountThread()
    await flushPromises()

    expect(getProductChecklist).toHaveBeenCalledWith(1)
    // 기본은 접힌 상태라 항목이 보이지 않습니다.
    expect(wrapper.text()).toContain('검증 체크리스트')
    expect(wrapper.text()).toContain('1 / 2')
    expect(wrapper.text()).not.toContain('전면·후면·측면 외관')

    await wrapper.findAll('button').find((button) => button.text().includes('펼치기')).trigger('click')

    expect(wrapper.text()).toContain('전면·후면·측면 외관')
    expect(wrapper.text()).toContain('화면 밝기')
    expect(wrapper.text()).toContain('자료 확인')
    expect(wrapper.text()).toContain('미등록')

    // 항목이 많아도 채팅 영역을 밀지 않도록 스크롤 영역에 담습니다.
    const list = wrapper.findAll('ul').find((node) => node.text().includes('전면·후면·측면 외관'))
    expect(list.classes()).toContain('overflow-y-auto')
  })

  it('상대방이 읽은 내 메시지에만 읽음을 표시한다', async () => {
    const wrapper = mountThread()
    await flushPromises()

    expect(wrapper.findAll('span').filter((item) => item.text().includes('읽음'))).toHaveLength(1)

    await socketHandlers.onEvent({
      type: 'READ',
      readerId: 2,
      lastReadSeq: 3,
    })
    await flushPromises()

    expect(wrapper.findAll('span').filter((item) => item.text().includes('읽음'))).toHaveLength(2)
  })

  it('채팅방에서 통화 시간과 메모로 약속을 요청한다', async () => {
    requestRtcCall.mockResolvedValue({ callId: 30, status: 'PROPOSED' })
    getMyRtcCalls
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{
        callId: 30,
        chatRoomId: 10,
        status: 'PROPOSED',
        scheduledAt: '2026-08-01T15:30:00',
        incoming: false,
      }])
    const wrapper = mountThread()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text().includes('실시간 검증 일정 잡기')).trigger('click')
    await wrapper.get('input[type="datetime-local"]').setValue('2026-08-01T15:30')
    await wrapper.get('input[placeholder="확인할 내용을 입력하세요."]').setValue('배터리 확인')
    await wrapper.find('form:has(input[type="datetime-local"])').trigger('submit')
    await flushPromises()

    expect(requestRtcCall).toHaveBeenCalledWith(10, {
      scheduledAt: '2026-08-01T15:30:00',
      memo: '배터리 확인',
    })
    expect(wrapper.text()).toContain('통화 약속을 요청했습니다.')
    expect(wrapper.text()).toContain('실시간 화상 검증 일정 제안')
  })

  it('받은 통화 약속을 카드에서 수락한다', async () => {
    getMyRtcCalls
      .mockResolvedValueOnce([{
        callId: 31,
        chatRoomId: 10,
        status: 'PROPOSED',
        scheduledAt: '2026-08-02T14:00:00',
        memo: '화면 상태 확인',
        incoming: true,
      }])
      .mockResolvedValueOnce([{
        callId: 31,
        chatRoomId: 10,
        status: 'ACCEPTED',
        scheduledAt: '2026-08-02T14:00:00',
        incoming: true,
        rtcSessionId: 7,
      }])
    respondRtcCall.mockResolvedValue({ callId: 31, status: 'ACCEPTED', rtcSessionId: 7 })
    const wrapper = mountThread()
    await flushPromises()

    expect(wrapper.text()).toContain('실시간 화상 검증 일정 제안')
    await wrapper.findAll('button').find((button) => button.text() === '수락하기').trigger('click')
    await flushPromises()

    expect(respondRtcCall).toHaveBeenCalledWith(31, true, null)
    expect(wrapper.text()).toContain('실시간 화상 검증 일정 확정')
    expect(wrapper.text()).toContain('실시간 검증 입장하기')

    await socketHandlers.onEvent({
      type: 'MESSAGE',
      message: {
        messageId: 3,
        roomSequence: 4,
        senderId: 2,
        clientMessageId: 'message-after-appointment',
        type: 'TEXT',
        content: '약속 뒤에 보낸 메시지',
        sentAt: '2026-07-28T10:02:00',
        media: [],
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="appointment-card"]').attributes('style')).toContain('order: 7')
    expect(wrapper.get('[data-message-sequence="4"]').attributes('style')).toContain('order: 8')
  })

  it('순번이 없는 전송 대기 메시지를 일정 카드 뒤에 안전하게 정렬한다', async () => {
    getMyRtcCalls.mockResolvedValue([{
      callId: 32,
      chatRoomId: 10,
      status: 'PROPOSED',
      scheduledAt: '2026-08-03T14:00:00',
      incoming: false,
    }])
    const wrapper = mountThread()
    await flushPromises()
    await socketHandlers.onOpen()

    await wrapper.get('input[placeholder="메시지를 입력하세요..."]').setValue('전송 대기 메시지')
    await wrapper.findAll('form').find((form) => form.find('input[placeholder="메시지를 입력하세요..."]').exists()).trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="pending-message"]').attributes('style')).toContain('order: 2147483647')
    expect(wrapper.get('[data-testid="appointment-card"]').attributes('style')).toContain('order: 7')
  })

  it('사진 또는 영상 첨부 버튼으로 파일 선택기를 연다', async () => {
    const wrapper = mountThread()
    await flushPromises()
    await socketHandlers.onOpen()
    const fileInput = wrapper.get('input[type="file"]')
    const clickSpy = vi.spyOn(fileInput.element, 'click')

    await wrapper.get('button[aria-label="사진 또는 영상 첨부"]').trigger('click')

    expect(clickSpy).toHaveBeenCalledTimes(1)
  })
})
