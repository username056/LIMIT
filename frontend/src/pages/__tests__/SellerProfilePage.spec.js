import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SellerProfilePage from '../SellerProfilePage.vue'
import { getSellerProfile } from '../../api/seller'
import { getProducts } from '../../api/products'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { sellerId: '55' } }),
}))

vi.mock('../../api/seller', () => ({ getSellerProfile: vi.fn() }))
vi.mock('../../api/products', () => ({ getProducts: vi.fn() }))

const globalOptions = {
  stubs: {
    DefaultLayout: { template: '<main><slot /></main>' },
    BaseBadge: { template: '<span><slot /></span>' },
    BaseButton: {
      props: ['disabled'],
      emits: ['click'],
      template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
    },
    RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' },
  },
}

describe('SellerProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getSellerProfile.mockResolvedValue({
      sellerId: 55,
      nickname: '리미트판매자',
      sellerType: 'INDIVIDUAL',
      joinedAt: '2026-07-20T10:00:00+09:00',
      onSaleCount: 2,
    })
    getProducts.mockResolvedValue({
      data: [{
        productId: 1001,
        name: 'Galaxy S24 팝니다',
        manufacturerName: 'Samsung',
        modelName: 'Galaxy S24',
        price: 650000,
        status: 'ON_SALE',
        thumbnailUrl: 'https://cdn.example.com/1001.jpg',
      }],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })
  })

  // 그 판매자의 상품만 보여야 하므로 sellerId로 걸러서 조회합니다.
  it('판매자 프로필과 그 사람의 판매 목록만 조회한다', async () => {
    const wrapper = mount(SellerProfilePage, { global: globalOptions })
    await flushPromises()

    expect(getSellerProfile).toHaveBeenCalledWith(55)
    expect(getProducts).toHaveBeenCalledWith({
      sellerId: 55,
      page: 0,
      size: 12,
      sort: 'createdAt,desc',
    })
    expect(wrapper.text()).toContain('리미트판매자')
    expect(wrapper.text()).toContain('개인 판매자')
    expect(wrapper.text()).toContain('판매 중 2개')
  })

  it('상품 목록과 같은 카드로 대표 이미지를 보여준다', async () => {
    const wrapper = mount(SellerProfilePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.get('img').attributes('src')).toBe('https://cdn.example.com/1001.jpg')
    expect(wrapper.text()).toContain('Galaxy S24 팝니다')
    expect(wrapper.text()).toContain('650,000원')
  })

  it('판매 중인 상품이 없으면 안내를 보여준다', async () => {
    getProducts.mockResolvedValue({
      data: [],
      meta: { page: 0, totalPages: 0, hasNext: false },
    })

    const wrapper = mount(SellerProfilePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).toContain('지금 판매 중인 상품이 없습니다.')
  })

  it('조회에 실패하면 오류와 전체 상품 동선을 보여준다', async () => {
    getSellerProfile.mockRejectedValue(new Error('판매자 정보를 불러오지 못했습니다.'))

    const wrapper = mount(SellerProfilePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).toContain('판매자 정보를 불러오지 못했습니다.')
    expect(wrapper.text()).toContain('전체 상품으로')
  })
it('판매자 프로필 사진이 있으면 첫 글자 대신 사진을 보여준다', async () => {
    getSellerProfile.mockResolvedValue({
      sellerId: 55,
      nickname: '리미트판매자',
      sellerType: 'INDIVIDUAL',
      joinedAt: '2026-07-20T10:00:00+09:00',
      onSaleCount: 2,
      profileImageUrl: 'https://cdn.example.com/seller.jpg',
    })

    const wrapper = mount(SellerProfilePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.get('img[alt="리미트판매자 프로필 사진"]').attributes('src'))
      .toBe('https://cdn.example.com/seller.jpg')
  })

  // 사진을 올리지 않은 판매자가 많아, 첫 글자로 대신하는 길이 계속 살아 있어야 합니다.
  it('사진이 없으면 닉네임 첫 글자를 보여준다', async () => {
    const wrapper = mount(SellerProfilePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.find('img[alt="리미트판매자 프로필 사진"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('리')
  })
})

