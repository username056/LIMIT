import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CallsPage from '../CallsPage.vue'
import { getChatRooms } from '../../api/chat'
import { cancelRtcCall, getMyRtcCalls, getRtcSession, updateRtcCall } from '../../api/rtc'
import { getMyReinspectionRequests, getProduct, getProductImages } from '../../api/products'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('../../auth/session', () => ({
  useAuthSession: () => ({ value: { member: { memberId: 1 } } }),
}))

vi.mock('../../api/rtc', () => ({
  cancelRtcCall: vi.fn(),
  getMyRtcCalls: vi.fn(),
  getRtcSession: vi.fn(),
  respondRtcCall: vi.fn(),
  updateRtcCall: vi.fn(),
}))
vi.mock('../../api/chat', () => ({
  getChatRooms: vi.fn(),
}))
vi.mock('../../api/products', () => ({
  getMyReinspectionRequests: vi.fn(),
  getProduct: vi.fn(),
  getProductImages: vi.fn(),
}))

const futureScheduledAt = new Date(Date.now() + 24 * 60 * 60 * 1000)
const futureSessionExpiresAt = new Date(futureScheduledAt.getTime() + 30 * 60 * 1000)
const futureScheduledDateLabel = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
}).format(futureScheduledAt)

const outgoingCall = {
  callId: 20,
  status: 'PROPOSED',
  incoming: false,
  scheduledAt: futureScheduledAt.toISOString(),
  memo: '제품 상태 확인',
  rtcSessionId: null,
  proposerId: 1,
  respondentId: 2,
  counterpartName: '상대 회원',
  sessionExpiresAt: futureSessionExpiresAt.toISOString(),
}

const layoutStub = { template: '<main><slot /></main>' }
const cardStub = { template: '<section><slot /></section>' }
const buttonStub = {
  props: ['disabled', 'type'],
  emits: ['click'],
  template: '<button :type="type || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

function mountPage() {
  return mount(CallsPage, {
    global: {
      stubs: {
        DefaultLayout: layoutStub,
        BaseCard: cardStub,
        BaseButton: buttonStub,
      },
    },
  })
}

describe('CallsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMyRtcCalls.mockResolvedValue([outgoingCall])
    getChatRooms.mockResolvedValue({
      content: [{
        roomId: 7,
        listingId: 101,
        listingTitle: '갤럭시 테스트 상품',
        listingThumbnailUrl: 'https://example.test/product.jpg',
        counterpartNickname: '상대 회원',
      }],
    })
    getRtcSession.mockResolvedValue({ expiresAt: futureSessionExpiresAt.toISOString() })
    getMyReinspectionRequests.mockResolvedValue([])
    getProduct.mockResolvedValue(null)
    getProductImages.mockResolvedValue([])
  })

  it('대기, 진행 중, 완료, 종료 상태 필터를 유지하고 요청 카드 디자인으로 표시한다', async () => {
    getMyRtcCalls.mockResolvedValue([
      { ...outgoingCall, callId: 20, chatRoomId: 7, status: 'PROPOSED' },
      { ...outgoingCall, callId: 21, chatRoomId: 7, status: 'ACCEPTED' },
      { ...outgoingCall, callId: 22, chatRoomId: 7, status: 'COMPLETED' },
      { ...outgoingCall, callId: 23, chatRoomId: 7, status: 'REJECTED' },
      { ...outgoingCall, callId: 24, chatRoomId: 7, status: 'CANCELED' },
      {
        ...outgoingCall,
        callId: 25,
        chatRoomId: 7,
        status: 'ACCEPTED',
        scheduledAt: '2020-01-01T00:00:00',
      },
    ])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('갤럭시 테스트 상품')
    expect(wrapper.text()).toContain('전체 목록 (6)')
    expect(wrapper.text()).toContain('대기 (1)')
    expect(wrapper.text()).toContain('진행 중 (1)')
    expect(wrapper.text()).toContain('완료 (1)')
    expect(wrapper.text()).toContain('종료 (3)')
    expect(wrapper.findAll('[data-testid="rtc-request-card"]')).toHaveLength(6)
    expect(wrapper.get('[data-testid="rtc-request-card"]').classes()).toContain('request-card')

    await wrapper.findAll('button').find((button) => button.text() === '대기 (1)').trigger('click')

    expect(wrapper.findAll('[data-testid="rtc-request-card"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('상대 응답 대기')

    await wrapper.findAll('button').find((button) => button.text() === '진행 중 (1)').trigger('click')

    expect(wrapper.findAll('[data-testid="rtc-request-card"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('일정 확정')

    await wrapper.findAll('button').find((button) => button.text() === '완료 (1)').trigger('click')

    expect(wrapper.findAll('[data-testid="rtc-request-card"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('확인 완료')

    await wrapper.findAll('button').find((button) => button.text() === '종료 (3)').trigger('click')

    expect(wrapper.findAll('[data-testid="rtc-request-card"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('거절됨')
    expect(wrapper.text()).toContain('취소됨')
    expect(wrapper.text()).toContain('세션 만료')
    expect(wrapper.text()).not.toContain('시간 만료')
  })

  it('검증 일정 날짜와 상대방, 세션 만료 시간을 표시한다', async () => {
    getMyRtcCalls.mockResolvedValue([{ ...outgoingCall, status: 'ACCEPTED' }])
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('상대 회원')
    expect(wrapper.text()).toContain(futureScheduledDateLabel)
    expect(wrapper.text()).toContain('만료까지')
  })

  it('기존 요청의 자동 생성 상태 확인 메모를 표시하지 않는다', async () => {
    getMyRtcCalls.mockResolvedValue([{
      ...outgoingCall,
      chatRoomId: 7,
      memo: '갤럭시 자급제 상태 실시간 확인 요청',
    }])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('갤럭시 자급제 상태 실시간 확인 요청')
  })

  it('목록 응답에 정보가 없으면 채팅방과 세션 상세에서 보완한다', async () => {
    getMyRtcCalls.mockResolvedValue([{
      ...outgoingCall,
      chatRoomId: 7,
      rtcSessionId: 31,
      status: 'ACCEPTED',
      counterpartName: null,
      sessionExpiresAt: null,
    }])
    getChatRooms.mockResolvedValue({
      content: [{ roomId: 7, counterpartNickname: '채팅 상대방' }],
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(getRtcSession).toHaveBeenCalledWith(31)
    expect(wrapper.text()).toContain('채팅 상대방')
    expect(wrapper.text()).toContain('만료까지')
  })

  it('채팅방 썸네일이 없으면 상품 대표 이미지를 상품 카드에 매핑한다', async () => {
    getMyRtcCalls.mockResolvedValue([{ ...outgoingCall, chatRoomId: 7 }])
    getChatRooms.mockResolvedValue({
      content: [{
        roomId: 7,
        listingId: 101,
        listingTitle: '이미지 보완 상품',
        listingThumbnailUrl: null,
      }],
    })
    getProductImages.mockResolvedValue([
      { imageId: 2, imageType: 'DETAIL', imageUrl: 'https://example.test/detail.jpg' },
      { imageId: 1, imageType: 'THUMBNAIL', imageUrl: 'https://example.test/thumbnail.jpg' },
    ])

    const wrapper = mountPage()
    await flushPromises()

    expect(getProductImages).toHaveBeenCalledWith(101)
    expect(wrapper.get('[data-testid="rtc-request-card"] img').attributes('src'))
      .toBe('https://example.test/thumbnail.jpg')
  })

  it('재촬영 요청 상품의 정보와 사진을 매핑하고 강조 카드로 표시한다', async () => {
    getMyReinspectionRequests.mockResolvedValue([{
      requestKey: 'request-1',
      listingId: 101,
      requestedAt: '2026-07-31T10:00:00',
      reason: '모서리를 다시 찍어 주세요.',
      status: 'REQUESTED',
      items: [{ itemName: '외관' }],
    }])
    getProduct.mockResolvedValue({
      productId: 101,
      name: '갤럭시 재촬영 상품',
      price: 650000,
      thumbnailUrl: null,
      sellerId: 1,
    })
    getProductImages.mockResolvedValue([
      { imageId: 1, imageType: 'THUMBNAIL', imageUrl: 'https://example.test/recapture.jpg' },
    ])

    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '재촬영 요청').trigger('click')

    const requestCard = wrapper.get('[data-testid="recapture-request-card"]')
    expect(requestCard.text()).toContain('갤럭시 재촬영 상품')
    expect(requestCard.text()).toContain('재촬영 대기')
    expect(requestCard.get('img').attributes('src')).toBe('https://example.test/recapture.jpg')
    expect(requestCard.classes()).toContain('request-card')
    expect(requestCard.classes()).toContain('request-card--retake')
    expect(wrapper.text()).toContain('재촬영 진행하기')
  })

  it('재촬영을 요청한 구매자에게는 재촬영 진행 버튼만 숨긴다', async () => {
    getMyReinspectionRequests.mockResolvedValue([{
      requestKey: 'request-1',
      listingId: 101,
      requestedAt: '2026-07-31T10:00:00',
      reason: '모서리를 다시 찍어 주세요.',
      status: 'REQUESTED',
      items: [{ itemName: '외관' }],
    }])
    getProduct.mockResolvedValue({
      productId: 101,
      name: '구매자가 요청한 상품',
      price: 650000,
      sellerId: 2,
    })

    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '재촬영 요청').trigger('click')

    expect(wrapper.text()).toContain('구매자가 요청한 상품')
    expect(wrapper.text()).toContain('모서리를 다시 찍어 주세요.')
    expect(wrapper.text()).toContain('미처리')
    expect(wrapper.text()).not.toContain('재촬영 진행하기')
  })

  it('상단 안내 문구를 요청 중심 표현으로 표시한다', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('구매 희망자가 보낸 화상 확인 및 재촬영 요청을 확인할 수 있습니다.')
    await wrapper.findAll('button').find((button) => button.text() === '재촬영 요청').trigger('click')
    expect(wrapper.text()).not.toContain('실시간 검수 전 특정 부위에 대한 재검수를 요청한 내역입니다.')
  })

  it('만료된 세션을 종료에 포함하고 통화 입장 버튼을 숨긴다', async () => {
    getMyRtcCalls.mockResolvedValue([{
      ...outgoingCall,
      chatRoomId: 7,
      status: 'ACCEPTED',
      scheduledAt: '2020-01-01T00:00:00',
      rtcSessionId: 31,
      sessionExpiresAt: '2020-01-01T00:00:00',
    }])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('종료 (1)')
    expect(wrapper.text()).toContain('완료 (0)')
    expect(wrapper.text()).toContain('세션 만료')
    expect(wrapper.findAll('button').some((button) => button.text() === '통화 입장')).toBe(false)

    await wrapper.findAll('button').find((button) => button.text() === '종료 (1)').trigger('click')

    expect(wrapper.text()).not.toContain('시간 만료')
    expect(wrapper.text()).toContain('갤럭시 테스트 상품')
  })

  it('보낸 통화 약속의 시간과 메모를 변경한다', async () => {
    updateRtcCall.mockResolvedValue({ ...outgoingCall, memo: '변경된 메모' })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '일정 변경').trigger('click')
    await wrapper.get('input[type="datetime-local"]').setValue('2026-08-01T15:30')
    await wrapper.get('textarea').setValue('변경된 메모')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(updateRtcCall).toHaveBeenCalledWith(20, {
      scheduledAt: '2026-08-01T15:30:00',
      memo: '변경된 메모',
    })
    expect(getMyRtcCalls).toHaveBeenCalledTimes(2)
  })

  it('보낸 통화 약속을 사유와 함께 취소한다', async () => {
    cancelRtcCall.mockResolvedValue({ ...outgoingCall, status: 'CANCELED' })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '약속 취소').trigger('click')
    await wrapper.get('textarea').setValue('일정이 변경됐습니다.')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(cancelRtcCall).toHaveBeenCalledWith(20, '일정이 변경됐습니다.')
    expect(getMyRtcCalls).toHaveBeenCalledTimes(2)
  })
})
