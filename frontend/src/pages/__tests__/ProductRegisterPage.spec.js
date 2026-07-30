import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductRegisterPage from '../ProductRegisterPage.vue'
import {
  completeEvidence,
  completeProductImage,
  createEvidenceUploadUrl,
  createProductImageUploadUrl,
  createProduct,
  deleteEvidence,
  deleteProductImage,
  generateChecklist,
  getChecklistTemplate,
  getDeviceCategories,
  getDeviceModels,
  getProductChecklist,
  getEvidenceHistory,
  getProductImages,
  transitionProductStatus,
  updateProductImageOrder,
} from '../../api/products'
import {
  confirmDiagnosisValue,
  extractOcrText,
  getDiagnosis,
  parseBatteryReport,
  parseDxdiag,
} from '../../api/inspection'

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
  completeReinspectionRequest: vi.fn(),
  completeProductImage: vi.fn(),
  createEvidenceUploadUrl: vi.fn(),
  createProductImageUploadUrl: vi.fn(),
  createProduct: vi.fn(),
  generateChecklist: vi.fn(),
  getChecklistTemplate: vi.fn(),
  getDeviceCategories: vi.fn(),
  getDeviceModels: vi.fn(),
  getHandoverGuide: vi.fn(),
  getReinspectionRequest: vi.fn(),
  getMyProduct: vi.fn(),
  getProductChecklist: vi.fn(),
  getProductDraftProgress: vi.fn(),
  getEvidenceHistory: vi.fn(),
  getProductImages: vi.fn(),
  deleteEvidence: vi.fn(),
  deleteProductImage: vi.fn(),
  transitionProductStatus: vi.fn(),
  updateProduct: vi.fn(),
  updateProductDraftProgress: vi.fn(),
  updateProductImageOrder: vi.fn(),
  uploadToPresignedUrl: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('../../api/inspection', () => ({
  extractOcrText: vi.fn(),
  parseDxdiag: vi.fn(),
  parseBatteryReport: vi.fn(),
  getDiagnosis: vi.fn(),
  confirmDiagnosisValue: vi.fn(),
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
  await wrapper.find('select[aria-label="저장 용량 선택"]').setValue('256')
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
    // clearAllMocks는 호출 기록만 지우고 구현은 남기므로, 테스트마다 기본 동작을 다시 세웁니다.
    getEvidenceHistory.mockResolvedValue([])
    transitionProductStatus.mockResolvedValue({})
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
    completeProductImage.mockResolvedValue({
      imageId: 1,
      imageType: 'THUMBNAIL',
      displayOrder: 0,
      imageUrl: 'https://storage.test/image',
      mimeType: 'image/jpeg',
    })
    createProductImageUploadUrl.mockResolvedValue({
      uploadId: 'image-upload-1',
      presignedUrl: 'https://storage.test/image-upload',
      requiredHeaders: { 'Content-Type': 'image/jpeg' },
    })
    getProductImages.mockResolvedValue([])
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

  it('임시저장은 저장 용량 없이도 저장된다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    await wrapper.findAll('select')[0].setValue('10')
    await flushPromises()
    await wrapper.findAll('select')[1].setValue('101')
    await flushPromises()
    await wrapper.find('input[placeholder="예: 갤럭시 S24 256GB 자급제"]').setValue('갤럭시 북')
    await wrapper.find('input[placeholder="판매 가격"]').setValue('850000')

    await buttonByText(wrapper, '임시저장').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({ storageGb: null }))
    expect(routerPushMock).toHaveBeenCalledWith({ name: 'seller-products' })
  })

  it('저장 용량은 드롭다운으로 고르거나 직접 입력할 수 있다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()

    const storageSelect = wrapper.find('select[aria-label="저장 용량 선택"]')
    expect(wrapper.text()).toContain('1TB')
    expect(wrapper.find('input[aria-label="저장 용량 직접 입력"]').exists()).toBe(false)

    // fillDeviceStep이 드롭다운으로 용량을 채우므로, 직접 입력은 그 뒤에 설정합니다.
    await fillDeviceStep(wrapper)
    await storageSelect.setValue('custom')
    const customInput = wrapper.find('input[aria-label="저장 용량 직접 입력"]')
    expect(customInput.exists()).toBe(true)

    await customInput.setValue('384')
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

  it('개인정보 확인은 필수라서 건너뛸 수 없다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    // 촬영은 건너뛸 수 있다
    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()
    await buttonByText(wrapper, '확인').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('개인정보를 정리했는지 확인해 주세요.')

    // 개인정보 확인은 건너뛸 수 없다 — 진행 버튼이 없고 4단계로 넘어가지 않는다
    await buttonByText(wrapper, '다음 단계로').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('건너뛸 수 없습니다')
    expect(buttonByText(wrapper, '계속 작성하기')).toBeUndefined()
    await buttonByText(wrapper, '확인').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('체크리스트 등록이 완료되었습니다.')
    expect(wrapper.text()).toContain('개인정보를 정리했는지 확인해 주세요.')
  })

  it('체크리스트 항목당 사진은 3개까지만 첨부할 수 있다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    for (let index = 0; index < 3; index += 1) {
      const fileInput = wrapper.find('input[accept="image/*"]')
      expect(fileInput.element.disabled).toBe(false)
      // eslint-disable-next-line no-await-in-loop
      await attachFile(fileInput, new File(['x'], `photo${index}.jpg`, { type: 'image/jpeg' }))
      // eslint-disable-next-line no-await-in-loop
      await flushPromises()
    }

    expect(wrapper.findAll('button[aria-label*="첨부 파일 확인"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('최대 3개까지 첨부했습니다')
    expect(wrapper.find('input[accept="image/*"]').element.disabled).toBe(true)
    expect(wrapper.text()).toContain('첨부 3 / 3')
  })

  it('OCR 자동화 항목은 업로드 후 자동 인식을 호출하고 인식값을 수정해 저장할 수 있다', async () => {
    getProductChecklist.mockResolvedValue([
      {
        checklistItemId: 7003,
        itemCode: 'LAP-SCR-013',
        name: '설정 정보 화면',
        evidenceType: 'PHOTO',
        automationType: 'OCR',
        isRequired: true,
        status: 'PENDING',
      },
      { checklistItemId: 7001, itemCode: 'EXT-001', name: '전면·후면·측면 외관', evidenceType: 'PHOTO', isRequired: true, status: 'PENDING' },
      { checklistItemId: 7002, itemCode: 'PRV-004', name: '계정 제거 및 초기화', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, status: 'PENDING' },
    ])
    completeEvidence.mockResolvedValue({ evidenceId: 9101, mediaUrl: 'https://storage.test/evidence', attemptNo: 1 })
    extractOcrText.mockResolvedValue({})
    getDiagnosis.mockResolvedValue({
      itemId: 7003,
      fields: [
        { fieldName: 'CPU', ocrValue: 'Intel i7-1165G7', fileParseValue: null, conflict: false, confirmedValue: null },
      ],
    })
    confirmDiagnosisValue.mockResolvedValue({
      itemId: 7003,
      fieldName: 'CPU',
      originalValue: 'Intel i7-1165G7',
      confirmedValue: 'Intel i7-1165G7 (확인함)',
      updatedAt: '2026-07-30T10:00:00',
    })

    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await attachFile(
      wrapper.find('input[accept="image/*"]'),
      new File(['x'], 'system-info.png', { type: 'image/png' }),
    )
    await flushPromises()

    expect(extractOcrText).toHaveBeenCalledWith(9101)
    expect(getDiagnosis).toHaveBeenCalledWith(7003)
    expect(wrapper.text()).toContain('자동 인식된 사양')
    expect(wrapper.text()).toContain('CPU')

    const valueInput = wrapper.find('input[aria-label="CPU 값"]')
    expect(valueInput.element.value).toBe('Intel i7-1165G7')

    await valueInput.setValue('Intel i7-1165G7 (확인함)')
    await wrapper.find('button[aria-label="CPU 저장"]').trigger('click')
    await flushPromises()

    expect(confirmDiagnosisValue).toHaveBeenCalledWith(7003, {
      fieldName: 'CPU',
      confirmedValue: 'Intel i7-1165G7 (확인함)',
    })
    expect(wrapper.text()).not.toContain('저장 중…')

    // CPU 외에 인식되지 않은 필드(RAM 등)도 드롭다운 없이 바로 타이핑할 수 있는 빈 입력으로 보인다.
    const ramInput = wrapper.find('input[aria-label="RAM 값"]')
    expect(ramInput.exists()).toBe(true)
    expect(ramInput.element.value).toBe('')
  })

  it('자동 인식이 완전히 실패하면 오류 안내와 함께 필드마다 직접 입력해 저장할 수 있다', async () => {
    getProductChecklist.mockResolvedValue([
      {
        checklistItemId: 7003,
        itemCode: 'LAP-SCR-013',
        name: '설정 정보 화면',
        evidenceType: 'PHOTO',
        automationType: 'OCR',
        isRequired: true,
        status: 'PENDING',
      },
    ])
    completeEvidence.mockResolvedValue({ evidenceId: 9101, mediaUrl: 'https://storage.test/evidence', attemptNo: 1 })
    extractOcrText.mockRejectedValue(new Error('OCR 인식에 실패했습니다.'))
    getDiagnosis.mockResolvedValue({ itemId: 7003, fields: [] })
    confirmDiagnosisValue.mockResolvedValue({
      itemId: 7003,
      fieldName: 'MODEL_NAME',
      originalValue: null,
      confirmedValue: 'Galaxy Book4 Pro',
      updatedAt: '2026-07-30T10:00:00',
    })

    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await attachFile(
      wrapper.find('input[accept="image/*"]'),
      new File(['x'], 'system-info.png', { type: 'image/png' }),
    )
    await flushPromises()

    expect(wrapper.text()).toContain('OCR 인식에 실패했습니다.')
    // 드롭다운으로 항목을 고르는 게 아니라, 인식되지 못한 필드마다 바로 입력창이 보인다.
    expect(wrapper.find('select[aria-label="직접 추가할 항목 선택"]').exists()).toBe(false)

    const modelNameInput = wrapper.find('input[aria-label="모델명 값"]')
    expect(modelNameInput.element.value).toBe('')
    await modelNameInput.setValue('Galaxy Book4 Pro')
    await wrapper.find('button[aria-label="모델명 저장"]').trigger('click')
    await flushPromises()

    expect(confirmDiagnosisValue).toHaveBeenCalledWith(7003, {
      fieldName: 'MODEL_NAME',
      confirmedValue: 'Galaxy Book4 Pro',
    })
    expect(modelNameInput.element.value).toBe('Galaxy Book4 Pro')
  })

  it('첨부 썸네일을 누르면 확인 모달이 열리고 삭제할 수 있다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await attachFile(wrapper.find('input[accept="image/*"]'), new File(['x'], 'photo.jpg', { type: 'image/jpeg' }))
    await flushPromises()

    await wrapper.find('button[aria-label*="첨부 파일 확인"]').trigger('click')
    expect(wrapper.text()).toContain('1 / 1번째 첨부 파일')

    deleteEvidence.mockResolvedValue({})
    await buttonByText(wrapper, '삭제').trigger('click')
    await flushPromises()

    expect(deleteEvidence).toHaveBeenCalledWith(1001, 7001, 1)
    expect(wrapper.findAll('button[aria-label*="첨부 파일 확인"]')).toHaveLength(0)
  })

  it('상품 이미지를 S3에 업로드하고 삭제 API와 동기화한다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await attachFile(
      wrapper.find('input[accept="image/jpeg,image/png,image/webp"]'),
      new File(['image'], 'product.jpg', { type: 'image/jpeg' }),
    )
    await flushPromises()

    expect(createProductImageUploadUrl).toHaveBeenCalledWith(1001, {
      filename: 'product.jpg',
      contentType: 'image/jpeg',
      fileSize: 5,
    })
    expect(completeProductImage).toHaveBeenCalledWith(1001, {
      uploadId: 'image-upload-1',
      imageType: 'THUMBNAIL',
      displayOrder: 0,
    })
    expect(wrapper.text()).toContain('대표')

    await wrapper.find('button[aria-label="상품 이미지 삭제"]').trigger('click')
    await flushPromises()
    expect(deleteProductImage).toHaveBeenCalledWith(1001, 1)
  })

  it('대표 이미지를 바꾸면 순서 변경 API 응답을 그대로 반영한다', async () => {
    completeProductImage
      .mockResolvedValueOnce({
        imageId: 1,
        imageType: 'THUMBNAIL',
        displayOrder: 0,
        imageUrl: 'https://storage.test/image-1',
        mimeType: 'image/jpeg',
      })
      .mockResolvedValueOnce({
        imageId: 2,
        imageType: 'DETAIL',
        displayOrder: 1,
        imageUrl: 'https://storage.test/image-2',
        mimeType: 'image/jpeg',
      })
    updateProductImageOrder.mockResolvedValue([
      { imageId: 2, imageType: 'THUMBNAIL', displayOrder: 0, imageUrl: 'https://storage.test/image-2', mimeType: 'image/jpeg' },
      { imageId: 1, imageType: 'DETAIL', displayOrder: 1, imageUrl: 'https://storage.test/image-1', mimeType: 'image/jpeg' },
    ])

    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    const imageInput = wrapper.find('input[accept="image/jpeg,image/png,image/webp"]')
    await attachFile(imageInput, new File(['a'], 'a.jpg', { type: 'image/jpeg' }))
    await flushPromises()
    await attachFile(imageInput, new File(['b'], 'b.jpg', { type: 'image/jpeg' }))
    await flushPromises()

    const promoteButton = wrapper.findAll('button')
      .find((button) => button.text() === '대표' && button.attributes('disabled') === undefined)
    await promoteButton.trigger('click')
    await flushPromises()

    expect(updateProductImageOrder).toHaveBeenCalledWith(1001, {
      imageIds: [1, 2],
      thumbnailImageId: 2,
    })
    expect(wrapper.text()).not.toContain('상품 이미지 순서를 변경하지 못했습니다.')
  })

  it('등록을 완료하면 판매 상태로 올리고 등록한 상품 상세로 이동한다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await attachFile(wrapper.find('input[accept="image/*"]'), new File(['x'], 'photo.jpg', { type: 'image/jpeg' }))
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

  it('1단계에서 임시저장을 누르면 상품을 저장하고 상품 관리로 나간다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await fillDeviceStep(wrapper)

    await buttonByText(wrapper, '임시저장').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledTimes(1)
    expect(transitionProductStatus).not.toHaveBeenCalled()
    expect(routerPushMock).toHaveBeenCalledWith({ name: 'seller-products' })
  })

  it('촬영 단계에서 임시저장을 누르면 판매를 시작하지 않고 나간다', async () => {
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    await buttonByText(wrapper, '임시저장').trigger('click')
    await flushPromises()

    expect(transitionProductStatus).not.toHaveBeenCalled()
    expect(routerPushMock).toHaveBeenCalledWith({ name: 'seller-products' })
  })
})
