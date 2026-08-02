import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductListPage from '../ProductListPage.vue'
import { getDeviceCategories, getProducts } from '../../api/products'
import { addFavorite, getMyFavorites, removeFavorite } from '../../api/favorites'
import { getAccessToken } from '../../auth/session'

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

vi.mock('../../api/favorites', () => ({
  addFavorite: vi.fn(),
  getMyFavorites: vi.fn(),
  removeFavorite: vi.fn(),
}))

vi.mock('../../auth/session', () => ({
  getAccessToken: vi.fn(),
  getSessionMember: vi.fn(),
  hasRole: vi.fn(() => false),
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
    getAccessToken.mockReturnValue(null)
    getMyFavorites.mockResolvedValue({ data: [], meta: {} })
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

  it('조회수 높은 순을 선택하면 서버 정렬을 요청하고 중복 포함 안내를 보여준다', async () => {
    getProducts.mockResolvedValue({
      data: [{ productId: 1001, name: '많이 본 상품', price: 100000, viewCount: 1234 }],
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

    await wrapper.get('select[aria-label="상품 정렬"]').setValue('viewCount,desc')
    await flushPromises()

    expect(getProducts).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'viewCount,desc', page: 0 }),
    )
    expect(wrapper.text()).toContain('반복 조회가 포함된 누적 조회수 기준입니다.')
    expect(wrapper.text()).toContain('조회 1,234')
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

  // 상세로 들어가지 않고 목록에서 바로 담을 수 있어야 합니다.
  describe('목록 좋아요', () => {
    function mountList() {
      return mount(ProductListPage, {
        global: {
          stubs: {
            DefaultLayout: layoutStub,
            BaseButton: buttonStub,
            RouterLink: { template: '<a><slot /></a>' },
          },
        },
      })
    }

    beforeEach(() => {
      getProducts.mockResolvedValue({
        data: [{ productId: 1001, name: '상품', price: 100000 }],
        meta: { page: 0, totalPages: 1, hasNext: false },
      })
    })

    it('로그인하지 않으면 하트를 보여주지 않는다', async () => {
      const wrapper = mountList()
      await flushPromises()

      expect(wrapper.find('button[aria-label="상품 좋아요"]').exists()).toBe(false)
      expect(getMyFavorites).not.toHaveBeenCalled()
    })

    it('이미 담은 상품은 채워진 하트로 보여준다', async () => {
      getAccessToken.mockReturnValue('token')
      getMyFavorites.mockResolvedValue({ data: [{ productId: 1001 }], meta: {} })

      const wrapper = mountList()
      await flushPromises()

      // 하트 모양은 늘 같고, 담은 상태는 색으로 구분합니다(흰 하트 → 빨간 하트).
      expect(wrapper.get('button[aria-label="상품 좋아요 해제"]').classes()).toContain('text-red-500')
    })

    it('하트를 누르면 담고 다시 누르면 해제한다', async () => {
      getAccessToken.mockReturnValue('token')
      addFavorite.mockResolvedValue({})
      removeFavorite.mockResolvedValue({})

      const wrapper = mountList()
      await flushPromises()

      await wrapper.get('button[aria-label="상품 좋아요"]').trigger('click')
      await flushPromises()
      expect(addFavorite).toHaveBeenCalledWith(1001)

      await wrapper.get('button[aria-label="상품 좋아요 해제"]').trigger('click')
      await flushPromises()
      expect(removeFavorite).toHaveBeenCalledWith(1001)
    })
  })

  // 예전에는 구간 선택을 COMPLETED/IN_PROGRESS 둘로 뭉개 보내서 고른 구간과 결과가 달랐습니다.
  describe('검증 개수 필터', () => {
    async function pickBucket(wrapper, label) {
      await wrapper.findAll('button').find((node) => node.text() === label).trigger('click')
      await flushPromises()
    }

    it('고른 구간을 실제 개수 범위로 보낸다', async () => {
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

      await pickBucket(wrapper, '5-7개')

      expect(getProducts).toHaveBeenLastCalledWith(expect.objectContaining({
        minVerifiedCount: 5,
        maxVerifiedCount: 7,
      }))
    })

    it('10개 이상은 상한 없이 보낸다', async () => {
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

      await pickBucket(wrapper, '10개 이상')

      expect(getProducts).toHaveBeenLastCalledWith(expect.objectContaining({
        minVerifiedCount: 10,
        maxVerifiedCount: undefined,
      }))
    })

    it('전체를 고르면 개수 조건을 보내지 않는다', async () => {
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

      await pickBucket(wrapper, '5-7개')
      await pickBucket(wrapper, '전체')

      expect(getProducts).toHaveBeenLastCalledWith(expect.objectContaining({
        minVerifiedCount: undefined,
        maxVerifiedCount: undefined,
      }))
    })
  })
})
