import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductDetailPage from '../ProductDetailPage.vue'
import { getProduct } from '../../api/products'
import { getFavoriteStatus, removeFavorite } from '../../api/favorites'
import { getAccessToken } from '../../auth/session'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { productId: '1001' }, fullPath: '/products/1001' }),
  useRouter: () => ({ push }),
}))

vi.mock('../../api/products', () => ({ getProduct: vi.fn() }))
vi.mock('../../api/favorites', () => ({
  addFavorite: vi.fn(),
  getFavoriteStatus: vi.fn(),
  removeFavorite: vi.fn(),
}))
vi.mock('../../auth/session', () => ({ getAccessToken: vi.fn() }))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  props: ['disabled'],
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
  })

  it('기존 관심 상품 상태를 불러와 첫 클릭으로 해제한다', async () => {
    getAccessToken.mockReturnValue('test-token')
    getFavoriteStatus.mockResolvedValue({ favorite: true })
    removeFavorite.mockResolvedValue(undefined)
    const wrapper = mount(ProductDetailPage, {
      global: { stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub } },
    })
    await flushPromises()

    expect(getFavoriteStatus).toHaveBeenCalledWith('1001')
    expect(wrapper.find('button').text()).toBe('관심 상품 해제')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(removeFavorite).toHaveBeenCalledWith(1001)
    expect(wrapper.find('button').text()).toBe('관심 상품 등록')
  })

  it('비로그인 사용자는 관심 상품 클릭 시 로그인으로 이동한다', async () => {
    getAccessToken.mockReturnValue(null)
    const wrapper = mount(ProductDetailPage, {
      global: { stubs: { DefaultLayout: layoutStub, BaseButton: buttonStub } },
    })
    await flushPromises()

    await wrapper.find('button').trigger('click')

    expect(getFavoriteStatus).not.toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith({
      name: 'login',
      query: { redirect: '/products/1001' },
    })
  })
})
