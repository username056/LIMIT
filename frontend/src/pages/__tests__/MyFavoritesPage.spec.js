import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyFavoritesPage from '../MyFavoritesPage.vue'
import { getMyFavorites, removeFavorite } from '../../api/favorites'

vi.mock('../../api/favorites', () => ({
  getMyFavorites: vi.fn(),
  removeFavorite: vi.fn(),
}))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
}

describe('MyFavoritesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API에서 관심 상품을 조회하고 해제하면 목록에서 제거한다', async () => {
    getMyFavorites
      .mockResolvedValueOnce({
        data: [
          {
            favoriteId: 501,
            productId: 1001,
            manufacturerName: 'Samsung',
            name: 'Galaxy S24',
            price: 650000,
            status: 'ON_SALE',
          },
        ],
        meta: { page: 0, totalPages: 1, hasNext: false },
      })
      .mockResolvedValueOnce({
        data: [],
        meta: { page: 0, totalPages: 0, hasNext: false },
      })
    removeFavorite.mockResolvedValue(undefined)
    const wrapper = mount(MyFavoritesPage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(getMyFavorites).toHaveBeenCalledOnce()
    expect(wrapper.findAll('h2')).toHaveLength(1)
    // '해제' 글자 버튼이 아니라 이미 눌러진 하트를 한 번 더 눌러 끕니다.
    const heartButton = wrapper.get('button[aria-label="Galaxy S24 좋아요 해제"]')
    expect(heartButton.text()).toBe('♥')
    await heartButton.trigger('click')
    await flushPromises()

    expect(removeFavorite).toHaveBeenCalledWith(1001)
    expect(wrapper.findAll('h2')).toHaveLength(0)
    expect(wrapper.text()).toContain('좋아요한 상품이 없습니다.')
  })

  it('관심 상품에도 대표 이미지를 보여준다', async () => {
    getMyFavorites.mockResolvedValue({
      data: [{
        favoriteId: 501,
        productId: 1001,
        manufacturerName: 'Samsung',
        name: 'Galaxy S24',
        price: 650000,
        status: 'ON_SALE',
        thumbnailUrl: 'https://cdn.example.com/1001.jpg',
      }],
      meta: { page: 0, totalPages: 1, hasNext: false },
    })

    const wrapper = mount(MyFavoritesPage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.get('img').attributes('src')).toBe('https://cdn.example.com/1001.jpg')
  })

  it('조회 실패 시 오류와 재시도 동작을 제공한다', async () => {
    getMyFavorites
      .mockRejectedValueOnce(new Error('목록 조회 실패'))
      .mockResolvedValueOnce({
        data: [],
        meta: { page: 0, totalPages: 0, hasNext: false },
      })

    const wrapper = mount(MyFavoritesPage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('목록 조회 실패')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(getMyFavorites).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('좋아요한 상품이 없습니다.')
  })
})
