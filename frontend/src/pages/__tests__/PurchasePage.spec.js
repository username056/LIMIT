import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PurchasePage from '../PurchasePage.vue'
import { getProduct } from '../../api/products'
import { createPayment, getPayment, retryPayment } from '../../api/payment'
import { getAccessToken } from '../../auth/session'
import { getMyProfile } from '../../api/member'

const routeState = { query: {} }
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1001' }, fullPath: '/purchase/1001', query: routeState.query }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('../../api/products', () => ({ getProduct: vi.fn() }))
vi.mock('../../api/payment', () => ({
  createPayment: vi.fn(),
  retryPayment: vi.fn(),
  getPayment: vi.fn(),
}))
vi.mock('../../api/member', () => ({ getMyProfile: vi.fn() }))
vi.mock('../../auth/session', () => ({ getAccessToken: vi.fn() }))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  props: ['disabled', 'to'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

function mountPage() {
  return mount(PurchasePage, {
    global: {
      stubs: {
        DefaultLayout: layoutStub,
        BaseButton: buttonStub,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

describe('PurchasePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.query = {}
    getAccessToken.mockReturnValue('test-token')
    getProduct.mockResolvedValue({
      productId: 1001,
      name: 'Galaxy S24',
      price: 650000,
      device: { manufacturer: 'Samsung' },
    })
    getPayment.mockResolvedValue({ paymentId: 500, listingId: 1001 })
    vi.stubGlobal('crypto', { randomUUID: () => 'test-idempotency-key' })
    vi.stubEnv('VITE_TOSS_PAYMENTS_CLIENT_KEY', 'test_ck_1234')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('결제 요청 생성 후 Toss 결제창을 연다', async () => {
    createPayment.mockResolvedValue({
      paymentId: 500,
      providerOrderId: 'PAY-500-1',
      requestedAmount: 650000,
    })
    const requestPayment = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))

    const wrapper = mountPage()
    await flushPromises()

    const payButton = wrapper.findAll('button').find((button) => button.text().includes('결제하기'))
    await payButton.trigger('click')
    await flushPromises()

    expect(createPayment).toHaveBeenCalledWith({
      listingId: 1001,
      method: 'CARD',
      idempotencyKey: 'test-idempotency-key',
    })
    expect(requestPayment).toHaveBeenCalledWith(
      '카드',
      expect.objectContaining({ amount: 650000, orderId: 'PAY-500-1' }),
    )
  })

  it('결제 요청이 실패하면 오류 메시지를 보여주고 결제창을 열지 않는다', async () => {
    createPayment.mockRejectedValue(new Error('결제 요청에 실패했습니다.'))
    const tossPayments = vi.fn()
    vi.stubGlobal('TossPayments', tossPayments)

    const wrapper = mountPage()
    await flushPromises()

    const payButton = wrapper.findAll('button').find((button) => button.text().includes('결제하기'))
    await payButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('결제 요청에 실패했습니다.')
    expect(tossPayments).not.toHaveBeenCalled()
  })

  it('retryPaymentId가 있으면 새 결제 대신 같은 예약을 재사용한다', async () => {
    routeState.query = { retryPaymentId: '500' }
    retryPayment.mockResolvedValue({
      paymentId: 500,
      providerOrderId: 'PAY-500-2',
      requestedAmount: 650000,
    })
    const requestPayment = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))

    const wrapper = mountPage()
    await flushPromises()

    const payButton = wrapper.findAll('button').find((button) => button.text().includes('결제하기'))
    await payButton.trigger('click')
    await flushPromises()

    expect(retryPayment).toHaveBeenCalledWith('500', 'CARD')
    expect(createPayment).not.toHaveBeenCalled()
    expect(requestPayment).toHaveBeenCalledWith(
      '카드',
      expect.objectContaining({ amount: 650000, orderId: 'PAY-500-2' }),
    )
  })

  it('retry 시 결제수단을 바꾸면 바뀐 수단으로 retryPayment를 호출한다', async () => {
    routeState.query = { retryPaymentId: '500' }
    retryPayment.mockResolvedValue({
      paymentId: 500,
      providerOrderId: 'PAY-500-2',
      requestedAmount: 650000,
    })
    const requestPayment = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))

    const wrapper = mountPage()
    await flushPromises()

    const tossPayButton = wrapper.findAll('button').find((button) => button.text() === '토스페이')
    await tossPayButton.trigger('click')
    const payButton = wrapper.findAll('button').find((button) => button.text().includes('결제하기'))
    await payButton.trigger('click')
    await flushPromises()

    expect(retryPayment).toHaveBeenCalledWith('500', 'TOSSPAY')
  })

  it('재시도 결제의 listingId가 현재 상품과 다르면 결제를 막는다', async () => {
    routeState.query = { retryPaymentId: '500' }
    getPayment.mockResolvedValue({ paymentId: 500, listingId: 9999 })
    const requestPayment = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))

    const wrapper = mountPage()
    await flushPromises()

    expect(getPayment).toHaveBeenCalledWith('500')
    expect(wrapper.text()).toContain('재시도할 결제와 현재 상품 정보가 일치하지 않습니다')
    expect(wrapper.findAll('button').find((button) => button.text().includes('결제하기'))).toBeUndefined()

    expect(retryPayment).not.toHaveBeenCalled()
    expect(requestPayment).not.toHaveBeenCalled()
  })
})
