import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductRegisterPage from '../ProductRegisterPage.vue'
import {
  completeEvidence,
  createEvidenceUploadUrl,
  createProduct,
  generateChecklist,
  getChecklistTemplate,
  getDeviceCategories,
  getDeviceModels,
  getProductChecklist,
  transitionProductStatus,
} from '../../api/products'

const { routerPushMock } = vi.hoisted(() => ({ routerPushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: routerPushMock, replace: vi.fn() }),
}))

vi.mock('../../utils/mediaOptimize', () => ({
  compressImage: vi.fn((file) => Promise.resolve(file)),
  compressVideo: vi.fn((file) => Promise.resolve(file)),
}))

vi.mock('../../api/products', () => ({
  completeEvidence: vi.fn(),
  createEvidenceUploadUrl: vi.fn(),
  createProduct: vi.fn(),
  generateChecklist: vi.fn(),
  getChecklistTemplate: vi.fn(),
  getDeviceCategories: vi.fn(),
  getDeviceModels: vi.fn(),
  getHandoverGuide: vi.fn(),
  getMyProduct: vi.fn(),
  getProductChecklist: vi.fn(),
  transitionProductStatus: vi.fn(),
  updateProduct: vi.fn(),
}))

const buttonStub = {
  props: ['disabled', 'type'],
  emits: ['click'],
  template: '<button :type="type || \'button\'" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

const globalOptions = {
  stubs: {
    DefaultLayout: { template: '<div><slot /></div>' },
    BaseButton: buttonStub,
    BaseBadge: { template: '<span><slot /></span>' },
    RouterLink: { template: '<a><slot /></a>' },
  },
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

function attachFile(input, file) {
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  return input.trigger('change')
}

async function fillDeviceStep(wrapper) {
  await wrapper.findAll('select')[0].setValue('10')
  await flushPromises()
  await wrapper.findAll('select')[1].setValue('101')
  await flushPromises()

  await wrapper.find('input[placeholder="예: 갤럭시 S24 256GB 자급제"]').setValue('갤럭시 북 테스트 상품')
  await wrapper.find('input[placeholder="판매 가격"]').setValue('850000')
}

async function goToCaptureStep(wrapper) {
  await fillDeviceStep(wrapper)
  await buttonByText(wrapper, '다음 단계').trigger('click')
  await flushPromises()
}

const templateItems = [
  { itemCode: 'EXT-001', name: '전면·후면·측면 외관', evidenceType: 'PHOTO', isRequired: true, guide: '밝은 곳에서 촬영하세요.' },
  { itemCode: 'PRV-004', name: '계정 제거 및 초기화', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, guide: '계정을 로그아웃하세요.' },
]

describe('ProductRegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // jsdom에는 scrollTo 구현이 없어 단계 이동 시 예외가 나므로 스텁으로 대체합니다.
    window.scrollTo = vi.fn()
    global.fetch = vi.fn().mockResolvedValue({ ok: true })
    global.URL.createObjectURL = vi.fn(() => 'blob:preview')
    global.URL.revokeObjectURL = vi.fn()
    createEvidenceUploadUrl.mockResolvedValue({
      uploadId: 'upload-1',
      presignedUrl: 'https://storage.test/upload',
      requiredHeaders: {},
    })
    completeEvidence.mockResolvedValue({})
    getDeviceCategories.mockResolvedValue([{ categoryId: 10, name: '노트북' }])
    getDeviceModels.mockResolvedValue([{
      deviceModelId: 101,
      manufacturerName: 'Samsung',
      modelName: 'Galaxy Book',
    }])
    createProduct.mockResolvedValue({ productId: 1001 })
    getChecklistTemplate.mockResolvedValue({ items: templateItems })
    generateChecklist.mockResolvedValue({
      deviceModelId: 101,
      manufacturer: 'Samsung',
      modelName: 'Galaxy Book',
      osFamily: 'WINDOWS',
      aiApplied: true,
      items: templateItems.map((item) => ({ ...item, required: item.isRequired })),
      aiSuggestions: [],
      reviewCandidates: [],
    })
    getProductChecklist.mockResolvedValue([
      { checklistItemId: 7001, itemCode: 'EXT-001', name: '전면·후면·측면 외관', evidenceType: 'PHOTO', isRequired: true, status: 'PENDING' },
      { checklistItemId: 7002, itemCode: 'PRV-004', name: '계정 제거 및 초기화', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, status: 'PENDING' },
    ])
  })

  it('기기 등록 정보를 입력하면 상품을 생성하고 촬영 단계로 진행한다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    await fillDeviceStep(wrapper)
    expect(getChecklistTemplate).toHaveBeenCalledWith(101)

    await wrapper.find('input[placeholder="예: 오닉스 블랙"]').setValue('그라파이트')
    await wrapper.find('select[aria-label="저장 용량 선택"]').setValue('512')
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
      // 화면에서는 거래 지역을 받지 않지만 백엔드 @NotBlank 때문에 기본값을 채워 보냅니다.
      tradeRegion: '협의',
    })
    expect(getProductChecklist).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).toContain('검수용 기기 촬영')
  })

  it('Windows 모델은 자동 생성 체크리스트와 AI 확인 후보를 보여주고 선택한 기능을 함께 보낸다', async () => {
    getDeviceModels.mockResolvedValue([{
      deviceModelId: 101,
      manufacturerName: 'Samsung',
      modelName: 'Galaxy Book',
      defaultOs: 'WINDOWS',
    }])
    generateChecklist.mockResolvedValue({
      deviceModelId: 101,
      manufacturer: 'Samsung',
      modelName: 'Galaxy Book',
      osFamily: 'WINDOWS',
      aiApplied: true,
      items: templateItems.map((item) => ({ ...item, required: item.isRequired })),
      aiSuggestions: [{
        featureCode: 'CAMERA',
        featureName: '내장 카메라',
        evidenceStatus: 'VERIFIED',
        reason: '공식 사양에서 카메라를 확인했습니다.',
        checkGuide: '카메라 앱을 실행해 영상 출력 상태를 확인하세요.',
        sourceUrl: 'https://www.samsung.com/example',
        sourceTitle: 'Galaxy Book 공식 사양',
      }],
      reviewCandidates: ['FINGERPRINT'],
    })

    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await fillDeviceStep(wrapper)

    expect(generateChecklist).toHaveBeenCalledWith({
      deviceModelId: 101,
      confirmedFeatures: [],
    })
    expect(getChecklistTemplate).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('자동 생성 체크리스트')
    expect(wrapper.text()).toContain('AI 공식자료 반영')
    expect(wrapper.text()).toContain('AI 공식자료 확인 후보')
    expect(wrapper.text()).toContain('내장 카메라')
    expect(wrapper.text()).toContain('선정 이유')
    expect(wrapper.text()).toContain('점검 방법')
    expect(wrapper.text()).toContain('추가 검토가 필요한 기능')
    expect(wrapper.text()).not.toContain('/ 5개 선택')
    expect(wrapper.text()).not.toContain('0개 선택')

    await wrapper.find('input[type="checkbox"][value="CAMERA"]').setValue(true)
    expect(wrapper.text()).toContain('1개 선택')
    expect(wrapper.text()).not.toContain('/ 5개 선택')
    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({
      confirmedFeatures: ['CAMERA'],
    }))
  })

  it('0원 상품은 촬영 단계로 진행하지 않는다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    await fillDeviceStep(wrapper)
    await wrapper.find('input[placeholder="판매 가격"]').setValue('0')
    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('가격은 1원 이상 입력해 주세요.')
    expect(wrapper.text()).not.toContain('검수용 기기 촬영')
    expect(createProduct).not.toHaveBeenCalled()
  })

  it('가격은 숫자만 받아 천 단위 쉼표로 보여주고 숫자로 전송한다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    const priceInput = wrapper.find('input[placeholder="판매 가격"]')
    // 증감 화살표가 붙는 number 타입이 아니어야 합니다.
    expect(priceInput.attributes('type')).toBe('text')
    expect(priceInput.attributes('inputmode')).toBe('numeric')

    await priceInput.setValue('1234567')
    expect(priceInput.element.value).toBe('1,234,567')
    // 정상 입력 중에는 안내 문구가 보이지 않습니다.
    expect(wrapper.text()).not.toContain('숫자만 입력해 주세요.')

    // 문자가 섞여 들어오면 숫자만 남기고, 그때만 이유를 알려 줍니다.
    await priceInput.setValue('12a3,4원')
    expect(priceInput.element.value).toBe('1,234')
    expect(wrapper.text()).toContain('숫자만 입력해 주세요.')

    // 다시 숫자만 입력하면 문구가 사라집니다.
    await priceInput.setValue('5000')
    expect(wrapper.text()).not.toContain('숫자만 입력해 주세요.')

    await fillDeviceStep(wrapper)
    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({ price: 850000 }))
  })

  it('가격이 12자리를 넘으면 최대값으로 잡아 준다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    const priceInput = wrapper.find('input[placeholder="판매 가격"]')

    await priceInput.setValue('999999999999')
    expect(priceInput.element.value).toBe('999,999,999,999')
    expect(wrapper.text()).not.toContain('최대 12자리까지')

    // 상한을 넘겨 더 눌러도 입력창에 남지 않아야 합니다.
    await priceInput.setValue('999,999,999,999545546')
    expect(priceInput.element.value).toBe('999,999,999,999')
    expect(wrapper.text()).toContain('최대 12자리까지 입력할 수 있습니다.')

    // 다른 값에서도 12자리까지만 유지합니다.
    await priceInput.setValue('1234567890123')
    expect(priceInput.element.value).toBe('123,456,789,012')
  })

  it('등록 폼에서 거래 지역을 받지 않는다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).not.toContain('거래 지역')
    expect(wrapper.find('input[placeholder="역, 랜드마크로 검색 (예: 상동역)"]').exists()).toBe(false)
  })

  it('대표 이미지는 미리보기로 확인하고 삭제할 수 있다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).toContain('대표 이미지')
    expect(wrapper.text()).toContain('미등록')
    expect(wrapper.find('img[alt="대표 이미지 미리보기"]').exists()).toBe(false)

    const input = wrapper.find('input[aria-label="대표 이미지 선택"]')
    await attachFile(input, new File(['x'], 'thumb.jpg', { type: 'image/jpeg' }))
    await flushPromises()

    expect(wrapper.find('img[alt="대표 이미지 미리보기"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('thumb.jpg')

    await buttonByText(wrapper, '삭제').trigger('click')
    expect(wrapper.find('img[alt="대표 이미지 미리보기"]').exists()).toBe(false)
  })

  it('대표 이미지는 이미지 파일만 받는다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    const input = wrapper.find('input[aria-label="대표 이미지 선택"]')
    await attachFile(input, new File(['x'], 'spec.pdf', { type: 'application/pdf' }))
    await flushPromises()

    expect(wrapper.text()).toContain('이미지 파일만 대표 이미지로 등록할 수 있습니다.')
    expect(wrapper.find('img[alt="대표 이미지 미리보기"]').exists()).toBe(false)
  })

  it('저장 용량은 드롭다운으로 고르거나 직접 입력할 수 있다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    const storageSelect = wrapper.find('select[aria-label="저장 용량 선택"]')
    expect(wrapper.text()).toContain('1TB')
    expect(wrapper.find('input[aria-label="저장 용량 직접 입력"]').exists()).toBe(false)

    await storageSelect.setValue('custom')
    const customInput = wrapper.find('input[aria-label="저장 용량 직접 입력"]')
    expect(customInput.exists()).toBe(true)

    await customInput.setValue('384')
    await fillDeviceStep(wrapper)
    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({ storageGb: 384 }))
  })

  it('필수 촬영 항목이 남으면 팝업으로 알리되 계속 작성하기를 누르면 그 단계에 머문다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('아직 촬영하지 않은 필수 항목이 1개 있습니다.')

    await buttonByText(wrapper, '계속 작성하기').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('검수용 기기 촬영')
    expect(wrapper.text()).not.toContain('개인정보를 정리했는지 확인해 주세요.')
  })

  it('필수 촬영 항목이 남아도 확인을 누르면 다음 단계로 넘어간다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()
    await buttonByText(wrapper, '확인').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('개인정보를 정리했는지 확인해 주세요.')
  })

  it('개인정보 확인이 남아도 확인을 누르면 등록완료 단계로 넘어간다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    // 촬영을 건너뛰고 3단계로 이동
    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()
    await buttonByText(wrapper, '확인').trigger('click')
    await flushPromises()

    // 개인정보 확인도 건너뛰고 4단계로 이동
    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('개인정보 정리 확인이 남아 있습니다.')
    await buttonByText(wrapper, '확인').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('체크리스트 등록이 완료되었습니다.')
  })

  it('체크리스트 항목당 사진은 3개까지만 첨부할 수 있다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    for (let index = 0; index < 3; index += 1) {
      const fileInput = wrapper.find('input[type="file"]')
      expect(fileInput.element.disabled).toBe(false)
      // eslint-disable-next-line no-await-in-loop
      await attachFile(fileInput, new File(['x'], `photo${index}.jpg`, { type: 'image/jpeg' }))
      // eslint-disable-next-line no-await-in-loop
      await flushPromises()
    }

    expect(wrapper.findAll('button[aria-label*="첨부 파일 확인"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('최대 3개까지 첨부했습니다')
    expect(wrapper.find('input[type="file"]').element.disabled).toBe(true)
    expect(wrapper.text()).toContain('첨부 3 / 3')
  })

  it('첨부 썸네일을 누르면 확인 모달이 열리고 삭제할 수 있다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await attachFile(wrapper.find('input[type="file"]'), new File(['x'], 'photo.jpg', { type: 'image/jpeg' }))
    await flushPromises()

    await wrapper.find('button[aria-label*="첨부 파일 확인"]').trigger('click')
    expect(wrapper.text()).toContain('1 / 1번째 첨부 파일')

    await buttonByText(wrapper, '삭제').trigger('click')
    expect(wrapper.findAll('button[aria-label*="첨부 파일 확인"]')).toHaveLength(0)
  })

  it('등록을 완료하면 판매 상태로 올리고 등록한 상품 상세로 이동한다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await attachFile(wrapper.find('input[type="file"]'), new File(['x'], 'photo.jpg', { type: 'image/jpeg' }))
    await flushPromises()
    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()

    await buttonByText(wrapper, '완료').trigger('click')
    await flushPromises()

    expect(transitionProductStatus).toHaveBeenCalledWith(1001, 'ON_SALE', '등록 완료')
    expect(routerPushMock).toHaveBeenCalledWith({
      name: 'product-detail',
      params: { productId: 1001 },
    })
  })
})
