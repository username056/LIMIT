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
  template: '<button type="button"><slot /></button>',
}

function apiError(message, code) {
  return Object.assign(new Error(message), { code })
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

  it('일시적 오류(PAY011)면 같은 결제로 다시 승인을 시도할 수 있다', async () => {
    confirmPayment
      .mockRejectedValueOnce(apiError('일시적인 오류로 승인에 실패했습니다.', 'PAY011'))
      .mockResolvedValueOnce({ paymentId: 500, approvedAmount: 650000 })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('결제 승인에 실패했습니다')
    expect(wrapper.text()).toContain('다시 승인 시도')
    expect(wrapper.text()).not.toContain('주문 내역에서 확인하기')

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(confirmPayment).toHaveBeenCalledTimes(2)
    expect(confirmPayment).toHaveBeenNthCalledWith(2, '500', {
      paymentKey: 'pk-1',
      orderId: 'PAY-500-1',
      amount: 650000,
    })
    expect(wrapper.text()).toContain('구매에 성공하셨습니다')
  })

  it('명확한 거절(PAY012)이면 새 결제수단으로 다시 결제하도록 안내한다', async () => {
    confirmPayment.mockRejectedValue(apiError('카드 승인이 거절되었습니다.', 'PAY012'))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('결제 승인에 실패했습니다')
    expect(wrapper.text()).toContain('다른 결제수단으로 다시 결제')
    expect(wrapper.text()).not.toContain('다시 승인 시도')
    expect(wrapper.text()).not.toContain('주문 내역에서 확인하기')
  })

  it('확정 실패(PAY016)면 새 결제로 유도하지 않고 주문 내역·상품 상세로만 안내한다', async () => {
    confirmPayment.mockRejectedValue(
      apiError('이미 승인된 결제이지만 요청한 결제 키가 승인 기록과 일치하지 않습니다.', 'PAY016'),
    )

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('결제 승인에 실패했습니다')
    expect(wrapper.text()).toContain('일치하지 않습니다')
    expect(wrapper.text()).not.toContain('다시 승인 시도')
    expect(wrapper.text()).toContain('주문 내역에서 확인하기')
    expect(wrapper.text()).toContain('상품 상세로 돌아가기')
  })

  it('오류 코드가 없는 확정 실패도 주문 내역·상품 상세로만 안내한다', async () => {
    confirmPayment.mockRejectedValue(new Error('카드 승인이 거절되었습니다.'))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('결제 승인에 실패했습니다')
    expect(wrapper.text()).toContain('카드 승인이 거절되었습니다.')
    expect(wrapper.text()).not.toContain('다시 승인 시도')
    expect(wrapper.text()).toContain('주문 내역에서 확인하기')
  })
})
