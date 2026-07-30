import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CallsPage from '../CallsPage.vue'
import { getChatRooms } from '../../api/chat'
import { cancelRtcCall, getMyRtcCalls, getRtcSession, updateRtcCall } from '../../api/rtc'
import { getMyReinspectionRequests } from '../../api/products'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
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
}))

const outgoingCall = {
  callId: 20,
  status: 'PROPOSED',
  incoming: false,
  scheduledAt: '2026-08-01T14:00:00',
  memo: '제품 상태 확인',
  rtcSessionId: null,
  proposerId: 1,
  respondentId: 2,
  counterpartName: '상대 회원',
  sessionExpiresAt: '2026-08-01T16:00:00',
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
    getRtcSession.mockResolvedValue({ expiresAt: '2026-08-01T16:00:00' })
    getMyReinspectionRequests.mockResolvedValue([])
  })

  it('상품별로 요청을 묶고 전체, 거절, 완료 상태로 필터링한다', async () => {
    getMyRtcCalls.mockResolvedValue([
      { ...outgoingCall, callId: 20, chatRoomId: 7, status: 'PROPOSED' },
      { ...outgoingCall, callId: 21, chatRoomId: 7, status: 'REJECTED' },
      { ...outgoingCall, callId: 22, chatRoomId: 7, status: 'COMPLETED' },
    ])

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('갤럭시 테스트 상품')
    expect(wrapper.text()).toContain('요청 3건')
    expect(wrapper.text()).toContain('전체 목록 (3)')
    expect(wrapper.text()).toContain('대기 (1)')
    expect(wrapper.text()).toContain('거절 (1)')
    expect(wrapper.text()).toContain('완료 (1)')

    await wrapper.findAll('button').find((button) => button.text() === '대기 (1)').trigger('click')

    expect(wrapper.text()).toContain('영상 확인 요청 #20')
    expect(wrapper.text()).not.toContain('영상 확인 요청 #21')
    expect(wrapper.text()).not.toContain('영상 확인 요청 #22')

    await wrapper.findAll('button').find((button) => button.text() === '거절 (1)').trigger('click')

    expect(wrapper.text()).toContain('영상 확인 요청 #21')
    expect(wrapper.text()).not.toContain('영상 확인 요청 #20')
    expect(wrapper.text()).not.toContain('영상 확인 요청 #22')
  })

  it('검증 일정 날짜와 상대방, 세션 만료 시간을 표시한다', async () => {
    getMyRtcCalls.mockResolvedValue([{ ...outgoingCall, status: 'ACCEPTED' }])
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('상대 회원')
    expect(wrapper.text()).toContain('2026년 8월 1일')
    expect(wrapper.text()).toContain('세션 만료까지')
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
    expect(wrapper.text()).toContain('세션 만료까지')
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
    expect(wrapper.findAll('button').some((button) => button.text() === '통화 입장')).toBe(false)

    await wrapper.findAll('button').find((button) => button.text() === '완료 (1)').trigger('click')

    expect(wrapper.text()).toContain('영상 확인 요청 #20')
  })

  it('보낸 통화 약속의 시간과 메모를 변경한다', async () => {
    updateRtcCall.mockResolvedValue({ ...outgoingCall, memo: '변경된 메모' })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '약속 변경').trigger('click')
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
