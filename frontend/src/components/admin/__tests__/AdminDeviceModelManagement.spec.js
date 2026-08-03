import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminDeviceModelManagement from '../AdminDeviceModelManagement.vue'
import {
  getAdminDeviceModel,
  getAdminDeviceModelProductMaterials,
  getAdminDeviceModelProducts,
  getAdminDeviceModelResearches,
  getAdminDeviceModels,
  updateAdminDeviceModelStatus,
} from '../../../api/admin'
import { getDeviceCategories } from '../../../api/products'

vi.mock('../../../api/admin', () => ({
  getAdminDeviceModel: vi.fn(),
  getAdminDeviceModelProductMaterials: vi.fn(),
  getAdminDeviceModelProducts: vi.fn(),
  getAdminDeviceModelResearches: vi.fn(),
  getAdminDeviceModels: vi.fn(),
  researchAdminDeviceModel: vi.fn(),
  updateAdminDeviceModel: vi.fn(),
  updateAdminDeviceModelStatus: vi.fn(),
}))

vi.mock('../../../api/products', () => ({
  getDeviceCategories: vi.fn(),
}))

const emptyPage = (size = 20) => ({
  content: [],
  page: 0,
  size,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
})

const summary = {
  modelId: 202,
  categoryId: 10,
  categoryName: '스마트폰',
  manufacturer: 'Samsung',
  modelName: 'Galaxy S25',
  modelCode: 'SM-S931N',
  osFamily: 'ANDROID',
  reviewStatus: 'VERIFIED',
  sourceType: 'CATALOG',
  isActive: true,
  latestResearchStatus: 'APPROVED',
  latestResearchVersion: 2,
  relatedProductCount: 1,
  updatedAt: '2026-08-03T12:00:00',
}

const detail = {
  ...summary,
  baseChecklistItems: [],
  latestResearch: null,
  impact: {
    productCount: 1,
    productStatusCounts: { ON_SALE: 1 },
    researchCount: 2,
    variantCount: 3,
  },
}

async function mountAndSelectModel() {
  const wrapper = mount(AdminDeviceModelManagement)
  await flushPromises()
  await wrapper.findAll('button')
    .find((button) => button.text().includes('Galaxy S25'))
    .trigger('click')
  await flushPromises()
  return wrapper
}

describe('AdminDeviceModelManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDeviceCategories.mockResolvedValue([{ categoryId: 10, name: '스마트폰' }])
    getAdminDeviceModels.mockResolvedValue({
      ...emptyPage(),
      content: [summary],
      totalElements: 1,
      totalPages: 1,
    })
    getAdminDeviceModel.mockResolvedValue(detail)
    getAdminDeviceModelProducts.mockResolvedValue(emptyPage(10))
    getAdminDeviceModelResearches.mockResolvedValue(emptyPage(10))
  })

  it('연관 상품을 먼저 조회하고 선택한 상품의 자료만 지연 조회한다', async () => {
    getAdminDeviceModelProducts.mockResolvedValue({
      ...emptyPage(10),
      content: [{
        productId: 901,
        sellerId: 7,
        title: '갤럭시 S25 판매',
        price: 850000,
        status: 'ON_SALE',
        createdAt: '2026-08-01T10:00:00',
        updatedAt: '2026-08-03T11:00:00',
      }],
      totalElements: 1,
      totalPages: 1,
    })
    getAdminDeviceModelProductMaterials.mockResolvedValue({
      productId: 901,
      images: [{ imageId: 1, imageUrl: 'https://example.com/product.jpg' }],
      checklistItems: [],
    })
    const wrapper = await mountAndSelectModel()

    await wrapper.findAll('button')
      .find((button) => button.text() === '연관 상품')
      .trigger('click')
    await flushPromises()

    expect(getAdminDeviceModelProducts).toHaveBeenCalledWith(202, {
      page: 0,
      size: 10,
      sort: 'updatedAt,desc',
    })
    expect(getAdminDeviceModelProductMaterials).not.toHaveBeenCalled()

    await wrapper.findAll('button')
      .find((button) => button.text() === '사진·영상·검수 증빙 보기')
      .trigger('click')
    await flushPromises()

    expect(getAdminDeviceModelProductMaterials).toHaveBeenCalledWith(202, 901)
    expect(wrapper.get('img[alt="상품 이미지 1"]').attributes('src'))
      .toBe('https://example.com/product.jpg')
  })

  it('삭제 요청을 기존 상품을 유지하는 모델 비활성화로 처리한다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    updateAdminDeviceModelStatus.mockResolvedValue({
      ...detail,
      isActive: false,
      reviewStatus: 'DISABLED',
      disableReason: '중복 등록 모델',
    })
    const wrapper = await mountAndSelectModel()

    await wrapper.findAll('button')
      .find((button) => button.text() === '모델 삭제(비활성화)')
      .trigger('click')
    const reasonLabel = wrapper.findAll('label')
      .find((label) => label.text().includes('처리 사유'))
    await reasonLabel.get('input').setValue('중복 등록 모델')
    await wrapper.findAll('button')
      .find((button) => button.text() === '삭제 확인')
      .trigger('click')
    await flushPromises()

    expect(updateAdminDeviceModelStatus).toHaveBeenCalledWith(202, {
      isActive: false,
      reason: '중복 등록 모델',
      replacementModelId: null,
    })
    expect(wrapper.text()).toContain('기존 상품은 그대로 유지됩니다.')
  })
})
