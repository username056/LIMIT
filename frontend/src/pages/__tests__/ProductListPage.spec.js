import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductListPage from '../ProductListPage.vue'
import { getDeviceCategories, getProducts } from '../../api/products'

vi.mock('../../api/products', () => ({
  getDeviceCategories: vi.fn(),
  getProducts: vi.fn(),
}))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  props: ['disabled'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

describe('ProductListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDeviceCategories.mockResolvedValue([])
  })

  it('서버 페이지 메타데이터로 다음 상품 페이지를 조회한다', async () => {
    getProducts
      .mockResolvedValueOnce({
        data: [{ productId: 1001, name: '첫 상품', price: 100000 }],
        meta: { page: 0, totalPages: 2, hasNext: true },
      })
      .mockResolvedValueOnce({
        data: [{ productId: 1002, name: '다음 상품', price: 200000 }],
        meta: { page: 1, totalPages: 2, hasNext: false },
      })
    const wrapper = mount(ProductListPage, {
      global: {
        stubs: {
          DefaultLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('첫 상품')
    const next = wrapper.findAll('button').find((button) => button.text() === '다음')
    await next.trigger('click')
    await flushPromises()

    expect(getProducts).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    expect(wrapper.text()).toContain('다음 상품')
  })
})
