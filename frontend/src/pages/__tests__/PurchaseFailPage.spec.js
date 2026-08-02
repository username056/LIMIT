import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
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
const buttonStub = { props: ['to', 'disabled'], template: '<a><slot /></a>' }

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

  it('취소 요청이 끝나기 전에는 다시 시도 버튼이 비활성화된다', async () => {
    routeQuery.paymentId = '500'
    let resolveCancel
    cancelPayment.mockReturnValue(
      new Promise((resolve) => {
        resolveCancel = resolve
      }),
    )

    const wrapper = mount(PurchaseFailPage, {
      global: {
        stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub },
      },
    })
    await nextTick()

    const retryButtonWhilePending = wrapper.findAllComponents(buttonStub)[0]
    expect(retryButtonWhilePending.props('disabled')).toBe(true)

    resolveCancel({ paymentId: 500, status: 'CANCELLED' })
    await flushPromises()

    const retryButtonAfter = wrapper.findAllComponents(buttonStub)[0]
    expect(retryButtonAfter.props('disabled')).toBe(false)
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

  it('취소가 성공하면 다시 시도 버튼이 새 결제 경로로 간다', async () => {
    routeQuery.paymentId = '500'
    cancelPayment.mockResolvedValue({ paymentId: 500, status: 'CANCELLED' })

    const wrapper = mount(PurchaseFailPage, {
      global: {
        stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub },
      },
    })
    await flushPromises()

    const retryButton = wrapper.findAllComponents(buttonStub)[0]
    expect(retryButton.props('to')).toBe('/purchase/1001')
  })

  it('취소가 실패하면 다시 시도 버튼이 같은 예약을 재사용하는 경로로 간다', async () => {
    routeQuery.paymentId = '500'
    cancelPayment.mockRejectedValue(new Error('network error'))

    const wrapper = mount(PurchaseFailPage, {
      global: {
        stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub },
      },
    })
    await flushPromises()

    const retryButton = wrapper.findAllComponents(buttonStub)[0]
    expect(retryButton.props('to')).toEqual({
      path: '/purchase/1001',
      query: { retryPaymentId: '500' },
    })
  })
})
