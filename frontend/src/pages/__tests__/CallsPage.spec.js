import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CallsPage from '../CallsPage.vue'
import { cancelRtcCall, getMyRtcCalls, updateRtcCall } from '../../api/rtc'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../../api/rtc', () => ({
  cancelRtcCall: vi.fn(),
  getMyRtcCalls: vi.fn(),
  respondRtcCall: vi.fn(),
  updateRtcCall: vi.fn(),
}))

const outgoingCall = {
  callId: 20,
  status: 'PROPOSED',
  incoming: false,
  scheduledAt: '2026-08-01T14:00:00',
  memo: '제품 상태 확인',
  rtcSessionId: null,
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
