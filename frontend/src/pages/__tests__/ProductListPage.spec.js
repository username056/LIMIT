import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductListPage from '../ProductListPage.vue'
import { getDeviceCategories, getProducts } from '../../api/products'

const { replace, routeQuery } = vi.hoisted(() => ({
  replace: vi.fn(),
  routeQuery: {},
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ replace }),
}))

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
    Object.keys(routeQuery).forEach((key) => delete routeQuery[key])
    replace.mockResolvedValue(undefined)
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

    expect(getProducts).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 18 }))
    expect(wrapper.text()).toContain('다음 상품')
  })

  it('조회 실패를 검색 결과 없음 상태와 동시에 표시하지 않는다', async () => {
    getProducts.mockRejectedValue(new Error('상품 조회 실패'))
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

    expect(wrapper.text()).toContain('상품 조회 실패')
    expect(wrapper.text()).not.toContain('조건에 맞는 상품이 없습니다.')
  })

  it('느린 이전 요청이 최신 정렬 결과를 덮어쓰지 않는다', async () => {
    let resolveOlder
    let resolveLatest
    getProducts
      .mockResolvedValueOnce({
        data: [{ productId: 1001, name: '초기 상품', price: 100000 }],
        meta: { page: 0, totalPages: 1, hasNext: false },
      })
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOlder = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveLatest = resolve }))

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

    const sort = wrapper.get('select[aria-label="상품 정렬"]')
    await sort.setValue('price,asc')
    await sort.setValue('price,desc')
    resolveLatest({
      data: [{ productId: 1003, name: '최신 요청 상품', price: 300000 }],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })
    await flushPromises()
    resolveOlder({
      data: [{ productId: 1002, name: '과거 요청 상품', price: 200000 }],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('최신 요청 상품')
    expect(wrapper.text()).not.toContain('과거 요청 상품')
  })

  it('필터 초기화 시 URL 검색어도 제거한다', async () => {
    routeQuery.q = '노트북'
    getProducts.mockResolvedValue({
      data: [{ productId: 1001, name: '검색 상품', price: 100000 }],
      meta: { page: 0, totalPages: 1, hasNext: false },
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

    const reset = wrapper.findAll('button').find((button) => button.text() === '초기화')
    await reset.trigger('click')
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({ name: 'products', query: {} })
  })
})
