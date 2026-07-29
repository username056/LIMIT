import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductManagePage from '../ProductManagePage.vue'
import { getMyProducts } from '../../api/products'

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
})
