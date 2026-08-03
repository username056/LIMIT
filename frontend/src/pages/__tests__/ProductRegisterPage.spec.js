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
  getProductDraftProgress,
  getEvidenceHistory,
  getMyProduct,
  getProductImages,
  requestDeviceModel,
  transitionProductStatus,
  updateProductDraftProgress,
  updateProductImageOrder,
} from '../../api/products'
import {
  confirmDiagnosisValue,
  extractOcrText,
  getDiagnosis,
  parseBatteryReport,
  parseDxdiag,
} from '../../api/inspection'

// 수정 모드는 /seller/products/:productId/edit 로 들어옵니다. 테스트마다 params를 바꿀 수 있게
// 참조를 공유합니다(기본은 등록 모드).
const { routerPushMock, routeParams } = vi.hoisted(() => ({
  routerPushMock: vi.fn(),
  routeParams: {},
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams, query: {} }),
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
  requestDeviceModel: vi.fn(),
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
    delete routeParams.productId
    // clearAllMocks는 호출 기록만 지우고 구현은 남기므로, 테스트마다 기본 동작을 다시 세웁니다.
    getEvidenceHistory.mockResolvedValue([])
    transitionProductStatus.mockResolvedValue({})
    // jsdom에는 scrollTo 구현이 없어 단계 이동 시 예외가 나므로 스텁으로 대체합니다.
    window.scrollTo = vi.fn()
    // jsdom에는 카메라가 없습니다. 촬영 버튼이 보이는 기본 상태(카메라 있음)로 세웁니다.
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      writable: true,
      value: {
        getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }),
        enumerateDevices: vi.fn().mockResolvedValue([{ kind: 'videoinput' }]),
      },
    })
    // jsdom의 video 요소에는 play가 없습니다.
    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined)
    // jsdom에는 실제 영상·캔버스가 없어 촬영 프레임을 만들 수 없습니다. 화면이 준비된 상태로 둡니다.
    Object.defineProperty(window.HTMLVideoElement.prototype, 'videoWidth', {
      configurable: true,
      get: () => 640,
    })
    Object.defineProperty(window.HTMLVideoElement.prototype, 'videoHeight', {
      configurable: true,
      get: () => 480,
    })
    window.HTMLCanvasElement.prototype.getContext = vi.fn(() => ({ drawImage: vi.fn() }))
    window.HTMLCanvasElement.prototype.toBlob = vi.fn((callback) => {
      callback(new Blob(['shot'], { type: 'image/jpeg' }))
    })
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
    requestDeviceModel.mockResolvedValue({
      requestId: 9001,
      status: 'PENDING',
      resolvedModelId: 202,
      resolvedCategoryId: 202,
      categoryId: 10,
      manufacturer: 'LG',
      modelName: 'gram Pro 17',
      modelCode: '17Z90SP',
      osFamily: 'ANDROID',
    })
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
    expect(generateChecklist).toHaveBeenCalledWith({
      deviceModelId: 101,
      confirmedFeatures: [],
    })

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
      confirmedFeatures: [],
    })
    expect(getProductChecklist).toHaveBeenCalledWith(1001)
    expect(wrapper.text()).toContain('검수용 기기 촬영')
  })

  it('Windows 검사기를 같은 웹 도메인의 기본 경로에서 내려받을 수 있다', async () => {
    getProductChecklist.mockResolvedValue([{
      checklistItemId: 7003,
      itemCode: 'LAP-SCR-014',
      name: 'Windows 시스템 정보',
      evidenceType: 'DOCUMENT',
      automationType: 'FILE_PARSE',
      parserType: 'DXDIAG',
      isRequired: true,
      status: 'PENDING',
    }])
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await goToCaptureStep(wrapper)

    const downloadLink = wrapper.find('a[download]')
    expect(downloadLink.exists()).toBe(true)
    expect(downloadLink.attributes('href')).toBe('/downloads/LimitScanner.exe')
    expect(downloadLink.text()).toContain('진단 프로그램 다운로드')
  })

  it('AI 체크리스트 조사 중 진행률과 움직이는 점을 표시한다', async () => {
    let resolveChecklist
    generateChecklist.mockReturnValue(new Promise((resolve) => {
      resolveChecklist = resolve
    }))
    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await wrapper.findAll('select')[0].setValue('10')
    await flushPromises()

    vi.useFakeTimers()
    try {
      await wrapper.findAll('select')[1].setValue('101')
      await wrapper.vm.$nextTick()

      const progress = wrapper.get('[role="progressbar"][aria-label="AI 체크리스트 조사 진행률"]')
      expect(progress.attributes('aria-valuenow')).toBe('8')
      const initialStatus = wrapper.get('[role="status"]').text()
      expect(initialStatus).toContain('등록된 모델과 기본 체크리스트를 확인하는 중.')

      vi.advanceTimersByTime(450)
      await wrapper.vm.$nextTick()
      expect(wrapper.get('[role="status"]').text()).not.toBe(initialStatus)

      resolveChecklist({
        deviceModelId: 101,
        manufacturer: 'Samsung',
        modelName: 'Galaxy Book',
        osFamily: 'WINDOWS',
        aiApplied: false,
        items: templateItems,
      })
      await flushPromises()
      expect(wrapper.find('[role="status"]').exists()).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('Windows 모델의 AI 추가 항목을 노출하고 판매자가 선택해 적용한다', async () => {
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
        evidenceType: 'SELLER_CONFIRMATION',
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
    expect(wrapper.text()).toContain('AI 추가 항목 선택 가능')
    expect(wrapper.text()).toContain('내장 카메라')
    const cameraFeature = wrapper.get('input[type="checkbox"][value="CAMERA"]')
    await cameraFeature.setValue(true)
    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({
      confirmedFeatures: ['CAMERA'],
    }))
  })

  it('기본 항목과 선택한 AI 항목을 촬영·업로드와 직접 확인으로 나눠 같은 수로 표시한다', async () => {
    generateChecklist.mockResolvedValue({
      deviceModelId: 101,
      manufacturer: 'Samsung',
      modelName: 'Galaxy Book',
      osFamily: 'WINDOWS',
      aiApplied: true,
      items: templateItems.map((item) => ({ ...item, required: item.isRequired })),
      aiSuggestions: [{
        featureCode: 'PORTS',
        featureName: '외부 포트',
        evidenceStatus: 'VERIFIED',
        reason: '공식 사양에서 외부 포트를 확인했습니다.',
        checkGuide: '외부 장치를 연결해 인식 상태를 확인하세요.',
        sourceUrl: 'https://www.samsung.com/example',
        sourceTitle: 'Galaxy Book 공식 사양',
        evidenceType: 'VIDEO',
      }],
      reviewCandidates: [],
    })
    getProductChecklist.mockResolvedValue([
      { checklistItemId: 7001, itemCode: 'EXT-001', name: '전면·후면·측면 외관', evidenceType: 'PHOTO', isRequired: true, status: 'PENDING' },
      { checklistItemId: 7002, itemCode: 'PRV-004', name: '계정 제거 및 초기화', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, status: 'PENDING' },
      { checklistItemId: 7003, itemCode: 'LAP-FTR-PORT', name: '외부 포트', evidenceType: 'VIDEO', isRequired: true, status: 'PENDING' },
    ])

    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await fillDeviceStep(wrapper)

    expect(wrapper.text()).toContain('기본 2개 + AI 선택 0개 = 전체 2개')
    expect(wrapper.text()).toContain('촬영·업로드 1개 · 직접 확인 1개')

    await wrapper.get('input[type="checkbox"][value="PORTS"]').setValue(true)

    expect(wrapper.text()).toContain('기본 2개 + AI 선택 1개 = 전체 3개')
    expect(wrapper.text()).toContain('촬영·업로드 2개 · 직접 확인 1개')

    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('촬영·업로드 항목 2개')
    expect(wrapper.text()).toContain('현재 진행률: 2개 중 0개 등록 완료')
  })

  it('스마트폰 모델도 관리자 검토 전 AI 후보를 판매자에게 노출한다', async () => {
    getDeviceCategories.mockResolvedValue([{ categoryId: 10, name: '스마트폰' }])
    getDeviceModels.mockResolvedValue([{
      deviceModelId: 101,
      manufacturerName: 'Samsung',
      modelName: 'Galaxy S24',
      defaultOs: 'ANDROID',
    }])
    generateChecklist.mockResolvedValue({
      deviceModelId: 101,
      manufacturer: 'Samsung',
      modelName: 'Galaxy S24',
      osFamily: 'ANDROID',
      aiApplied: true,
      items: templateItems.map((item) => ({ ...item, required: item.isRequired })),
      aiSuggestions: [{
        featureCode: 'WIRELESS_CHARGING',
        featureName: '무선 충전',
        evidenceStatus: 'VERIFIED',
        reason: '공식 사양에서 무선 충전을 확인했습니다.',
        checkGuide: '호환 충전기로 충전 상태를 확인하세요.',
        sourceUrl: 'https://www.samsung.com/example',
        sourceTitle: 'Galaxy S24 공식 사양',
        evidenceType: 'VIDEO',
      }],
      reviewCandidates: [],
    })

    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await fillDeviceStep(wrapper)

    expect(generateChecklist).toHaveBeenCalledWith({
      deviceModelId: 101,
      confirmedFeatures: [],
    })
    expect(getChecklistTemplate).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('무선 충전')
    await wrapper.get('input[type="checkbox"][value="WIRELESS_CHARGING"]').setValue(true)

    await buttonByText(wrapper, '다음 단계').trigger('click')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({
      confirmedFeatures: ['WIRELESS_CHARGING'],
    }))
  })

  it('AI 조사 실패와 기본 정책 적용을 판매자에게 구분해 표시한다', async () => {
    generateChecklist.mockResolvedValue({
      deviceModelId: 101,
      manufacturer: 'Samsung',
      modelName: 'Galaxy Book',
      osFamily: 'WINDOWS',
      aiApplied: false,
      researchStatus: 'FAILED',
      items: templateItems.map((item) => ({ ...item, required: item.isRequired })),
      aiSuggestions: [],
      reviewCandidates: [],
    })

    const wrapper = mount(ProductRegisterPage, { global: globalOptions })
    await flushPromises()
    await fillDeviceStep(wrapper)

    expect(wrapper.text()).toContain('AI 조사 실패 · 기본 정책 적용')
    expect(wrapper.text()).toContain('관리자가 실패 원인을 확인하고 재조사할 수 있으며')
    expect(wrapper.text()).not.toContain('AI 연결 없이')
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

    expect(wrapper.text()).toContain('개인 정보 보호를 위해 반드시 초기화를 진행해주세요.')
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
    expect(wrapper.text()).toContain('저장됐습니다')

    // CPU 외에 인식되지 않은 필드(RAM 등)도 드롭다운 없이 바로 타이핑할 수 있는 빈 입력으로 보인다.
    const ramInput = wrapper.find('input[aria-label="RAM 값"]')
    expect(ramInput.exists()).toBe(true)
    expect(ramInput.element.value).toBe('')

    // 저장 후 값을 다시 고치기 시작하면, 방금 본 "저장됐습니다"는 이제 그 값 얘기가 아니므로 사라진다.
    await valueInput.setValue('Intel i7-1165G7 (다시 수정)')
    expect(wrapper.text()).not.toContain('저장됐습니다')
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
  describe('모델 목록 탐색', () => {
    it('모델을 제조사·모델명으로 검색하고 제조사 안에서 정렬한다', async () => {
      getDeviceModels.mockResolvedValue([
        { deviceModelId: 101, manufacturerName: 'Samsung', modelName: 'Zeta Book', modelCode: 'NT750XGK' },
        { deviceModelId: 102, manufacturerName: 'LG', modelName: 'gram Pro 17', modelCode: '17Z90SP' },
        { deviceModelId: 103, manufacturerName: 'Samsung', modelName: 'Alpha Book', modelCode: 'NT960XGK' },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await wrapper.findAll('select')[0].setValue('10')
      await flushPromises()

      const groups = wrapper.findAll('select')[1].findAll('optgroup')
      expect(groups[0].findAll('option').map((node) => node.text())).toEqual([
        'Alpha Book (NT960XGK)',
        'Zeta Book (NT750XGK)',
      ])

      await wrapper.find('input[aria-label="기기 모델 검색"]').setValue('gram')
      const labels = wrapper.findAll('select')[1].findAll('option').map((node) => node.text())
      expect(labels).toContain('gram Pro 17 (17Z90SP)')
      expect(labels).not.toContain('Zeta Book (NT750XGK)')
    })

    it('모델 API가 배열이 아닌 값을 반환하면 빈 목록으로 안전하게 처리한다', async () => {
      getDeviceModels.mockResolvedValue(null)

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await wrapper.findAll('select')[0].setValue('10')
      await flushPromises()

      expect(wrapper.findAll('select')[1].findAll('optgroup')).toHaveLength(0)
      expect(wrapper.find('input[aria-label="기기 모델 검색"]').attributes('disabled')).toBeUndefined()
    })

    it('승인된 모델을 모델 코드로 서버 검색해 목록에서 찾는다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await wrapper.findAll('select')[0].setValue('10')
      await flushPromises()
      getDeviceModels.mockResolvedValue([{
        deviceModelId: 201,
        manufacturerName: 'Samsung',
        modelName: 'Galaxy S25',
        modelCode: 'SM-S931N',
      }])

      await wrapper.find('input[aria-label="기기 모델 검색"]').setValue('SM-S931N')
      await buttonByText(wrapper, '검색').trigger('click')
      await flushPromises()

      expect(getDeviceModels).toHaveBeenLastCalledWith({
        categoryId: 10,
        keyword: 'SM-S931N',
        page: 0,
        size: 100,
      })
      expect(wrapper.findAll('select')[1].text()).toContain('Galaxy S25 (SM-S931N)')
    })

    it('카테고리를 빠르게 바꿔도 이전 모델 응답이 현재 목록을 덮어쓰지 않는다', async () => {
      let resolveFirst
      let resolveSecond
      getDeviceCategories.mockResolvedValue([
        { categoryId: 10, name: '노트북' },
        { categoryId: 20, name: '스마트폰' },
      ])
      getDeviceModels
        .mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve }))
        .mockReturnValueOnce(new Promise((resolve) => { resolveSecond = resolve }))

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      const categorySelect = wrapper.findAll('select')[0]
      await categorySelect.setValue('10')
      await categorySelect.setValue('20')

      resolveSecond([{ deviceModelId: 201, manufacturerName: 'Samsung', modelName: 'Galaxy S24' }])
      await flushPromises()
      resolveFirst([{ deviceModelId: 101, manufacturerName: 'LG', modelName: 'gram Pro' }])
      await flushPromises()

      const labels = wrapper.findAll('select')[1].findAll('option').map((node) => node.text())
      expect(labels).toContain('Galaxy S24')
      expect(labels).not.toContain('gram Pro')
    })

    it('모델 목록 조회에 실패하면 오류를 안내한다', async () => {
      getDeviceModels.mockRejectedValueOnce(new Error('network error'))

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await wrapper.findAll('select')[0].setValue('10')
      await flushPromises()

      expect(wrapper.text()).toContain('모델 목록을 불러오지 못했습니다')
    })
  })

  describe('카탈로그에 없는 기기 모델 검토 요청', () => {
    it('모델을 제조사별로 묶고 내부 carrier 모델은 숨긴다', async () => {
      getDeviceModels.mockResolvedValue([
        {
          deviceModelId: 101,
          manufacturerName: 'Samsung',
          modelName: 'Galaxy Book',
          modelCode: 'NT750XGK',
        },
        {
          deviceModelId: 102,
          manufacturerName: 'LG',
          modelName: 'gram Pro',
          modelCode: '17Z90SP',
        },
        {
          deviceModelId: 199,
          manufacturerName: null,
          modelName: '기타 (직접 입력)',
          modelCode: 'ETC-LAPTOP',
        },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await wrapper.findAll('select')[0].setValue('10')
      await flushPromises()

      expect(wrapper.findAll('select')[1].findAll('optgroup').map((node) => node.attributes('label')))
        .toEqual(['Samsung', 'LG'])
      expect(wrapper.findAll('select')[1].text()).not.toContain('기타 (직접 입력)')
    })

    it('직접 입력한 모델을 즉시 등록하고 AI 체크리스트를 생성한다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await buttonByText(wrapper, '찾는 모델이 없나요? 직접 입력').trigger('click')

      await wrapper.find('select[aria-label="신규 모델 카테고리"]').setValue('10')
      await wrapper.find('input[placeholder="예: Samsung"]').setValue('LG')
      await wrapper.find('input[placeholder="예: Galaxy S25"]').setValue('gram Pro 17')
      await wrapper.findAll('input[maxlength="50"]')
        .find((input) => !input.attributes('placeholder'))
        .setValue('17Z90SP')
      await buttonByText(wrapper, '모델 검토 요청').trigger('click')
      await flushPromises()

      expect(requestDeviceModel).toHaveBeenCalledWith({
        categoryId: 10,
        manufacturer: 'LG',
        modelName: 'gram Pro 17',
        modelCode: '17Z90SP',
        osFamily: 'ANDROID',
      })
      expect(createProduct).not.toHaveBeenCalled()
      expect(generateChecklist).toHaveBeenCalledWith({
        deviceModelId: 202,
        confirmedFeatures: [],
      })
      expect(wrapper.text()).toContain('새 모델을 바로 사용할 수 있습니다')
    })
  })

  // 수정은 고치러 들어오는 흐름이라 기기 정보부터 보이고, 무엇이든 고칠 수 있어야 합니다.
  describe('상품 수정', () => {
    beforeEach(() => {
      routeParams.productId = '1001'
      getMyProduct.mockResolvedValue({
        productId: 1001,
        status: 'DRAFT',
        name: '수정할 상품',
        description: '설명',
        price: 850000,
        category: { categoryId: 10 },
        device: { deviceModelId: 101, color: '블랙', storageGb: 256 },
      })
      getProductDraftProgress.mockResolvedValue({ step: 3, results: {} })
    })

    it('진행 단계가 남아 있어도 1단계부터 연다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()

      expect(wrapper.text()).toContain('판매할 기기를 등록해 주세요.')
    })

    it('기존 정보를 채워서 보여준다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()

      expect(wrapper.find('input[placeholder="예: 갤럭시 S24 256GB 자급제"]').element.value)
        .toBe('수정할 상품')
      expect(wrapper.findAll('select')[0].element.value).toBe('10')
    })

    // 카테고리·기기 모델도 고칠 수 있어야 합니다. 예전에는 수정 모드에서 잠겨 있었습니다.
    it('카테고리와 기기 모델을 잠그지 않는다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()

      const selects = wrapper.findAll('select')
      expect(selects[0].attributes('disabled')).toBeUndefined()
      expect(selects[1].attributes('disabled')).toBeUndefined()
    })

    // persistDraftProgress는 체크한 항목만 SUCCESS로 덮어쓰고, 체크 해제는 기존 값이 SUCCESS일
    // 때만 지웁니다. 실동작 점검(DeviceCheckPage)에서 넘어온 FAILED는 관련 없는 체크박스를
    // 바꿔도 조용히 사라지면 안 됩니다 — 실제로는 안 되는 기능이 확인된 것처럼 보일 수 있습니다.
    it('개인정보 체크를 바꿔도 실동작 점검에서 온 FAILED 결과는 지우지 않는다', async () => {
      getProductChecklist.mockResolvedValue([
        { checklistItemId: 7002, itemCode: 'PRV-004', name: '계정 제거 및 초기화', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, status: 'PENDING' },
        { checklistItemId: 7003, itemCode: 'LAP-KBD-005', name: '키보드 실동작 확인', evidenceType: 'SELLER_CONFIRMATION', isRequired: true, status: 'PENDING' },
      ])
      // 키보드 실동작 점검이 이미 실패로 기록돼 있는 상태를 가정합니다.
      getProductDraftProgress.mockResolvedValue({ step: 3, results: { 7003: 'FAILED' } })

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()

      await buttonByText(wrapper, '다음 단계').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '다음 단계로').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('개인정보를 정리했는지 확인해 주세요.')

      const checkboxes = wrapper.findAll('input[type="checkbox"]')
      expect(checkboxes).toHaveLength(2)
      // FAILED로 기록된 키보드 항목이 아니라, 관련 없는 개인정보 항목만 체크합니다.
      await checkboxes[0].setValue(true)
      await flushPromises()

      const [, payload] = updateProductDraftProgress.mock.calls.at(-1)
      expect(payload.step).toBe(3)
      expect(payload.results).toEqual(expect.arrayContaining([
        { checklistItemId: 7002, result: 'SUCCESS' },
        { checklistItemId: 7003, result: 'FAILED' },
      ]))
      expect(payload.results).toHaveLength(2)
    })
  })

  // 2단계 촬영/업로드 선택 —
  // 폰에서는 카메라만 열려 앨범을 못 쓰고, 노트북에서는 촬영을 못 하던 문제를 버튼 분리로 풉니다.
  describe('촬영과 파일 업로드 선택', () => {
    it('카메라가 있는 기기의 사진 항목에는 촬영과 파일 업로드를 함께 보여준다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      expect(buttonByText(wrapper, '촬영하기')).toBeDefined()
      expect(wrapper.text()).toContain('파일 업로드')
    })

    // 필수 항목 + checklistGuideImages.js에 등록된 카테고리·itemCode 조합이면
    // 서버 guide 문구 대신 프론트에 하드코딩해 둔 사진·설명이 뜹니다.
    it('필수 항목이고 카테고리·itemCode가 등록돼 있으면 지정된 사진과 설명을 보여준다', async () => {
      getDeviceCategories.mockResolvedValueOnce([{ categoryId: 10, name: 'Windows 노트북' }])
      getProductChecklist.mockResolvedValue([
        {
          checklistItemId: 7001,
          itemCode: 'EXT-001',
          name: '전면·후면·측면 외관',
          evidenceType: 'PHOTO',
          isRequired: true,
          status: 'PENDING',
          guide: '서버가 내려준 원본 가이드 문구',
        },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      expect(wrapper.find('[role="dialog"][aria-label="촬영 가이드"]').exists()).toBe(false)

      await wrapper.find('button[aria-label="전면·후면·측면 외관 촬영 가이드 보기"]').trigger('click')

      const modal = wrapper.find('[role="dialog"][aria-label="촬영 가이드"]')
      expect(modal.exists()).toBe(true)
      expect(modal.text()).toContain('외관 손상 여부 확인')
      expect(modal.text()).toContain('사면 테두리가 모두 잘 보이도록')
      expect(modal.text()).not.toContain('서버가 내려준 원본 가이드 문구')
      expect(modal.find('img').attributes('src')).toBeTruthy()

      await modal.find('button[aria-label="닫기"]').trigger('click')
      expect(wrapper.find('[role="dialog"][aria-label="촬영 가이드"]').exists()).toBe(false)
    })

    // 필수 항목이어도 카테고리·itemCode 조합이 아직 등록 안 됐으면(플레이스홀더 미작성)
    // 서버 guide 문구 + evidenceType에 맞는 범용 이미지로 대체합니다.
    it('필수 항목이어도 등록되지 않은 조합이면 서버 문구와 범용 이미지를 보여준다', async () => {
      getProductChecklist.mockResolvedValue([
        {
          checklistItemId: 7009,
          itemCode: 'CUSTOM-999',
          name: '커스텀 확인 항목',
          evidenceType: 'PHOTO',
          isRequired: true,
          status: 'PENDING',
          guide: '커스텀 항목 촬영 가이드입니다.',
        },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await wrapper.find('button[aria-label="커스텀 확인 항목 촬영 가이드 보기"]').trigger('click')

      const modal = wrapper.find('[role="dialog"][aria-label="촬영 가이드"]')
      expect(modal.find('img').attributes('src')).toBeTruthy()
      expect(modal.text()).toContain('커스텀 항목 촬영 가이드입니다.')
    })

    // 비필수 항목은 카테고리·itemCode가 등록돼 있어도 항상 서버 문구를 그대로 씁니다.
    it('비필수 항목은 등록된 조합이 있어도 무시하고 서버 문구를 그대로 보여준다', async () => {
      getDeviceCategories.mockResolvedValueOnce([{ categoryId: 10, name: 'Windows 노트북' }])
      getProductChecklist.mockResolvedValue([
        {
          checklistItemId: 7011,
          itemCode: 'EXT-001',
          name: '전면·후면·측면 외관',
          evidenceType: 'PHOTO',
          isRequired: false,
          status: 'PENDING',
          guide: '비필수 항목의 서버 기본 가이드 문구',
        },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await wrapper.find('button[aria-label="전면·후면·측면 외관 촬영 가이드 보기"]').trigger('click')

      const modal = wrapper.find('[role="dialog"][aria-label="촬영 가이드"]')
      expect(modal.text()).toContain('비필수 항목의 서버 기본 가이드 문구')
      expect(modal.text()).not.toContain('사면 테두리가 모두 잘 보이도록')
    })

    // 영상 녹화는 지원하지 않습니다. 촬영 버튼을 보여주면 눌러도 할 수 있는 게 없습니다.
    // 할 수 없는 일을 안내하면 사용자는 없는 버튼을 찾아 헤맵니다.
    it('영상 항목의 버튼 문구에는 촬영을 넣지 않는다', async () => {
      getProductChecklist.mockResolvedValue([
        {
          checklistItemId: 7003,
          itemCode: 'SCR-002',
          name: '화면 전체 터치',
          evidenceType: 'VIDEO',
          isRequired: true,
          status: 'PENDING',
        },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      expect(wrapper.text()).toContain('파일 업로드')
      expect(wrapper.text()).not.toContain('촬영 또는 파일 업로드')
    })

    it('영상 항목에는 파일 업로드만 보여준다', async () => {
      getProductChecklist.mockResolvedValue([
        {
          checklistItemId: 7003,
          itemCode: 'SCR-002',
          name: '화면 전체 터치',
          evidenceType: 'VIDEO',
          isRequired: true,
          status: 'PENDING',
        },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      expect(buttonByText(wrapper, '촬영하기')).toBeUndefined()
      expect(wrapper.find('input[aria-label="검증 항목 파일 업로드"]').attributes('accept')).toBe('video/*')
    })

    // 배터리 리포트·시스템 진단 정보는 기기가 내보낸 파일을 그대로 올려야 값을 신뢰할 수 있습니다.
    // 화면을 찍은 사진은 업로드로 받아 주되 촬영 버튼으로 권하지는 않습니다.
    it('진단 자료 항목에는 촬영 버튼을 두지 않고 파일 형식만 열어 준다', async () => {
      getProductChecklist.mockResolvedValue([
        {
          checklistItemId: 7004,
          itemCode: 'BAT-001',
          name: '배터리 리포트',
          evidenceType: 'DIAGNOSTIC_FILE',
          isRequired: true,
          status: 'PENDING',
        },
      ])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      expect(buttonByText(wrapper, '촬영하기')).toBeUndefined()
      const accept = wrapper.find('input[aria-label="검증 항목 파일 업로드"]').attributes('accept')
      expect(accept).toContain('text/html')
      expect(accept).toContain('text/plain')
      // 진단 앱이 파일을 못 내보내면 화면 사진으로도 올릴 수 있어야 합니다.
      expect(accept).toContain('image/jpeg')
    })

    it('카메라가 없는 기기에서는 사진 항목에도 파일 업로드만 보여준다', async () => {
      navigator.mediaDevices.enumerateDevices.mockResolvedValue([{ kind: 'audioinput' }])

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      expect(buttonByText(wrapper, '촬영하기')).toBeUndefined()
      expect(wrapper.find('input[aria-label="검증 항목 파일 업로드"]').attributes('accept')).toBe('image/*')
    })

    it('촬영을 시작하면 미리보기 박스에 카메라 화면을 띄우고 찍기·취소만 남긴다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()

      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled()
      expect(wrapper.find('video').exists()).toBe(true)
      expect(buttonByText(wrapper, '사진 찍기')).toBeDefined()
      expect(buttonByText(wrapper, '촬영 끝내기')).toBeDefined()
      // 촬영 중에는 파일 선택을 함께 두지 않습니다('파일 업로드'는 단계 제목에도 있어 입력으로 확인).
      expect(wrapper.find('input[aria-label="검증 항목 파일 업로드"]').exists()).toBe(false)
    })

    // 흔들리거나 잘린 사진을 그대로 올리면 올린 뒤에야 알게 됩니다.
    it('찍은 사진을 먼저 보여주고 저장 여부를 묻는다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '사진 찍기').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('이 사진으로 하시겠습니까?')
      expect(buttonByText(wrapper, '예 (저장)')).toBeDefined()
      expect(buttonByText(wrapper, '아니오 (다시 촬영)')).toBeDefined()
      // 묻는 동안에는 아직 올리지 않습니다.
      expect(createEvidenceUploadUrl).not.toHaveBeenCalled()
    })

    // 찍은 사진을 v-if로 갈아 끼우면 video가 DOM에서 빠져 스트림 연결이 끊깁니다.
    // 그러면 다시 찍을 때 '카메라 화면이 아직 준비되지 않았습니다'가 나고 화면이 빕니다.
    it('사진을 찍어도 카메라 video는 계속 붙어 있다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()
      const videoBefore = wrapper.find('video').element

      await buttonByText(wrapper, '사진 찍기').trigger('click')
      await flushPromises()

      // 확인 중에도 video가 살아 있어야 다시 찍기가 바로 됩니다.
      expect(wrapper.find('video').exists()).toBe(true)
      expect(wrapper.find('video').element).toBe(videoBefore)
      expect(wrapper.find('img[alt="방금 촬영한 사진"]').exists()).toBe(true)
    })

    it('저장한 뒤에도 같은 video가 유지되어 곧바로 다시 찍을 수 있다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()
      const videoBefore = wrapper.find('video').element

      await buttonByText(wrapper, '사진 찍기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '예 (저장)').trigger('click')
      await flushPromises()

      expect(wrapper.find('video').element).toBe(videoBefore)
      expect(wrapper.text()).not.toContain('카메라 화면이 아직 준비되지 않았습니다')
    })

    it('아니오를 누르면 올리지 않고 카메라를 켜 둔 채 다시 찍게 한다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '사진 찍기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '아니오 (다시 촬영)').trigger('click')
      await flushPromises()

      expect(createEvidenceUploadUrl).not.toHaveBeenCalled()
      expect(wrapper.text()).not.toContain('이 사진으로 하시겠습니까?')
      // 카메라가 그대로 켜져 있어야 바로 다시 찍을 수 있습니다.
      expect(wrapper.find('video').exists()).toBe(true)
      expect(buttonByText(wrapper, '사진 찍기')).toBeDefined()
    })

    // 여러 각도를 잇달아 찍는 흐름을 저장 한 번으로 끊으면 안 됩니다.
    it('저장한 뒤에도 카메라를 유지해 계속 찍을 수 있다', async () => {
      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '사진 찍기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '예 (저장)').trigger('click')
      await flushPromises()

      expect(createEvidenceUploadUrl).toHaveBeenCalledTimes(1)
      expect(completeEvidence).toHaveBeenCalledTimes(1)
      expect(wrapper.find('video').exists()).toBe(true)

      // 두 번째 촬영도 이어서 됩니다.
      await buttonByText(wrapper, '사진 찍기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '예 (저장)').trigger('click')
      await flushPromises()

      expect(createEvidenceUploadUrl).toHaveBeenCalledTimes(2)
      expect(completeEvidence).toHaveBeenCalledTimes(2)
    })

    it('촬영 끝내기를 누르면 카메라를 끄고 원래 버튼으로 돌아간다', async () => {
      const stop = vi.fn()
      navigator.mediaDevices.getUserMedia.mockResolvedValue({ getTracks: () => [{ stop }] })

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()
      await buttonByText(wrapper, '촬영 끝내기').trigger('click')
      await flushPromises()

      // 카메라를 놓아주지 않으면 표시등이 남고 다른 앱이 카메라를 쓸 수 없습니다.
      expect(stop).toHaveBeenCalled()
      expect(wrapper.find('video').exists()).toBe(false)
      expect(buttonByText(wrapper, '촬영하기')).toBeDefined()
    })

    it('권한을 거부하면 허용 방법을 알려주고 파일 업로드로 되돌린다', async () => {
      const denied = new Error('denied')
      denied.name = 'NotAllowedError'
      navigator.mediaDevices.getUserMedia.mockRejectedValue(denied)

      const wrapper = mount(ProductRegisterPage, { global: globalOptions })
      await flushPromises()
      await goToCaptureStep(wrapper)

      await buttonByText(wrapper, '촬영하기').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('카메라 사용이 차단되어 있습니다')
      expect(wrapper.find('video').exists()).toBe(false)
      // 촬영이 막혀도 파일 선택으로는 계속 올릴 수 있어야 합니다.
      expect(wrapper.find('input[aria-label="검증 항목 파일 업로드"]').attributes('accept')).toBe('image/*')
    })
  })
})
