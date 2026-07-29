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

  it('categoryId 쿼리로 들어오면 해당 카테고리가 선택된 상태로 조회한다', async () => {
    routeQuery.categoryId = '2'
    getDeviceCategories.mockResolvedValue([
      { categoryId: 1, name: '일반형 스마트폰', children: [] },
      { categoryId: 2, name: '폴더블 스마트폰', children: [] },
    ])
    getProducts.mockResolvedValue({
      data: [{ productId: 1001, name: '폴더블 상품', price: 100000 }],
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

    expect(wrapper.find('select[aria-label="카테고리 선택"]').element.value).toBe('2')
    expect(getProducts).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: '2' }))
  })

  it('사이드바에서 거래 지역 필터를 보여주지 않는다', async () => {
    getProducts.mockResolvedValue({
      data: [],
      meta: { page: 0, totalPages: 0, hasNext: false },
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

    expect(wrapper.text()).not.toContain('거래 지역')
    expect(wrapper.find('input[placeholder="예: 서울 강남구"]').exists()).toBe(false)
    expect(getProducts).toHaveBeenLastCalledWith(
      expect.not.objectContaining({ tradeRegion: expect.anything() }),
    )
  })

  it('가격 필터는 증감 화살표 없이 숫자만 받고 쉼표로 보여준다', async () => {
    getProducts.mockResolvedValue({
      data: [],
      meta: { page: 0, totalPages: 0, hasNext: false },
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

    const minInput = wrapper.find('input[aria-label="최소 가격"]')
    const maxInput = wrapper.find('input[aria-label="최대 가격"]')
    expect(minInput.attributes('type')).toBe('text')
    expect(maxInput.attributes('type')).toBe('text')

    await minInput.setValue('300000')
    expect(minInput.element.value).toBe('300,000')

    await maxInput.setValue('1a2,3만원')
    expect(maxInput.element.value).toBe('123')
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

  it('모바일 필터를 접은 상태로 시작하고 토글로 열 수 있다', async () => {
    getProducts.mockResolvedValue({ data: [], meta: { page: 0, totalPages: 0, hasNext: false } })
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

    const filterToggle = wrapper.get('button[aria-expanded]')
    expect(filterToggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('aside').classes()).toContain('hidden')

    await filterToggle.trigger('click')

    expect(filterToggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('aside').classes()).not.toContain('hidden')
  })
})
