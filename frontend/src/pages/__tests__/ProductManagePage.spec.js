import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductManagePage from '../ProductManagePage.vue'
import { getMyProducts, transitionProductStatus } from '../../api/products'

vi.mock('../../api/products', () => ({
  deleteProduct: vi.fn(),
  getMyProducts: vi.fn(),
  transitionProductStatus: vi.fn(),
}))

const buttonStub = {
  props: ['disabled', 'variant'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}
const routerLinkStub = {
  name: 'RouterLink',
  props: ['to'],
  template: '<a :data-to="JSON.stringify(to)"><slot /></a>',
}
const globalOptions = {
  stubs: {
    MyPageLayout: { template: '<main><slot /></main>' },
    BaseButton: buttonStub,
    BaseBadge: { template: '<span><slot /></span>' },
    BaseTable: { template: '<table><tbody><slot /></tbody></table>' },
    RouterLink: routerLinkStub,
  },
}

function linkByText(wrapper, text) {
  return wrapper.findAll('[data-to]').find((node) => node.text() === text)
}

describe('ProductManagePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMyProducts.mockResolvedValue({
      data: [{
        productId: 2002,
        name: '임시 저장된 상품',
        status: 'DRAFT',
        completedItemCount: 0,
        requiredItemCount: 2,
      }],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })
  })

  it('내 상품만 조회해서 상태를 한국어 라벨로 보여준다', async () => {
    const wrapper = mount(ProductManagePage, { global: globalOptions })
    await flushPromises()

    expect(getMyProducts).toHaveBeenCalledWith({
      status: '',
      page: 0,
      size: 20,
      sort: 'updatedAt,desc',
    })
    expect(wrapper.text()).toContain('임시 저장 중')
    expect(wrapper.text()).not.toContain('DRAFT')
  })

  it('상품 등록 위자드는 이 화면에 없다', async () => {
    const wrapper = mount(ProductManagePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).not.toContain('판매할 기기를 등록해 주세요.')
    expect(wrapper.text()).not.toContain('다음 단계')
    expect(wrapper.find('select[aria-label="저장 용량 선택"]').exists()).toBe(false)
  })

  it('상품명은 상세로, 수정은 등록 화면의 수정 모드로 연결된다', async () => {
    const wrapper = mount(ProductManagePage, { global: globalOptions })
    await flushPromises()

    expect(JSON.parse(linkByText(wrapper, '임시 저장된 상품').attributes('data-to'))).toEqual({
      name: 'product-detail',
      params: { productId: 2002 },
    })
    expect(JSON.parse(linkByText(wrapper, '수정').attributes('data-to'))).toEqual({
      name: 'seller-product-edit',
      params: { productId: 2002 },
    })
  })

  it('판매 중인 상품도 수정할 수 있고, 거래가 시작된 상품은 수정 링크를 감춘다', async () => {
    getMyProducts.mockResolvedValue({
      data: [
        {
          productId: 3003,
          name: '판매 중 상품',
          status: 'ON_SALE',
          completedItemCount: 2,
          requiredItemCount: 2,
        },
        {
          productId: 4004,
          name: '예약된 상품',
          status: 'RESERVED',
          completedItemCount: 2,
          requiredItemCount: 2,
        },
      ],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })
    const wrapper = mount(ProductManagePage, { global: globalOptions })
    await flushPromises()

    const editLinks = wrapper.findAll('[data-to]').filter((node) => node.text() === '수정')
    expect(editLinks).toHaveLength(1)
    expect(JSON.parse(editLinks[0].attributes('data-to'))).toEqual({
      name: 'seller-product-edit',
      params: { productId: 3003 },
    })
    expect(wrapper.text()).toContain('수정 불가')
  })

  // 결제를 거치지 않는 직거래를 판매자가 직접 닫는 경로입니다.
  it('판매 중 상품은 판매 완료로 직접 처리할 수 있다', async () => {
    getMyProducts.mockResolvedValue({
      data: [{
        productId: 3003,
        name: '판매 중 상품',
        status: 'ON_SALE',
        completedItemCount: 2,
        requiredItemCount: 2,
      }],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })
    transitionProductStatus.mockResolvedValue({})
    window.confirm = vi.fn(() => true)

    const wrapper = mount(ProductManagePage, { global: globalOptions })
    await flushPromises()

    const soldButton = wrapper.findAll('button').find((node) => node.text() === '판매 완료 처리')
    await soldButton.trigger('click')
    await flushPromises()

    expect(transitionProductStatus).toHaveBeenCalledWith(3003, 'SOLD', '판매자 직거래 판매 완료')
    expect(wrapper.text()).toContain('판매 완료로 처리했습니다.')
  })

  // 판매자도 자기 대표 이미지가 구매자 화면과 같은 모양으로 보이는지 확인할 수 있어야 합니다.
  it('상품 관리에서도 대표 이미지를 보여준다', async () => {
    getMyProducts.mockResolvedValue({
      data: [{
        productId: 2002,
        name: '임시 저장된 상품',
        status: 'DRAFT',
        thumbnailUrl: 'https://cdn.example.com/2002.jpg',
        completedItemCount: 0,
        requiredItemCount: 2,
      }],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })

    const wrapper = mount(ProductManagePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.get('img').attributes('src')).toBe('https://cdn.example.com/2002.jpg')
  })

  it('임시 저장 중이거나 이미 판매 완료된 상품에는 판매 완료 처리를 노출하지 않는다', async () => {
    getMyProducts.mockResolvedValue({
      data: [
        { productId: 1, name: '초안', status: 'DRAFT', completedItemCount: 0, requiredItemCount: 2 },
        { productId: 2, name: '완료', status: 'SOLD', completedItemCount: 2, requiredItemCount: 2 },
      ],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })

    const wrapper = mount(ProductManagePage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.findAll('button').filter((node) => node.text() === '판매 완료 처리')).toHaveLength(0)
  })
})
