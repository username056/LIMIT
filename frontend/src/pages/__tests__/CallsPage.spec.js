import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CallsPage from '../CallsPage.vue'
import { getChatRooms } from '../../api/chat'
import { cancelRtcCall, getMyRtcCalls, getRtcSession, updateRtcCall } from '../../api/rtc'
import { getMyProducts, getMyReinspectionRequests } from '../../api/products'

const routerPush = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  // 상품 링크(사진·제목)가 RouterLink를 쓰므로 최소 형태로 흉내 냅니다.
  RouterLink: {
    props: { to: { type: [String, Object], default: '' } },
    template: '<a :data-to="JSON.stringify(to)"><slot /></a>',
  },
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
  getMyProducts: vi.fn(),
  getMyReinspectionRequests: vi.fn(),
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
    getMyProducts.mockResolvedValue({ data: [], meta: {} })
  })

  it('화상 확인과 재촬영을 한 목록에 모으고 탭으로 걸러 낸다', async () => {
    getMyRtcCalls.mockResolvedValue([
      { ...outgoingCall, callId: 20, chatRoomId: 7, status: 'PROPOSED' },
      { ...outgoingCall, callId: 21, chatRoomId: 7, status: 'REJECTED' },
      { ...outgoingCall, callId: 22, chatRoomId: 7, status: 'COMPLETED' },
    ])

    const wrapper = mountPage()
    await flushPromises()

    // 화상 확인과 재촬영을 한 목록으로 합쳤습니다. 탭은 걸러 보는 용도이고,
    // 각 탭 라벨 옆에 지금 걸리는 건수를 답니다.
    // 거절된 약속은 손댈 것이 없으므로 '실시간 확인'이 아니라 '완료'로 셉니다.
    expect(wrapper.text()).toContain('갤럭시 테스트 상품')
    expect(wrapper.text()).toContain('전체 (3)')
    expect(wrapper.text()).toContain('실시간 확인 (1)')
    expect(wrapper.text()).toContain('완료 (2)')

    // 완료 탭에는 끝난 것만 남고, 진행 중인 건은 빠집니다.
    await wrapper.findAll('button').find((button) => button.text().startsWith('완료')).trigger('click')

    expect(wrapper.findAll('.request')).toHaveLength(2)
    expect(wrapper.text()).toContain('확인 완료')
    expect(wrapper.text()).toContain('거절됨')
    expect(wrapper.text()).not.toContain('상대 응답 대기')
  })

  it('서버 철자 그대로인 CANCELED 약속을 취소됨으로 읽고 완료로 옮긴다', async () => {
    // 서버 enum은 AppointmentStatus.CANCELED(L 하나)입니다. 예전에는 CANCELLED만
    // 보고 있어서 취소된 약속이 '일정 확정'으로 남아 있었습니다.
    getMyRtcCalls.mockResolvedValue([{ ...outgoingCall, chatRoomId: 7, status: 'CANCELED' }])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('취소됨')
    expect(wrapper.text()).not.toContain('일정 확정')
    expect(wrapper.text()).toContain('완료 (1)')
    expect(wrapper.text()).toContain('실시간 확인 (0)')
  })

  it('재촬영 요청에 내 상품의 이름과 대표 사진을 붙인다', async () => {
    // 재검수 응답에는 listingId만 있어서, 내 상품 목록에서 이름과 사진을 끌어옵니다.
    getMyRtcCalls.mockResolvedValue([])
    getMyReinspectionRequests.mockResolvedValue([{
      requestKey: 'rq-1',
      listingId: 101,
      status: 'REQUESTED',
      reason: '뒷면을 다시 찍어 주세요.',
      items: [{ checklistItemId: 5, itemName: '후면 상태', requestContent: '', displayOrder: 1 }],
      requestedAt: '2026-08-01T10:00:00',
    }])
    getMyProducts.mockResolvedValue({
      data: [{ productId: 101, name: '갤럭시 Z 플립5', thumbnailUrl: 'https://example.test/flip.jpg' }],
      meta: {},
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('갤럭시 Z 플립5')
    expect(wrapper.text()).not.toContain('상품 #101')
    expect(wrapper.get('.request__thumb img').attributes('src')).toBe('https://example.test/flip.jpg')
  })

  it('내 상품 목록을 못 받아도 재촬영 요청은 그대로 보여 준다', async () => {
    getMyRtcCalls.mockResolvedValue([])
    getMyReinspectionRequests.mockResolvedValue([{
      requestKey: 'rq-2',
      listingId: 202,
      status: 'REQUESTED',
      reason: '측면 흠집 확인',
      items: [{ checklistItemId: 6, itemName: '측면 상태', requestContent: '', displayOrder: 1 }],
      requestedAt: '2026-08-01T10:00:00',
    }])
    getMyProducts.mockRejectedValue(new Error('목록 조회 실패'))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.findAll('.request')).toHaveLength(1)
    expect(wrapper.text()).toContain('상품 #202')
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

  it('만료된 세션을 완료에 포함하고 통화 입장 버튼을 숨긴다', async () => {
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

    expect(wrapper.text()).toContain('완료 (1)')
    expect(wrapper.text()).toContain('세션 만료')
    expect(wrapper.findAll('button').some((button) => button.text() === '화상 입장')).toBe(false)

    await wrapper.findAll('button').find((button) => button.text().startsWith('완료')).trigger('click')

    // 시간이 지나 끝난 건도 완료 탭에 남아 있어야 합니다.
    expect(wrapper.findAll('.request')).toHaveLength(1)
    expect(wrapper.text()).toContain('시간 만료')
  })

  it('보낸 요청의 작업을 접지 않고 모두 펼쳐 놓는다', async () => {
    getMyRtcCalls.mockResolvedValue([{ ...outgoingCall, chatRoomId: 7 }])

    const wrapper = mountPage()
    await flushPromises()

    const labels = wrapper.findAll('.link').map((link) => link.text())
    expect(labels).toEqual(['채팅 열기', '약속 취소'])
  })

  it('상품 사진과 제목이 상품 상세로 가는 링크가 된다', async () => {
    getMyRtcCalls.mockResolvedValue([{ ...outgoingCall, chatRoomId: 7 }])

    const wrapper = mountPage()
    await flushPromises()

    // 채팅방에서 listingId 101을 받아 옵니다.
    const route = { name: 'product-detail', params: { productId: 101 } }
    expect(wrapper.get('.request__thumb').attributes('data-to')).toBe(JSON.stringify(route))
    expect(wrapper.get('.request__nameLink').attributes('data-to')).toBe(JSON.stringify(route))
  })

  it('약속 시각이 지난 요청은 수락 버튼을 감추고 시간 지남으로 알린다', async () => {
    // 서버는 약속 시각 +30분이 지나면 수락을 거부합니다(RTC_SESSION_EXPIRED).
    getMyRtcCalls.mockResolvedValue([{
      ...outgoingCall,
      chatRoomId: 7,
      incoming: true,
      status: 'PROPOSED',
      scheduledAt: '2020-01-01T00:00:00',
    }])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('시간 지남')
    expect(wrapper.findAll('button').some((button) => button.text() === '수락하기')).toBe(false)

    // 아직 판매자가 정리해야 할 건이라 완료로 치우지 않습니다.
    expect(wrapper.text()).toContain('실시간 확인 (1)')
    expect(wrapper.text()).toContain('완료 (0)')
  })

  it('취소된 재촬영 요청을 완료가 아니라 요청 취소로 읽는다', async () => {
    getMyRtcCalls.mockResolvedValue([])
    getMyReinspectionRequests.mockResolvedValue([{
      requestKey: 'rq-3',
      listingId: 303,
      status: 'CANCELED',
      reason: '구매자가 요청을 물렸습니다.',
      items: [{ checklistItemId: 7, itemName: '후면 상태', requestContent: '', displayOrder: 1 }],
      requestedAt: '2026-08-01T10:00:00',
    }])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('요청 취소')
    expect(wrapper.text()).not.toContain('재촬영 완료')
    expect(wrapper.text()).toContain('완료 (1)')
    expect(wrapper.text()).toContain('재촬영 요청 (0)')
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
