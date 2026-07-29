import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductManagePage from '../ProductManagePage.vue'
import {
  createProduct,
  generateChecklist,
  getChecklistTemplate,
  getDeviceCategories,
  getDeviceModels,
  getMyProducts,
  getProductChecklist,
} from '../../api/products'

vi.mock('../../api/products', () => ({
  completeEvidence: vi.fn(),
  createEvidenceUploadUrl: vi.fn(),
  createProduct: vi.fn(),
  deleteProduct: vi.fn(),
  generateChecklist: vi.fn(),
  getChecklistTemplate: vi.fn(),
  getDeviceCategories: vi.fn(),
  getDeviceModels: vi.fn(),
  getHandoverGuide: vi.fn(),
  getMyProduct: vi.fn(),
  getMyProducts: vi.fn(),
  getProductChecklist: vi.fn(),
  transitionProductStatus: vi.fn(),
  updateProduct: vi.fn(),
}))

const layoutStub = { template: '<main><slot /></main>' }
const buttonStub = {
  props: ['disabled', 'type'],
  emits: ['click'],
  template: '<button :type="type || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}
const tableStub = { template: '<table><tbody><slot /></tbody></table>' }

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

const templateItems = [
  { itemCode: 'EXT-001', name: '전면·후면·측면 외관', evidenceType: 'PHOTO', isRequired: true, guide: '밝은 곳에서 촬영하세요.' },
  { itemCode: 'PRV-004', name: '계정 제거 및 초기화', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, guide: '계정을 로그아웃하세요.' },
]

describe('ProductManagePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDeviceCategories.mockResolvedValue([{ categoryId: 10, name: '노트북' }])
    getDeviceModels.mockResolvedValue([{
      deviceModelId: 101,
      manufacturerName: 'Samsung',
      modelName: 'Galaxy Book',
      defaultOs: 'WINDOWS',
    }])
    getMyProducts.mockResolvedValue({
      data: [],
      meta: { page: 0, totalPages: 0, hasNext: false },
    })
    createProduct.mockResolvedValue({ productId: 1001 })
    generateChecklist.mockResolvedValue({
      deviceModelId: 101,
      manufacturer: 'Samsung',
      modelName: 'Galaxy Book',
      osFamily: 'WINDOWS',
      aiApplied: true,
      items: templateItems.map((item) => ({
        ...item,
        required: item.isRequired,
      })),
      aiSuggestions: [{
        featureCode: 'CAMERA',
        evidenceStatus: 'VERIFIED',
        reason: '공식 사양에서 카메라를 확인했습니다.',
        sourceUrl: 'https://www.samsung.com/example',
        sourceTitle: 'Galaxy Book 공식 사양',
      }],
      reviewCandidates: [],
    })
    getChecklistTemplate.mockResolvedValue({ items: templateItems })
    getProductChecklist.mockResolvedValue([
      { checklistItemId: 7001, itemCode: 'EXT-001', name: '전면·후면·측면 외관', evidenceType: 'PHOTO', isRequired: true, status: 'PENDING' },
      { checklistItemId: 7002, itemCode: 'PRV-004', name: '계정 제거 및 초기화', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, status: 'PENDING' },
    ])
  })

  it('기기 등록 정보를 입력하면 상품 초안을 생성하고 촬영 단계로 진행한다', async () => {
    const wrapper = mount(ProductManagePage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
          BaseBadge: true,
          BaseTable: tableStub,
        },
      },
    })
    await flushPromises()

    const selects = wrapper.findAll('select')
    await selects[0].setValue('10')
    await flushPromises()
    await wrapper.findAll('select')[1].setValue('101')
    await flushPromises()

    expect(generateChecklist).toHaveBeenCalledWith({
      deviceModelId: 101,
      confirmedFeatures: [],
    })
    expect(wrapper.text()).toContain('AI 공식자료 확인 후보')
    await wrapper.find('input[type="checkbox"][value="CAMERA"]').setValue(true)

    await wrapper.find('input[placeholder="예: 갤럭시 S24 256GB 자급제"]').setValue('갤럭시 북 테스트 상품')
    await wrapper.find('input[placeholder="판매 가격"]').setValue('850000')
    await wrapper.find('input[placeholder="예: 오닉스 블랙"]').setValue('그라파이트')
    await wrapper.find('input[placeholder="예: 256"]').setValue('512')
    await wrapper.find('input[placeholder^="역, 랜드마크로 검색"]').setValue('광주광역시 광산구')
    await wrapper.find('textarea').setValue('상태가 좋은 테스트 상품입니다.')
    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith({
      categoryId: 10,
      deviceModelId: 101,
      name: '갤럭시 북 테스트 상품',
      description: '상태가 좋은 테스트 상품입니다.',
      price: 850000,
      color: '그라파이트',
      storageGb: 512,
      tradeRegion: '광주광역시 광산구',
      confirmedFeatures: ['CAMERA'],
    })
    expect(getProductChecklist).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).toContain('검수용 기기 촬영')
  })

  it('0원 상품은 촬영 단계로 진행하지 않는다', async () => {
    const wrapper = mount(ProductManagePage, {
      global: {
        stubs: {
          MyPageLayout: layoutStub,
          BaseButton: buttonStub,
          BaseBadge: true,
          BaseTable: tableStub,
        },
      },
    })
    await flushPromises()

    await wrapper.findAll('select')[0].setValue('10')
    await flushPromises()
    await wrapper.findAll('select')[1].setValue('101')
    await flushPromises()

    await wrapper.find('input[placeholder="예: 갤럭시 S24 256GB 자급제"]').setValue('가격 오류 상품')
    await wrapper.find('input[placeholder="판매 가격"]').setValue('0')
    await wrapper.find('input[placeholder^="역, 랜드마크로 검색"]').setValue('광주광역시 광산구')
    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('가격은 1원 이상 입력해 주세요.')
    expect(wrapper.text()).not.toContain('검수용 기기 촬영')
    expect(createProduct).not.toHaveBeenCalled()
  })
})
