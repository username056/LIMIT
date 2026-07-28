import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductDetailPage from '../ProductDetailPage.vue'
import { getProduct, getProductChecklist } from '../../api/products'
import { getFavoriteStatus, removeFavorite } from '../../api/favorites'
import { getAccessToken } from '../../auth/session'
import { createOrGetChatRoom } from '../../api/chat'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1001' }, fullPath: '/products/1001' }),
  useRouter: () => ({ push }),
}))

vi.mock('../../api/products', () => ({
  getProduct: vi.fn(),
  getProductChecklist: vi.fn(),
  requestRecapture: vi.fn(),
}))
vi.mock('../../api/favorites', () => ({
  addFavorite: vi.fn(),
  getFavoriteStatus: vi.fn(),
  removeFavorite: vi.fn(),
}))
vi.mock('../../auth/session', () => ({ getAccessToken: vi.fn() }))
vi.mock('../../api/chat', () => ({ createOrGetChatRoom: vi.fn() }))
vi.mock('../../api/rtc', () => ({ createChatRoom: vi.fn(), requestRtcCall: vi.fn() }))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  props: ['disabled', 'to'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getProduct.mockResolvedValue({
      productId: 1001,
      name: 'Galaxy S24',
      price: 650000,
      status: 'ON_SALE',
      device: {},
      checklistSummary: {},
    })
    getProductChecklist.mockResolvedValue([])
  })

  it('기존 좋아요한 상품 상태를 불러와 첫 클릭으로 해제한다', async () => {
    getAccessToken.mockReturnValue('test-token')
    getFavoriteStatus.mockResolvedValue({ favorite: true })
    removeFavorite.mockResolvedValue(undefined)
    const wrapper = mount(ProductDetailPage, {
      global: {
        stubs: {
          DefaultLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(getFavoriteStatus).toHaveBeenCalledWith('1001')
    const favoriteButton = wrapper.get('button[aria-label="좋아요한 상품 해제"]')
    await favoriteButton.trigger('click')
    await flushPromises()

    expect(removeFavorite).toHaveBeenCalledWith(1001)
    expect(wrapper.get('button[aria-label="좋아요한 상품 등록"]').exists()).toBe(true)
  })

  it('비로그인 사용자는 좋아요한 상품 클릭 시 로그인으로 이동한다', async () => {
    getAccessToken.mockReturnValue(null)
    const wrapper = mount(ProductDetailPage, {
      global: {
        stubs: {
          DefaultLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    await wrapper.get('button[aria-label="좋아요한 상품 등록"]').trigger('click')

    expect(getFavoriteStatus).not.toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith({
      name: 'login',
      query: { redirect: '/products/1001' },
    })
  })

  it('판매자 문의 버튼으로 실제 채팅방을 생성하고 이동한다', async () => {
    getAccessToken.mockReturnValue('test-token')
    getFavoriteStatus.mockResolvedValue({ favorite: false })
    createOrGetChatRoom.mockResolvedValue({ roomId: 77 })
    const wrapper = mount(ProductDetailPage, {
      global: {
        stubs: {
          DefaultLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    const inquiryButton = wrapper.findAll('button').find((button) => button.text().includes('판매자에게 문의하기'))
    await inquiryButton.trigger('click')
    await flushPromises()

    expect(createOrGetChatRoom).toHaveBeenCalledWith(1001)
    expect(push).toHaveBeenCalledWith({ name: 'chat', params: { roomId: 77 } })
  })

  it('판매 중인 상품에는 구매 화면으로 이동하는 주 행동을 표시한다', async () => {
    getAccessToken.mockReturnValue(null)
    const wrapper = mount(ProductDetailPage, {
      global: {
        stubs: {
          DefaultLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    const purchaseButton = wrapper.findAllComponents(buttonStub)
      .find((button) => button.text().includes('안전결제하고 구매하기'))

    expect(purchaseButton.props('to')).toEqual({ name: 'purchase', params: { productId: 1001 } })
    expect(purchaseButton.props('disabled')).toBe(false)
  })

  it('판매 완료 상품은 구매 행동을 비활성화한다', async () => {
    getProduct.mockResolvedValue({
      productId: 1001,
      name: 'Galaxy S24',
      price: 650000,
      status: 'SOLD',
      device: {},
      checklistSummary: {},
    })
    const wrapper = mount(ProductDetailPage, {
      global: {
        stubs: {
          DefaultLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    const purchaseButton = wrapper.findAllComponents(buttonStub)
      .find((button) => button.text().includes('판매가 완료된 상품입니다'))

    expect(purchaseButton.props('disabled')).toBe(true)
    expect(purchaseButton.props('to')).toBe('')
  })
})
