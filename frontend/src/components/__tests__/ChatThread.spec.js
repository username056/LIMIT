import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChatThread from '../ChatThread.vue'
import { getChatMediaBlob, getChatMessages } from '../../api/chat'
import { createChatSocket } from '../../api/chatSocket'
import { getMyReinspectionRequests, getProduct, getProductChecklist } from '../../api/products'
import { getMyRtcCalls, requestRtcCall, respondRtcCall } from '../../api/rtc'

vi.mock('../../api/chat', () => ({
  getChatMediaBlob: vi.fn(),
  getChatMessages: vi.fn(),
  uploadChatMedia: vi.fn(),
}))
vi.mock('../../api/chatSocket', () => ({ createChatSocket: vi.fn() }))
vi.mock('../../api/products', () => ({
  getMyReinspectionRequests: vi.fn(),
  getProduct: vi.fn(),
  getProductChecklist: vi.fn(),
}))
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
    getProductChecklist.mockResolvedValue([])
    getMyReinspectionRequests.mockResolvedValue([])
    getMyRtcCalls.mockResolvedValue([])
    URL.createObjectURL = vi.fn(() => 'blob:chat-image')
    URL.revokeObjectURL = vi.fn()
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

  it('opens an image preview and closes it with Escape', async () => {
    getChatMediaBlob.mockResolvedValue(new Blob(['image'], { type: 'image/png' }))
    getChatMessages.mockResolvedValue({
      content: [{
        messageId: 3,
        roomSequence: 4,
        senderId: 2,
        clientMessageId: 'image-message',
        type: 'IMAGE',
        content: 'phone.png',
        sentAt: '2026-07-28T10:02:00',
        media: [{ mediaId: 30, type: 'IMAGE' }],
      }],
    })

    const wrapper = mountThread()
    await flushPromises()

    await wrapper.get('button[aria-label="phone.png 확대 보기"]').trigger('click')
    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('채팅 이미지 확대 보기')
    expect(wrapper.get('[role="dialog"] img').attributes('src')).toBe('blob:chat-image')
    expect(document.body.style.overflow).toBe('hidden')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(document.body.style.overflow).toBe('')
  })

  it('새로고침 후 일반 시스템 메시지로 조회된 재검수 알림을 카드로 복원한다', async () => {
    getChatMessages.mockResolvedValue({
      content: [{
        messageId: 50,
        roomSequence: 4,
        senderId: 2,
        clientMessageId: 'reinspection-event',
        type: 'SYSTEM',
        content: '재검수 요청이 1건 들어왔어요!\n사유: 영상 확인 필요\n- 외관 상태: 다시 촬영해 주세요.',
        sentAt: '2026-07-30T10:00:00',
        media: [],
      }],
    })
    getMyReinspectionRequests.mockResolvedValue([{
      requestKey: 'request-key',
      listingId: 1,
      reason: '영상 확인 필요',
      items: [{ itemName: '외관 상태', requestContent: '다시 촬영해 주세요.' }],
    }])

    const wrapper = mountThread()
    await flushPromises()

    expect(wrapper.get('[data-testid="system-notification-card"]').text())
      .toContain('재검수 요청이 들어왔어요!')
    expect(wrapper.text()).toContain('영상 확인 필요')
    expect(wrapper.text()).toContain('외관 상태')
    expect(wrapper.text()).not.toContain('바로 재촬영하기')
  })

  it('판매자 재검수 목록과 매칭되지 않아도 저장 본문을 카드로 복원한다', async () => {
    getChatMessages.mockResolvedValue({
      content: [{
        messageId: 52,
        roomSequence: 6,
        senderId: 2,
        clientMessageId: 'plain-reinspection-event',
        type: 'SYSTEM',
        content: '재검수 요청이 1건 들어왔어요!\n사유: 화면을 다시 확인해 주세요.\n- 화면 상태: 다시 촬영해 주세요.',
        sentAt: '2026-07-30T10:00:00',
        media: [],
      }],
    })
    getMyReinspectionRequests.mockResolvedValue([])

    const wrapper = mountThread()
    await flushPromises()

    const card = wrapper.get('[data-testid="system-notification-card"]')
    expect(card.text()).toContain('재검수 요청이 들어왔어요!')
    expect(card.text()).toContain('화면을 다시 확인해 주세요.')
    expect(card.text()).toContain('화면 상태')
  })

  it('새로고침 후 재검수 완료 시스템 메시지를 확인 카드로 복원한다', async () => {
    getChatMessages.mockResolvedValue({
      content: [{
        messageId: 51,
        roomSequence: 5,
        senderId: 2,
        clientMessageId: 'reinspection-completed-event',
        type: 'SYSTEM',
        content: '재검수가 완료되었습니다.',
        sentAt: '2026-07-30T11:00:00',
        media: [],
      }],
    })

    const wrapper = mountThread()
    await flushPromises()

    expect(wrapper.get('[data-testid="system-notification-card"]').text())
      .toContain('재검수를 완료했어요!')
    expect(wrapper.text()).toContain('확인하러 가기')
    expect(wrapper.text()).not.toContain('재검수가 완료되었습니다.')
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
    const scheduledAt = new Date(Date.now() - 5 * 60 * 1000).toISOString()
    getMyRtcCalls
      .mockResolvedValueOnce([{
        callId: 31,
        chatRoomId: 10,
        status: 'PROPOSED',
        scheduledAt,
        memo: '화면 상태 확인',
        incoming: true,
      }])
      .mockResolvedValueOnce([{
        callId: 31,
        chatRoomId: 10,
        status: 'ACCEPTED',
        scheduledAt,
        incoming: true,
        rtcSessionId: 7,
        sessionExpiresAt: '2026-08-02T16:00:00',
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
    expect(wrapper.get('[data-testid="appointment-expiration"]').text()).toContain('약속 만료까지')

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

    expect(wrapper.get('[data-testid="appointment-card"]').attributes('style')).toContain('order: 1')
    expect(wrapper.get('[data-message-sequence="4"]').attributes('style')).toContain('order: 8')
  })

  it('이미 만료된 약속 카드는 채팅에서 표시하지 않는다', async () => {
    getMyRtcCalls.mockResolvedValue([{
      callId: 33,
      chatRoomId: 10,
      status: 'ACCEPTED',
      scheduledAt: '2020-01-01T14:00:00',
      incoming: true,
      rtcSessionId: 8,
      sessionExpiresAt: '2020-01-01T16:00:00',
    }])

    const wrapper = mountThread()
    await flushPromises()

    expect(wrapper.find('[data-testid="appointment-card"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('실시간 검증 입장하기')
  })

  it('채팅 약속 카드에서 자동 생성된 상태 확인 메모를 숨긴다', async () => {
    getMyRtcCalls.mockResolvedValue([{
      callId: 34,
      chatRoomId: 10,
      status: 'PROPOSED',
      scheduledAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      memo: '갤럭시 자급제 상태 실시간 확인 요청',
      incoming: false,
    }])

    const wrapper = mountThread()
    await flushPromises()

    expect(wrapper.get('[data-testid="appointment-card"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('갤럭시 자급제 상태 실시간 확인 요청')
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
    expect(wrapper.get('[data-testid="appointment-card"]').attributes('style')).toContain('order: 1')
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
  it('약속 변경 이벤트를 받으면 새로고침 없이 약속 카드를 갱신한다', async () => {
    getMyRtcCalls
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{
        callId: 40,
        chatRoomId: 10,
        status: 'PROPOSED',
        scheduledAt: '2026-08-04T14:00:00',
        incoming: true,
      }])
    const wrapper = mountThread()
    await flushPromises()

    await socketHandlers.onEvent({ type: 'CALL_APPOINTMENT_UPDATED', roomId: 10 })
    await flushPromises()

    expect(getMyRtcCalls).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="appointment-card"]').exists()).toBe(true)
  })

  it.each([
    ['설정', '민수 님이 실시간 검증 약속을 설정했습니다.'],
    ['변경', '민수 님이 실시간 검증 약속을 변경했습니다.'],
    ['취소', '민수 님이 실시간 검증 약속을 취소했습니다.'],
  ])('약속 %s 메시지를 구분선 알림으로 표시한다', async (_, content) => {
    const wrapper = mountThread()
    await flushPromises()

    await socketHandlers.onEvent({
      type: 'MESSAGE',
      message: {
        messageId: 60,
        roomSequence: 5,
        senderId: 2,
        clientMessageId: 'appointment-updated-event',
        type: 'SYSTEM',
        content,
        sentAt: '2026-07-30T10:00:00',
        media: [],
      },
    })
    await flushPromises()

    const divider = wrapper.get('[data-testid="appointment-notification-divider"]')
    expect(divider.text()).toContain(content)
    expect(divider.element.parentElement?.parentElement?.textContent?.trim()).toBe(content)
    expect(wrapper.find('[data-testid="appointment-notification-card"]').exists()).toBe(false)
  })

  it('재검수 실시간 이벤트를 구조화된 알림 카드로 표시한다', async () => {
    const wrapper = mountThread()
    await flushPromises()

    await socketHandlers.onEvent({
      type: 'REINSPECTION_REQUESTED',
      message: {
        messageId: 50,
        roomSequence: 4,
        senderId: 2,
        clientMessageId: 'reinspection-event',
        type: 'SYSTEM',
        content: '배터리 상태를 다시 촬영해 주세요.',
        sentAt: '2026-07-30T10:00:00',
        media: [],
      },
      reinspection: {
        requestKey: 'request-key',
        listingId: 1,
        reason: '영상 확인 필요',
        items: [
          { name: '기기 식별 정보', requestContent: '자세히 보여 주세요.' },
          { name: '외관 상태', requestContent: '다시 촬영해 주세요.' },
        ],
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="system-notification-card"]').text())
      .toContain('재검수 요청이 들어왔어요!')
    expect(wrapper.get('[data-testid="system-notification-card"]').text())
      .toContain('재검수 요청')
    expect(wrapper.text()).toContain('기기 식별 정보')
    expect(wrapper.text()).toContain('외관 상태')
    expect(wrapper.text()).toContain('영상 확인 필요')
    expect(wrapper.text()).toContain('요청 내용')
    expect(wrapper.text()).toContain('선택한 체크리스트')
    expect(wrapper.text()).not.toContain('자세히 보여 주세요.')
    expect(wrapper.text()).not.toContain('바로 재촬영하기')
  })

  it('판매자 화면에서는 재검수 요청 카드를 내 쪽에 표시한다', async () => {
    getProduct.mockResolvedValue({ name: 'Galaxy S24', price: 650000, sellerId: 1 })
    const wrapper = mountThread()
    await flushPromises()

    await socketHandlers.onEvent({
      type: 'REINSPECTION_REQUESTED',
      message: {
        messageId: 61,
        roomSequence: 5,
        senderId: 1,
        clientMessageId: 'my-reinspection-event',
        type: 'SYSTEM',
        content: '재검수 요청이 들어왔어요!',
        sentAt: '2026-07-30T10:00:00',
        media: [],
      },
      reinspection: {
        requestKey: 'request-key',
        listingId: 1,
        reason: '화면 상태를 확인해 주세요.',
        items: [{ name: '화면 상태', requestContent: '다시 촬영해 주세요.' }],
      },
    })
    await flushPromises()

    const card = wrapper.get('[data-testid="system-notification-card"]')
    expect(card.text()).toContain('재검수 요청이 들어왔어요!')
    expect(card.element.parentElement.parentElement.classList).toContain('items-end')
    expect(wrapper.text()).toContain('바로 재촬영하기')
  })

})
