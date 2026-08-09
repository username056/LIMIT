import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminMarketplaceModeration from '../AdminMarketplaceModeration.vue'
import {
  decideAdminReport,
  decideAdminRestoration,
  getAdminReports,
  getAdminRestorationRequests,
  getModerationDashboard,
} from '../../../api/admin'

vi.mock('../../../api/admin', () => ({
  decideAdminReport: vi.fn(),
  decideAdminRestoration: vi.fn(),
  getAdminModeratedProduct: vi.fn(),
  getAdminModeratedProducts: vi.fn(),
  getAdminReports: vi.fn(),
  getAdminRestorationRequests: vi.fn(),
  getAdminRiskSignals: vi.fn(),
  getModerationDashboard: vi.fn(),
  resolveAdminRiskSignal: vi.fn(),
}))

const page = (content) => ({
  content,
  page: 0,
  size: 50,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  hasNext: false,
})

const buttonStub = {
  props: ['disabled', 'variant', 'type'],
  emits: ['click'],
  template: '<button :type="type || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

const globalOptions = {
  stubs: {
    BaseButton: buttonStub,
    BaseBadge: { template: '<span><slot /></span>' },
    BaseCard: { template: '<section><slot /></section>' },
  },
}

describe('AdminMarketplaceModeration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getModerationDashboard.mockResolvedValue({
      pendingReportCount: 3,
      warningRequiredProductCount: 1,
      suspendedProductCount: 2,
      pendingRestorationCount: 1,
      openRiskSignalCount: 4,
      suspiciousSellers: [],
      recentRiskSignals: [],
    })
    getAdminReports.mockResolvedValue(page([]))
    getAdminRestorationRequests.mockResolvedValue(page([]))
  })

  it('신고·판매 중지·복구·위험 신호를 한 화면에서 요약한다', async () => {
    const wrapper = mount(AdminMarketplaceModeration, { global: globalOptions })
    await flushPromises()

    expect(getModerationDashboard).toHaveBeenCalled()
    expect(wrapper.text()).toContain('처리 대기 신고')
    expect(wrapper.text()).toContain('판매 중지')
    expect(wrapper.text()).toContain('복구 승인 대기')
    expect(wrapper.text()).toContain('미검토 위험 신호')
  })

  it('관리자는 신고를 검토하고 상품 판매를 중지한다', async () => {
    getAdminReports.mockResolvedValue(page([{
      reportId: 501,
      productId: 1001,
      productName: '중복 노트북',
      sellerId: 55,
      reporterId: 77,
      category: 'DUPLICATE_LISTING',
      detail: '같은 사진의 상품이 반복 등록되었습니다.',
      status: 'PENDING',
      moderationStatus: 'NORMAL',
      createdAt: '2026-08-06T09:00:00',
    }]))
    decideAdminReport.mockResolvedValue({ reportId: 501, moderationStatus: 'SUSPENDED' })
    vi.spyOn(window, 'prompt').mockReturnValue('중복 등록 확인')
    const wrapper = mount(AdminMarketplaceModeration, { global: globalOptions })
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '신고 심사').trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '판매 중지').trigger('click')
    await flushPromises()

    expect(decideAdminReport).toHaveBeenCalledWith(501, {
      decision: 'SUSPEND',
      note: '중복 등록 확인',
    })
  })

  it('수정된 판매 중지 상품은 관리자 승인 후에만 복구한다', async () => {
    getAdminRestorationRequests.mockResolvedValue(page([{
      restorationRequestId: 601,
      productId: 1001,
      productName: '수정된 노트북',
      sellerId: 55,
      requestNote: '사진과 설명을 수정했습니다.',
      status: 'PENDING',
      moderationStatus: 'RESTORE_REQUESTED',
      createdAt: '2026-08-06T10:00:00',
      productUpdatedAt: '2026-08-06T09:30:00',
    }]))
    decideAdminRestoration.mockResolvedValue({
      restorationRequestId: 601,
      status: 'APPROVED',
      moderationStatus: 'NORMAL',
    })
    vi.spyOn(window, 'prompt').mockReturnValue('수정 확인')
    const wrapper = mount(AdminMarketplaceModeration, { global: globalOptions })
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '복구 승인').trigger('click')
    await flushPromises()
    const approvalButtons = wrapper.findAll('button')
      .filter((button) => button.text() === '복구 승인')
    await approvalButtons.at(-1).trigger('click')
    await flushPromises()

    expect(decideAdminRestoration).toHaveBeenCalledWith(601, {
      decision: 'APPROVE',
      note: '수정 확인',
    })
  })
})
