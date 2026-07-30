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
    getChatRooms.mockResolvedValue({ content: [] })
    getRtcSession.mockResolvedValue({ expiresAt: '2026-08-01T16:00:00' })
    getMyReinspectionRequests.mockResolvedValue([])
  })

  it('검증 일정 날짜와 상대방, 세션 만료 시간을 표시한다', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('상대 회원')
    expect(wrapper.text()).toContain('2026년 8월 1일')
    expect(wrapper.text()).toContain('세션 만료까지')
  })

  it('목록 응답에 정보가 없으면 채팅방과 세션 상세에서 보완한다', async () => {
    getMyRtcCalls.mockResolvedValue([{
      ...outgoingCall,
      chatRoomId: 7,
      rtcSessionId: 31,
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
