import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductDetailPage from '../ProductDetailPage.vue'
import { getMyProduct, getProduct, getProductChecklist } from '../../api/products'
import { getFavoriteStatus, removeFavorite } from '../../api/favorites'
import { getAccessToken, getSessionMember } from '../../auth/session'
import { createOrGetChatRoom } from '../../api/chat'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1001' }, fullPath: '/products/1001' }),
  useRouter: () => ({ push }),
}))

vi.mock('../../api/products', () => ({
  getMyProduct: vi.fn(),
  getProduct: vi.fn(),
  getProductChecklist: vi.fn(),
  requestRecapture: vi.fn(),
}))
vi.mock('../../api/favorites', () => ({
  addFavorite: vi.fn(),
  getFavoriteStatus: vi.fn(),
  removeFavorite: vi.fn(),
}))
vi.mock('../../auth/session', () => ({ getAccessToken: vi.fn(), getSessionMember: vi.fn() }))
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
      .find((button) => button.text().includes('상품 구매하기'))

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

  it('상세에서 거래 지역 항목을 보여주지 않는다', async () => {
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

    expect(wrapper.text()).not.toContain('거래 지역')
  })

  it('본인이 등록한 상품에는 구매·문의 대신 수정 동선을 보여준다', async () => {
    getAccessToken.mockReturnValue('test-token')
    getSessionMember.mockReturnValue({ memberId: 55 })
    getProduct.mockResolvedValue({
      productId: 1001,
      sellerId: 55,
      name: '내가 등록한 갤럭시 북',
      price: 850000,
      status: 'ON_SALE',
      device: {},
      checklistSummary: {},
    })
    getFavoriteStatus.mockResolvedValue({ favorite: false })

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

    const editButton = wrapper.findAllComponents(buttonStub)
      .find((button) => button.text().includes('수정하기'))
    expect(editButton.props('to')).toEqual({ name: 'seller-product-edit', params: { productId: 1001 } })
    expect(wrapper.text()).not.toContain('상품 구매하기')
    expect(wrapper.text()).not.toContain('판매자에게 문의하기')
  })

  it('공개 조회가 실패하면 판매자 본인의 초안 상품을 소유자 조회로 보여준다', async () => {
    getAccessToken.mockReturnValue('test-token')
    getProduct.mockRejectedValue(new Error('상품을 찾을 수 없습니다.'))
    getMyProduct.mockResolvedValue({
      productId: 1001,
      name: '초안 갤럭시 북',
      price: 850000,
      status: 'DRAFT',
      device: {},
      checklistSummary: {},
    })
    getFavoriteStatus.mockResolvedValue({ favorite: false })

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

    expect(getMyProduct).toHaveBeenCalledWith('1001')
    expect(wrapper.text()).toContain('초안 갤럭시 북')
    expect(wrapper.text()).toContain('임시 저장 중인 상품이라')
    expect(wrapper.text()).not.toContain('상품을 찾을 수 없습니다.')
  })

  it('소유자도 아니면 공개 조회 실패 사유를 그대로 보여준다', async () => {
    getAccessToken.mockReturnValue('test-token')
    getProduct.mockRejectedValue(new Error('상품을 찾을 수 없습니다.'))
    getMyProduct.mockRejectedValue(new Error('권한이 없습니다.'))

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

    expect(wrapper.text()).toContain('상품을 찾을 수 없습니다.')
    expect(wrapper.text()).not.toContain('권한이 없습니다.')
  })
})
