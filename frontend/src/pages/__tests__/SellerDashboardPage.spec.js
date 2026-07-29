import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SellerDashboardPage from '../SellerDashboardPage.vue'
import { getMySellerProfile } from '../../api/seller'
import { getMyProducts } from '../../api/products'

vi.mock('../../api/seller', () => ({ getMySellerProfile: vi.fn() }))
vi.mock('../../api/products', () => ({ getMyProducts: vi.fn() }))

const globalOptions = {
  stubs: {
    MyPageLayout: { template: '<main><slot /></main>' },
    BaseBadge: { template: '<span><slot /></span>' },
    StatCard: {
      props: ['label', 'value'],
      template: '<div class="stat"><span class="stat-label">{{ label }}</span><span class="stat-value">{{ value }}</span></div>',
    },
    BarChart: { props: ['labels', 'values'], template: '<div class="bar" :data-values="JSON.stringify(values)" />' },
    DonutChart: { props: ['percent'], template: '<div class="donut" :data-percent="percent" />' },
  },
}

function statValue(wrapper, label) {
  const card = wrapper.findAll('.stat').find((node) => node.find('.stat-label').text() === label)
  return card?.find('.stat-value').text()
}

describe('SellerDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMySellerProfile.mockResolvedValue({
      sellerType: 'INDIVIDUAL',
      status: 'ACTIVE',
      countryCode: 'KR',
      createdAt: '2026-07-01T00:00:00Z',
      settlementBankName: '국민은행',
      settlementAccountHolder: '홍길동',
      settlementAccountLast4: '1234',
    })
    getMyProducts.mockResolvedValue({
      data: [
        { productId: 1, status: 'ON_SALE', completedItemCount: 4, requiredItemCount: 4 },
        { productId: 2, status: 'ON_SALE', completedItemCount: 2, requiredItemCount: 4 },
        { productId: 3, status: 'DRAFT', completedItemCount: 0, requiredItemCount: 4 },
        { productId: 4, status: 'SOLD', completedItemCount: 4, requiredItemCount: 4 },
      ],
      meta: { page: 0, totalPages: 1, hasNext: false, totalElements: 4 },
    })
  })

  it('내 상품에서 상태별 집계와 검증 완료율을 계산해 보여준다', async () => {
    const wrapper = mount(SellerDashboardPage, { global: globalOptions })
    await flushPromises()

    expect(statValue(wrapper, '등록한 상품')).toBe('4개')
    expect(statValue(wrapper, '판매 중')).toBe('2개')
    expect(statValue(wrapper, '임시 저장 중')).toBe('1개')
    expect(statValue(wrapper, '판매 완료')).toBe('1개')

    // 필수 16개 중 10개 완료 → 63%
    expect(wrapper.find('.donut').attributes('data-percent')).toBe('63')
    expect(wrapper.text()).toContain('검증을 모두 마친 상품 2개')
    expect(JSON.parse(wrapper.find('.bar').attributes('data-values'))).toEqual([2, 1, 1, 0])
  })

  it('중복되던 상품 관리 버튼은 더 이상 없다', async () => {
    const wrapper = mount(SellerDashboardPage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).not.toContain('상품 관리 메뉴를 확인할 수 있습니다')
    expect(wrapper.findAll('button').length).toBe(0)
  })

  it('상품 통계 조회가 실패해도 판매자 등록 정보는 보여준다', async () => {
    getMyProducts.mockRejectedValue(new Error('집계에 실패했습니다.'))

    const wrapper = mount(SellerDashboardPage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).toContain('집계에 실패했습니다.')
    expect(wrapper.text()).toContain('국민은행')
  })
})
