import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PurchaseSuccessPage from '../PurchaseSuccessPage.vue'
import { confirmPayment } from '../../api/payment'

const routeQuery = {
  paymentId: '500',
  paymentKey: 'pk-1',
  orderId: 'PAY-500-1',
  amount: '650000',
  address: '서울시 강남구 테헤란로 123',
  productName: 'Galaxy S24',
  manufacturer: 'Samsung',
}

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1001' }, query: routeQuery }),
}))
vi.mock('../../api/payment', () => ({ confirmPayment: vi.fn() }))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  props: ['disabled', 'to'],
  template: '<a><slot /></a>',
}

function mountPage() {
  return mount(PurchaseSuccessPage, {
    global: {
      stubs: {
        DefaultLayout: layoutStub,
        BaseButton: buttonStub,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

describe('PurchaseSuccessPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('결제 승인에 성공하면 결과를 보여준다', async () => {
    confirmPayment.mockResolvedValue({ paymentId: 500, approvedAmount: 650000 })

    const wrapper = mountPage()
    await flushPromises()

    expect(confirmPayment).toHaveBeenCalledWith('500', {
      paymentKey: 'pk-1',
      orderId: 'PAY-500-1',
      amount: 650000,
    })
    expect(wrapper.text()).toContain('구매에 성공하셨습니다')
    expect(wrapper.text()).toContain('Galaxy S24')
    expect(wrapper.text()).toContain('Samsung')
  })

  it('결제 승인에 실패하면 오류와 다시 시도 버튼을 보여준다', async () => {
    confirmPayment.mockRejectedValue(new Error('카드 승인이 거절되었습니다.'))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('결제 승인에 실패했습니다')
    expect(wrapper.text()).toContain('카드 승인이 거절되었습니다.')
  })
})
