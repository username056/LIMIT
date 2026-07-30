import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PurchaseFailPage from '../PurchaseFailPage.vue'

const routeQuery = { message: 'Toss 결제창에서 취소되었습니다.' }

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { productId: '1001' },
    query: routeQuery,
  }),
}))
vi.mock('../../api/payment', () => ({ cancelPayment: vi.fn() }))

import { cancelPayment } from '../../api/payment'

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = { props: ['to'], template: '<a><slot /></a>' }

describe('PurchaseFailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    delete routeQuery.paymentId
  })

  it('결제 실패 사유와 다시 시도 링크를 보여준다', () => {
    const wrapper = mount(PurchaseFailPage, {
      global: {
        stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub },
      },
    })

    expect(wrapper.text()).toContain('결제가 완료되지 않았습니다')
    expect(wrapper.text()).toContain('Toss 결제창에서 취소되었습니다.')
    expect(cancelPayment).not.toHaveBeenCalled()
  })

  it('paymentId가 있으면 결제 전 예약 취소 API를 호출한다', async () => {
    routeQuery.paymentId = '500'
    cancelPayment.mockResolvedValue({ paymentId: 500, status: 'CANCELLED' })

    mount(PurchaseFailPage, {
      global: {
        stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub },
      },
    })
    await flushPromises()

    expect(cancelPayment).toHaveBeenCalledWith('500')
  })

  it('취소 API가 실패해도 화면은 그대로 실패 안내를 보여준다', async () => {
    routeQuery.paymentId = '500'
    cancelPayment.mockRejectedValue(new Error('network error'))

    const wrapper = mount(PurchaseFailPage, {
      global: {
        stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('결제가 완료되지 않았습니다')
  })
})
