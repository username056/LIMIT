import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PurchasePage from '../PurchasePage.vue'
import { getProduct } from '../../api/products'
import { cancelPayment, createPayment, getPayment, retryPayment } from '../../api/payment'
import { getAccessToken } from '../../auth/session'
import { getMyProfile } from '../../api/member'

const routeState = { query: {} }
const mockRouter = { push: vi.fn(), replace: vi.fn() }
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1001' }, fullPath: '/purchase/1001', query: routeState.query }),
  useRouter: () => mockRouter,
}))
vi.mock('../../api/products', () => ({ getProduct: vi.fn() }))
vi.mock('../../api/payment', () => ({
  createPayment: vi.fn(),
  retryPayment: vi.fn(),
  getPayment: vi.fn(),
  cancelPayment: vi.fn(),
}))
vi.mock('../../api/member', () => ({ getMyProfile: vi.fn() }))
vi.mock('../../auth/session', () => ({ getAccessToken: vi.fn() }))

function tossError(code, message) {
  return Object.assign(new Error(message), { code })
}

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

/*
  배송지를 채웁니다.
  ---------------------------------------------------------------------------
  예전에는 '김싸피'·'010-1234-5678'·주소가 값으로 박혀 있어서, 아무것도 입력하지
  않아도 결제가 진행됐습니다. 그래서 이 테스트들도 배송지를 채우지 않고 있었는데,
  실제 사용자는 빈 화면에서 시작하므로 그 경로를 검증하지 못하고 있었습니다.
*/
async function fillShippingInfo(wrapper) {
  const [name, phone] = wrapper.findAll('input[type="text"], input[type="tel"]')
  await name.setValue('김구매')
  await phone.setValue('010-0000-0000')

  // 주소는 우편번호 검색으로만 채워지므로(읽기 전용) 컴포넌트에 직접 넘깁니다.
  await wrapper.findComponent({ name: 'BaseAddressInput' }).vm.$emit('update:modelValue', {
    zonecode: '12345',
    address: '서울시 강남구 테헤란로 123',
    addressDetail: '4층',
  })
  await flushPromises()
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

    await fillShippingInfo(wrapper)

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

    await fillShippingInfo(wrapper)

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

    await fillShippingInfo(wrapper)

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
    await fillShippingInfo(wrapper)

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

  it('사용자가 결제창을 닫으면(PAY_PROCESS_CANCELED) 예약을 즉시 취소하고 안내한다', async () => {
    createPayment.mockResolvedValue({ paymentId: 500, providerOrderId: 'PAY-500-1', requestedAmount: 650000 })
    const requestPayment = vi.fn().mockRejectedValue(tossError('PAY_PROCESS_CANCELED', '사용자가 결제를 취소했습니다.'))
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))
    cancelPayment.mockResolvedValue({ paymentId: 500, status: 'CANCELLED' })

    const wrapper = mountPage()
    await flushPromises()
    await fillShippingInfo(wrapper)
    await (wrapper.findAll('button').find((button) => button.text().includes('결제하기'))).trigger('click')
    await flushPromises()

    expect(cancelPayment).toHaveBeenCalledWith(500)
    expect(wrapper.text()).toContain('결제가 취소되었습니다')
  })

  it('결제창이 승인 없이 중단되면(PAY_PROCESS_ABORTED) 예약을 즉시 취소한다', async () => {
    createPayment.mockResolvedValue({ paymentId: 500, providerOrderId: 'PAY-500-1', requestedAmount: 650000 })
    const requestPayment = vi.fn().mockRejectedValue(tossError('PAY_PROCESS_ABORTED', '승인 없이 중단되었습니다.'))
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))
    cancelPayment.mockResolvedValue({ paymentId: 500, status: 'CANCELLED' })

    const wrapper = mountPage()
    await flushPromises()
    await fillShippingInfo(wrapper)
    await (wrapper.findAll('button').find((button) => button.text().includes('결제하기'))).trigger('click')
    await flushPromises()

    expect(cancelPayment).toHaveBeenCalledWith(500)
    expect(wrapper.text()).toContain('결제가 취소되었습니다')
  })

  it('취소 API마저 실패하면 자동 해제 안내를 보여주고 같은 예약으로 재시도하도록 남겨둔다', async () => {
    createPayment.mockResolvedValue({ paymentId: 500, providerOrderId: 'PAY-500-1', requestedAmount: 650000 })
    const requestPayment = vi.fn().mockRejectedValue(tossError('PAY_PROCESS_CANCELED', '사용자가 결제를 취소했습니다.'))
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))
    cancelPayment.mockRejectedValue(new Error('network error'))

    const wrapper = mountPage()
    await flushPromises()
    await fillShippingInfo(wrapper)
    await (wrapper.findAll('button').find((button) => button.text().includes('결제하기'))).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('결제는 완료되지 않았습니다')
    expect(mockRouter.replace).toHaveBeenCalledWith({ query: { retryPaymentId: '500' } })
  })

  it('취소 시점에 이미 승인된 결제라면(PAY015) 자동해제·재시도 안내 없이 사실대로 알린다', async () => {
    createPayment.mockResolvedValue({ paymentId: 500, providerOrderId: 'PAY-500-1', requestedAmount: 650000 })
    const requestPayment = vi.fn().mockRejectedValue(tossError('PAY_PROCESS_CANCELED', '사용자가 결제를 취소했습니다.'))
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))
    cancelPayment.mockRejectedValue(tossError('PAY015', '요청 상태의 결제만 취소할 수 있습니다.'))

    const wrapper = mountPage()
    await flushPromises()
    await fillShippingInfo(wrapper)
    await (wrapper.findAll('button').find((button) => button.text().includes('결제하기'))).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 처리된 결제입니다')
    expect(wrapper.text()).not.toContain('결제는 완료되지 않았습니다')
    expect(mockRouter.replace).not.toHaveBeenCalled()
  })

  it('승인 여부가 불명확한 오류에서는 취소 API를 호출하지 않는다', async () => {
    createPayment.mockResolvedValue({ paymentId: 500, providerOrderId: 'PAY-500-1', requestedAmount: 650000 })
    const requestPayment = vi.fn().mockRejectedValue(new Error('네트워크 오류가 발생했습니다.'))
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))

    const wrapper = mountPage()
    await flushPromises()
    await fillShippingInfo(wrapper)
    await (wrapper.findAll('button').find((button) => button.text().includes('결제하기'))).trigger('click')
    await flushPromises()

    expect(cancelPayment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('네트워크 오류가 발생했습니다.')
  })

  it('취소가 성공하면 재시도 경로로 들어온 retryPaymentId를 지운다', async () => {
    routeState.query = { retryPaymentId: '500' }
    retryPayment.mockResolvedValue({ paymentId: 500, providerOrderId: 'PAY-500-2', requestedAmount: 650000 })
    const requestPayment = vi.fn().mockRejectedValue(tossError('PAY_PROCESS_CANCELED', '사용자가 결제를 취소했습니다.'))
    vi.stubGlobal('TossPayments', vi.fn(() => ({ requestPayment })))
    cancelPayment.mockResolvedValue({ paymentId: 500, status: 'CANCELLED' })

    const wrapper = mountPage()
    await flushPromises()
    await fillShippingInfo(wrapper)
    await (wrapper.findAll('button').find((button) => button.text().includes('결제하기'))).trigger('click')
    await flushPromises()

    expect(mockRouter.replace).toHaveBeenCalledWith({ query: {} })
  })
})
