<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseBadge from '../components/BaseBadge.vue'
import {
  completeEvidence,
  createEvidenceUploadUrl,
  createProduct,
  generateChecklist,
  getChecklistTemplate,
  getDeviceCategories,
  getDeviceModels,
  getHandoverGuide,
  getMyProduct,
  getProductChecklist,
  transitionProductStatus,
  updateProduct,
} from '../api/products'
import { compressImage, compressVideo } from '../utils/mediaOptimize'
import { MAX_PRICE_DIGITS, formatPriceDigits, toPriceDigits } from '../utils/priceInput'

const WIZARD_STEPS = [
  { number: 1, label: '기기 등록' },
  { number: 2, label: '촬영 및 파일 업로드' },
  { number: 3, label: '개인정보 관리' },
  { number: 4, label: '등록완료' },
]

// 체크리스트 항목 하나에 첨부할 수 있는 사진·영상 개수 상한입니다.
const MAX_MEDIA_PER_ITEM = 3

// TODO(대표 이미지): 필수 입력으로 두기로 했지만, S3 업로드 구현(feat/s3-media-storage)과
// 같은 파일에서 충돌하므로 임시 미리보기 UI를 걷어냈습니다. 그 브랜치가 dev에 들어오면
// listingImages 기반 업로드에 '최소 1장 필수' 규칙을 다시 붙이세요.
// 화면에서 거래 지역을 받지 않기로 했지만 CreateProductRequest의 tradeRegion에 @NotBlank가 남아 있어
// 값을 비우면 등록이 400으로 실패합니다. 백엔드에서 해당 제약이 풀리면 이 상수와 payload 항목을 함께 지우세요.
const DEFAULT_TRADE_REGION = '협의'

// 실제로 많이 쓰이는 용량만 골라 두고, 해당하지 않으면 직접 입력으로 넘어갑니다.
const STORAGE_OPTIONS = [16, 32, 64, 128, 256, 512, 1024]

function storageOptionLabel(gb) {
  return gb >= 1024 ? `${gb / 1024}TB` : `${gb}GB`
}

// 기종별 초기화 가이드 API/데이터가 아직 준비되지 않아(handover_guide 테이블 미생성),
// 조회 실패 시 OS 계열별 일반 초기화 안내로 대체합니다. 모델별 가이드가 생기면 이 대체 로직은 제거하세요.
const FALLBACK_GUIDES = {
  ANDROID: {
    title: '안드로이드 기기 일반 초기화 방법',
    steps: [
      { title: '계정 로그아웃', description: '설정 > 계정에서 Google/Samsung 계정을 로그아웃하고 제거하세요.' },
      { title: '화면 잠금 해제', description: '설정 > 보안에서 화면 잠금(PIN/패턴/지문)을 해제하세요.' },
      { title: '공장 초기화', description: '설정 > 일반 관리 > 초기화 > 모든 데이터 삭제(초기화)를 실행하세요.' },
    ],
  },
  IOS: {
    title: 'iOS 기기 일반 초기화 방법',
    steps: [
      { title: 'iCloud 로그아웃', description: '설정 > [내 이름] > 로그아웃을 눌러 iCloud와 Apple ID를 제거하세요.' },
      { title: '찾기 기능 끄기', description: '설정 > 찾기에서 "나의 iPhone 찾기"를 반드시 꺼주세요. 켜진 채로는 활성화 잠금이 남습니다.' },
      { title: '모든 콘텐츠 및 설정 지우기', description: '설정 > 일반 > 전송 또는 재설정 > 모든 콘텐츠 및 설정 지우기를 실행하세요.' },
    ],
  },
  WINDOWS: {
    title: 'Windows 노트북 일반 초기화 방법',
    steps: [
      { title: '계정 로그아웃', description: 'Microsoft 계정과 OneDrive 연결을 해제하세요.' },
      { title: '드라이브 암호화 해제', description: 'BitLocker 등 디스크 암호화를 사용 중이면 해제하세요.' },
      { title: 'PC 초기화', description: '설정 > 복구 > 이 PC 초기화 > 모든 항목 제거를 실행하세요.' },
    ],
  },
}

const route = useRoute()
const router = useRouter()
const categories = ref([])
const models = ref([])
const isSaving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const editingId = ref(null)
const draftProductId = ref(null)
const activeStep = ref(1)
const form = reactive({
  categoryId: '', deviceModelId: '', name: '', description: '', price: '',
  color: '', storageGb: '',
})



// 사용자가 직접 입력을 고른 상태. 수정 진입 시 목록에 없는 용량이면 자동으로 직접 입력으로 보여줍니다.
const isCustomStorage = ref(false)
const showStorageInput = computed(() => isCustomStorage.value
  || (form.storageGb !== '' && !STORAGE_OPTIONS.includes(Number(form.storageGb))))
const storageSelectValue = computed(() => {
  if (showStorageInput.value) return 'custom'
  return form.storageGb === '' ? '' : String(form.storageGb)
})
// 가격은 form.price에 숫자만 담아 두고, 화면에는 천 단위 쉼표를 붙여 보여줍니다.
// type="number"의 증감 화살표가 고가 상품 입력에 방해가 되어 문자 입력으로 바꿨습니다.
// 안내 문구는 평소에 숨기고, 입력이 실제로 거부됐을 때만 그 이유를 보여줍니다.
const priceRejection = ref('')
const priceFormatted = computed(() => formatPriceDigits(form.price))

// v-model이 아니라 :value + @input으로 직접 다루는 이유:
// 상한에 걸려 form.price가 그대로면 Vue가 DOM을 다시 그리지 않아서, 사용자가 더 누른
// 초과 문자가 입력창에 남습니다. 그래서 처리 후 DOM 값을 항상 되돌려 놓습니다.
function onPriceInput(event) {
  const raw = event.target.value
  const digits = raw.replace(/[^0-9]/g, '')

  if (digits.length > MAX_PRICE_DIGITS) {
    form.price = toPriceDigits(digits)
    priceRejection.value = 'max'
  } else {
    // 쉼표는 우리가 표시용으로 넣은 것이라 거부 대상이 아닙니다.
    priceRejection.value = raw.replace(/[0-9,]/g, '') ? 'non-digit' : ''
    form.price = digits
  }

  event.target.value = priceFormatted.value
}
const priceRejectionMessage = computed(() => {
  if (priceRejection.value === 'max') return `최대 ${MAX_PRICE_DIGITS}자리까지 입력할 수 있습니다.`
  if (priceRejection.value === 'non-digit') return '숫자만 입력해 주세요.'
  return ''
})

function onStorageSelect(event) {
  const { value } = event.target
  if (value === 'custom') {
    isCustomStorage.value = true
    form.storageGb = ''
    return
  }
  isCustomStorage.value = false
  form.storageGb = value
}


// 체크리스트 관련: templateItems는 항목 가이드/허용 형식을 보여주기 위한 모델 템플릿,
// checklistItems는 상품 생성 시 고정된 실제 스냅샷(evidence API 호출에 필요한 checklistItemId 포함).
const templateItems = ref([])
const checklistGeneration = ref(null)
const confirmedFeatures = ref([])
const isGeneratingChecklist = ref(false)
const checklistItems = ref([])
const activeCaptureItemId = ref(null)
// 백엔드 증거 업로드 API가 아직 스텁이라(항상 더미 응답) 실제 진행 상태를 신뢰할 수 없어
// 촬영 진행 상태는 화면(세션) 안에서만 관리합니다.
// captureState[checklistItemId] = { media: [{ key, previewUrl, evidenceType, name }], busy: '' | 'optimizing' | 'uploading' }
const captureState = reactive({})
const confirmState = reactive({})
const mediaPreview = ref(null)
// 체크리스트를 덜 채운 채 다음 단계를 누르면 인라인 문구만으로는 놓치기 쉬워 팝업으로 알립니다.
const alertMessage = ref('')
// 확인을 누르면 그대로 진행할 동작. 진행 없이 알리기만 할 때는 null입니다.
const alertProceed = ref(null)

function openAlert(message, proceed = null) {
  alertMessage.value = message
  alertProceed.value = proceed
}

function closeAlert() {
  alertMessage.value = ''
  alertProceed.value = null
}

function confirmAlert() {
  const proceed = alertProceed.value
  closeAlert()
  if (proceed) proceed()
}
let mediaKeySeq = 0

function mediaOf(checklistItemId) {
  return captureState[checklistItemId]?.media || []
}

function busyOf(checklistItemId) {
  return captureState[checklistItemId]?.busy || ''
}

function captureStatusOf(checklistItemId) {
  return busyOf(checklistItemId) || (mediaOf(checklistItemId).length ? 'captured' : 'idle')
}

function remainingSlots(checklistItemId) {
  return MAX_MEDIA_PER_ITEM - mediaOf(checklistItemId).length
}
const handoverGuide = ref(null)
const isLoadingHandoverGuide = ref(false)

const currentProductId = computed(() => editingId.value || draftProductId.value)
const selectedModel = computed(
  () => models.value.find((item) => String(item.deviceModelId) === String(form.deviceModelId)) || null,
)
const supportsGeneratedChecklist = computed(
  () => ['WINDOWS', 'LINUX'].includes(selectedModel.value?.defaultOs),
)
const confirmedFeatureLimitReached = computed(() => confirmedFeatures.value.length >= 5)
const mediaChecklistItems = computed(
  () => checklistItems.value.filter((item) => item.evidenceType !== 'SELLER_CONFIRMATION'),
)
const confirmationChecklistItems = computed(
  () => checklistItems.value.filter((item) => item.evidenceType === 'SELLER_CONFIRMATION'),
)
const capturedMediaCount = computed(
  () => mediaChecklistItems.value.filter((item) => mediaOf(item.checklistItemId).length > 0).length,
)
const confirmedCount = computed(
  () => confirmationChecklistItems.value.filter((item) => confirmState[item.checklistItemId]).length,
)
const activeCaptureItem = computed(
  () => mediaChecklistItems.value.find((item) => item.checklistItemId === activeCaptureItemId.value)
    || mediaChecklistItems.value[0]
    || null,
)
const isActiveItemBusy = computed(
  () => Boolean(activeCaptureItem.value && busyOf(activeCaptureItem.value.checklistItemId)),
)
const activeItemMedia = computed(
  () => (activeCaptureItem.value ? mediaOf(activeCaptureItem.value.checklistItemId) : []),
)
const isActiveItemFull = computed(() => activeItemMedia.value.length >= MAX_MEDIA_PER_ITEM)
const activeItemLatestMedia = computed(() => activeItemMedia.value[activeItemMedia.value.length - 1] || null)
const activeItemStatusLabel = computed(() => {
  const busy = activeCaptureItem.value && busyOf(activeCaptureItem.value.checklistItemId)
  if (busy === 'optimizing') return '최적화 중…'
  if (busy === 'uploading') return '업로드 중…'
  if (isActiveItemFull.value) return `최대 ${MAX_MEDIA_PER_ITEM}개까지 첨부했습니다`
  return activeItemMedia.value.length ? '사진·영상 추가하기' : '촬영 또는 파일 업로드'
})
const previewedMedia = computed(() => {
  if (!mediaPreview.value) return null
  return mediaOf(mediaPreview.value.checklistItemId)[mediaPreview.value.index] || null
})

function templateFor(itemCode) {
  return templateItems.value.find((item) => item.itemCode === itemCode) || null
}

function guideFor(item) {
  return item?.guide || templateFor(item?.itemCode)?.guide || ''
}

function evidenceStatusLabel(status) {
  return {
    VERIFIED: '공식 확인',
    LIKELY: '제품군 확인',
    UNKNOWN: '근거 부족',
    CONFLICTED: '자료 충돌',
  }[status] || status
}

// 자동 생성 체크리스트는 required, 기존 템플릿은 isRequired를 씁니다.
function isRequiredItem(item) {
  return item.required ?? item.isRequired ?? false
}

function evidenceTypeLabel(type) {
  return {
    PHOTO: '사진',
    VIDEO: '영상',
    DIAGNOSTIC_FILE: '진단파일',
    SELLER_CONFIRMATION: '확인',
  }[type] || type
}

function captureAccept(item) {
  if (!item) return ''
  if (item.evidenceType === 'VIDEO') return 'video/*'
  if (item.evidenceType === 'PHOTO') return 'image/*'
  return '*/*'
}

function clearCaptureState() {
  Object.keys(captureState).forEach((key) => {
    mediaOf(key).forEach((media) => URL.revokeObjectURL(media.previewUrl))
    delete captureState[key]
  })
  mediaPreview.value = null
}

function scrollToTop() {
  window.scrollTo(0, 0)
}

// 단계가 바뀌면 위자드 상단(단계 표시줄)부터 보이도록 항상 스크롤을 올립니다.
function setStep(step) {
  activeStep.value = step
  scrollToTop()
}

function resetForm() {
  editingId.value = null
  draftProductId.value = null
  activeStep.value = 1
  Object.assign(form, {
    categoryId: '', deviceModelId: '', name: '', description: '', price: '',
    color: '', storageGb: '',
  })
  models.value = []
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  isGeneratingChecklist.value = false
  checklistItems.value = []
  activeCaptureItemId.value = null
  handoverGuide.value = null
  isCustomStorage.value = false
  priceRejection.value = ''
  clearCaptureState()
  Object.keys(confirmState).forEach((key) => delete confirmState[key])
}

async function loadModels() {
  form.deviceModelId = ''
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  models.value = form.categoryId
    ? await getDeviceModels({ categoryId: form.categoryId, page: 0, size: 100 })
    : []
}

async function loadTemplatePreview() {
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  if (!form.deviceModelId) return
  isGeneratingChecklist.value = true
  errorMessage.value = ''
  try {
    if (supportsGeneratedChecklist.value) {
      const generated = await generateChecklist({
        deviceModelId: Number(form.deviceModelId),
        confirmedFeatures: [],
      })
      checklistGeneration.value = generated
      templateItems.value = generated.items || []
    } else {
      const template = await getChecklistTemplate(form.deviceModelId)
      templateItems.value = template.items || []
    }
  } catch (error) {
    errorMessage.value = error.message || '체크리스트를 불러오지 못했습니다.'
  } finally {
    isGeneratingChecklist.value = false
  }
}

// 기기 등록(1단계)이 실제 필수 구간입니다. 여기서 빠진 값이 있으면 다음 단계로 넘기지 않습니다.
// 반대로 2단계 촬영 체크리스트는 필수가 아니어서 건너뛸 수 있습니다.
// requireStorage: '다음 단계'는 저장 용량까지 요구하고, 중간 이탈용 '임시저장'은 요구하지 않습니다.
// TODO(필수 항목): 저장 용량·대표 이미지를 필수로 두기로 했지만, S3 업로드 브랜치와 같은 구간이라
// 병합 충돌을 줄이려고 검증을 미뤘습니다. 그 브랜치가 dev에 들어오면 여기에 다시 붙이세요.
function validateSaleInfo() {
  if (!form.name || form.price === '') {
    errorMessage.value = '상품명과 가격을 입력해 주세요.'
    return false
  }
  if (!Number.isFinite(Number(form.price)) || Number(form.price) < 1) {
    errorMessage.value = '가격은 1원 이상 입력해 주세요.'
    return false
  }
  if (form.storageGb !== '' && (!Number.isFinite(Number(form.storageGb)) || Number(form.storageGb) < 1)) {
    errorMessage.value = '저장 용량은 1GB 이상 입력해 주세요.'
    return false
  }
  return true
}

// 1단계 입력을 서버에 저장하고 체크리스트 스냅샷을 받아옵니다.
// '다음 단계'와 '임시저장'이 같은 저장 경로를 쓰도록 분리했습니다.
async function persistSaleInfo() {
  const payload = {
    name: form.name,
    description: form.description || null,
    price: Number(form.price),
    color: form.color || null,
    storageGb: form.storageGb ? Number(form.storageGb) : null,
    tradeRegion: DEFAULT_TRADE_REGION,
  }
  let productId = editingId.value
  if (productId) {
    await updateProduct(productId, payload)
  } else {
    const created = await createProduct({
      ...payload,
      categoryId: Number(form.categoryId),
      deviceModelId: Number(form.deviceModelId),
      ...(supportsGeneratedChecklist.value
        ? { confirmedFeatures: [...confirmedFeatures.value] }
        : {}),
    })
    productId = created.productId
    draftProductId.value = productId
  }
  checklistItems.value = await getProductChecklist(productId)
  activeCaptureItemId.value = mediaChecklistItems.value[0]?.checklistItemId || null
  return productId
}

function validateDeviceStep() {
  errorMessage.value = ''
  if (!form.categoryId || !form.deviceModelId) {
    errorMessage.value = '카테고리와 기기 모델을 선택해 주세요.'
    return false
  }
  return validateSaleInfo()
}

async function goToStep2() {
  if (!validateDeviceStep()) return

  isSaving.value = true
  try {
    await persistSaleInfo()
    setStep(2)
  } catch (error) {
    errorMessage.value = error.message || '상품 정보를 저장하지 못했습니다.'
  } finally {
    isSaving.value = false
  }
}

// 등록 중간에 이탈해야 할 때 쓰는 저장입니다. 지금까지 입력과 올린 사진은 그대로 남고,
// 상품은 '임시 저장 중' 상태로 상품 관리 목록에 남습니다.
async function saveDraft() {
  errorMessage.value = ''
  if (activeStep.value === 1) {
    if (!validateDeviceStep()) return
    isSaving.value = true
    try {
      await persistSaleInfo()
    } catch (error) {
      errorMessage.value = error.message || '임시 저장하지 못했습니다.'
      return
    } finally {
      isSaving.value = false
    }
  }

  if (!currentProductId.value) {
    errorMessage.value = '아직 임시 저장할 내용이 없습니다.'
    return
  }

  resetForm()
  await router.push({ name: 'seller-products' })
}

async function loadHandoverGuide() {
  handoverGuide.value = null
  if (!form.deviceModelId) return
  isLoadingHandoverGuide.value = true
  try {
    handoverGuide.value = await getHandoverGuide(form.deviceModelId)
  } catch {
    const os = models.value.find((item) => String(item.deviceModelId) === String(form.deviceModelId))?.defaultOs
    const fallback = FALLBACK_GUIDES[os] || FALLBACK_GUIDES.ANDROID
    handoverGuide.value = {
      title: fallback.title,
      disclaimer: '모델별 세부 가이드가 아직 준비되지 않아 일반 초기화 안내를 보여드립니다. 실제 절차는 제조사 공식 안내를 함께 확인하세요.',
      steps: fallback.steps.map((step, index) => ({ order: index + 1, ...step, isRequired: true })),
    }
  } finally {
    isLoadingHandoverGuide.value = false
  }
}

// 체크리스트 항목이 많아 전부 채우지 않고 등록하려는 판매자도 있습니다.
// 남은 항목을 알려주되, 확인을 누르면 그대로 다음 단계로 넘어갈 수 있게 합니다.
function goToStep3() {
  errorMessage.value = ''
  const proceed = () => {
    setStep(3)
    loadHandoverGuide()
  }
  const missingRequired = mediaChecklistItems.value.filter(
    (item) => isRequiredItem(item) && mediaOf(item.checklistItemId).length === 0,
  )
  if (missingRequired.length) {
    activeCaptureItemId.value = missingRequired[0].checklistItemId
    openAlert(
      `아직 촬영하지 않은 필수 항목이 ${missingRequired.length}개 있습니다.\n${missingRequired.map((item) => `· ${item.name}`).join('\n')}\n\n지금 넘어가도 나중에 이어서 등록할 수 있지만, 자료가 많을수록 구매자의 신뢰를 얻기 쉽습니다.`,
      proceed,
    )
    return
  }
  proceed()
}

// 개인정보 확인은 체크 몇 번이면 되는 일이고 기기를 넘긴 뒤에는 되돌릴 수 없어 필수로 둡니다.
// 촬영 체크리스트와 달리 건너뛸 수 없습니다.
function goToStep4() {
  errorMessage.value = ''
  const missingConfirm = confirmationChecklistItems.value.filter(
    (item) => isRequiredItem(item) && !confirmState[item.checklistItemId],
  )
  if (missingConfirm.length) {
    openAlert(
      `개인정보 정리 확인이 남아 있습니다.\n${missingConfirm.map((item) => `· ${item.name}`).join('\n')}\n\n기기를 넘기기 전에 반드시 초기화해야 하는 항목이라 건너뛸 수 없습니다.`,
    )
    return
  }
  setStep(4)
}

// 등록을 마치면 별도의 '판매 시작'을 누르지 않아도 바로 판매가 시작되게 합니다.
// 임시 저장은 중간 이탈용이지, 등록을 끝낸 사용자가 한 번 더 눌러야 하는 단계가 아닙니다.
async function finishWizard() {
  const productId = currentProductId.value
  if (!productId) {
    resetForm()
    await router.push({ name: 'seller-products' })
    return
  }

  isSaving.value = true
  try {
    await transitionProductStatus(productId, 'ON_SALE', '등록 완료')
  } catch {
    // 필수 자료가 서버에 아직 반영되지 않았거나 이미 판매 중이면 상태는 그대로 둡니다.
    // 상세 화면에서 현재 상태를 그대로 보여주므로 등록 흐름 자체는 막지 않습니다.
  } finally {
    isSaving.value = false
  }

  resetForm()
  await router.push({ name: 'product-detail', params: { productId } })
}

function readVideoDuration(file) {
  return new Promise((resolve) => {
    const videoEl = document.createElement('video')
    videoEl.preload = 'metadata'
    videoEl.onloadedmetadata = () => {
      URL.revokeObjectURL(videoEl.src)
      resolve(Number.isFinite(videoEl.duration) ? Math.round(videoEl.duration) : null)
    }
    videoEl.onerror = () => resolve(null)
    videoEl.src = URL.createObjectURL(file)
  })
}

async function handleCaptureFile(item, file) {
  if (!item || !file || !currentProductId.value) return
  const itemId = item.checklistItemId
  if (!captureState[itemId]) captureState[itemId] = { media: [], busy: '' }
  if (captureState[itemId].media.length >= MAX_MEDIA_PER_ITEM) {
    alertMessage.value = `‘${item.name}’ 항목은 최대 ${MAX_MEDIA_PER_ITEM}개까지 첨부할 수 있습니다.`
    return
  }
  errorMessage.value = ''
  captureState[itemId].busy = 'optimizing'

  // 업로드 용량과 서버 비용을 줄이기 위해 사진은 Canvas로, 영상은 ffmpeg.wasm으로
  // 브라우저에서 먼저 압축한 뒤 업로드합니다. 압축에 실패하면 원본으로 계속 진행합니다.
  let optimizedFile = file
  try {
    if (item.evidenceType === 'PHOTO') optimizedFile = await compressImage(file)
    else if (item.evidenceType === 'VIDEO') optimizedFile = await compressVideo(file)
  } catch {
    optimizedFile = file
  }

  const previewUrl = URL.createObjectURL(optimizedFile)
  captureState[itemId].busy = 'uploading'

  try {
    const durationSeconds = item.evidenceType === 'VIDEO' ? await readVideoDuration(optimizedFile) : null
    const uploadUrl = await createEvidenceUploadUrl(currentProductId.value, item.checklistItemId, {
      filename: optimizedFile.name,
      contentType: optimizedFile.type || 'application/octet-stream',
      fileSize: optimizedFile.size,
      durationSeconds,
    })
    try {
      // 로컬 환경에는 실제 오브젝트 스토리지가 없어 presignedUrl 업로드가 실패할 수 있습니다.
      // 백엔드 증거 API 구현이 끝나면 이 업로드가 실제로 저장됩니다.
      await fetch(uploadUrl.presignedUrl, {
        method: 'PUT',
        body: optimizedFile,
        headers: uploadUrl.requiredHeaders || {},
      })
    } catch {
      // 스토리지 미구현 환경에서는 무시하고 촬영 완료로만 처리합니다.
    }
    await completeEvidence(currentProductId.value, item.checklistItemId, { uploadId: uploadUrl.uploadId })
  } catch {
    // 증거 업로드 API 자체가 실패해도 화면상 촬영 진행은 막지 않습니다(스텁 백엔드).
  } finally {
    mediaKeySeq += 1
    captureState[itemId].media.push({
      key: mediaKeySeq,
      previewUrl,
      evidenceType: item.evidenceType,
      name: optimizedFile.name,
    })
    captureState[itemId].busy = ''
  }
}

function onCaptureInput(event, item) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) handleCaptureFile(item, file)
}

function openMediaPreview(item, index) {
  mediaPreview.value = { checklistItemId: item.checklistItemId, itemName: item.name, index }
}

function removePreviewedMedia() {
  const target = mediaPreview.value
  if (!target) return
  const media = mediaOf(target.checklistItemId)
  const [removed] = media.splice(target.index, 1)
  if (removed) URL.revokeObjectURL(removed.previewUrl)
  mediaPreview.value = null
}


async function startEdit(productId) {
  errorMessage.value = ''
  try {
    const product = await getMyProduct(productId)
    editingId.value = productId
    draftProductId.value = null
    Object.assign(form, {
      categoryId: product.category?.categoryId || '',
      deviceModelId: product.device?.deviceModelId || '',
      name: product.name || '', description: product.description || '', price: product.price || '',
      color: product.device?.color || '', storageGb: product.device?.storageGb || '',
    })
    if (form.categoryId) models.value = await getDeviceModels({ categoryId: form.categoryId, page: 0, size: 100 })
    if (form.deviceModelId) await loadTemplatePreview()
    checklistItems.value = await getProductChecklist(productId)
    activeCaptureItemId.value = mediaChecklistItems.value[0]?.checklistItemId || null
    clearCaptureState()
    Object.keys(confirmState).forEach((key) => delete confirmState[key])
    activeStep.value = 1
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    errorMessage.value = error.message || '상품 상세를 불러오지 못했습니다.'
  }
}

onMounted(async () => {
  try {
    categories.value = await getDeviceCategories({ activeOnly: true })
  } catch (error) {
    errorMessage.value = error.message || '기기 카테고리를 불러오지 못했습니다.'
  }

  // /seller/products/:productId/edit 로 들어오면 기존 데이터를 불러 수정 모드로 엽니다.
  if (route.params.productId) await startEdit(Number(route.params.productId))
})
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[1040px] px-4 py-8 sm:px-6 lg:px-10 lg:py-12">
      <!-- 흰 배경 패널은 유지하고 테두리만 없애 페이지에 자연스럽게 얹힙니다. -->
      <section class="mb-10 overflow-hidden rounded-lg bg-surface shadow-card">
        <div class="flex flex-col gap-5 border-b border-border px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">
              SELL YOUR DEVICE
            </p>
            <h1 class="mt-1 text-2xl font-bold text-text-main">
              {{ editingId ? '상품 수정' : '상품 등록' }}
            </h1>
            <p class="mt-1 text-sm text-text-sub">
              기기 정보와 검증 체크리스트를 순서대로 완료하면 바로 판매가 시작됩니다. 중간에 나가야 하면 임시저장을 눌러 주세요.
            </p>
          </div>
          <RouterLink
            :to="{ name: 'seller-products' }"
            class="shrink-0 text-sm font-semibold text-primary hover:underline"
          >
            상품 관리로 이동
          </RouterLink>
        </div>

        <ol
          class="grid grid-cols-4 border-b border-border"
          aria-label="상품 등록 단계"
        >
          <li
            v-for="step in WIZARD_STEPS"
            :key="step.number"
            class="flex flex-col items-center justify-center gap-2 px-2 py-4 text-center text-[11px] font-semibold sm:text-xs"
            :class="activeStep >= step.number ? 'bg-accent text-primary' : 'text-text-sub'"
          >
            <span
              class="flex h-6 w-6 items-center justify-center rounded-full text-xs"
              :class="activeStep >= step.number ? 'bg-primary text-white' : 'bg-slate-100 text-text-sub'"
            >{{ step.number }}</span>
            {{ step.label }}
          </li>
        </ol>

        <div class="p-6 lg:p-8">
          <section v-if="activeStep === 1">
            <h2 class="text-lg font-bold text-text-main">
              판매할 기기를 등록해 주세요.
            </h2>
            <p class="mt-1 text-sm text-text-sub">
              선택한 모델에 맞는 검증 체크리스트가 자동으로 연결됩니다. 다음 단계에서 항목별로 사진·영상을 등록하게 됩니다.
            </p>
            <div class="mt-6 grid gap-5 sm:grid-cols-2">
              <label class="text-sm font-semibold text-text-main">카테고리<span class="ml-0.5 text-red-500">*</span>
                <select
                  v-model="form.categoryId"
                  :disabled="Boolean(editingId)"
                  required
                  class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-3 font-normal outline-none focus:border-primary"
                  @change="loadModels"
                >
                  <option value="">카테고리 선택</option><option
                    v-for="item in categories"
                    :key="item.categoryId"
                    :value="item.categoryId"
                  >{{ item.name }}</option>
                </select>
              </label>
              <label class="text-sm font-semibold text-text-main">기기 모델<span class="ml-0.5 text-red-500">*</span>
                <select
                  v-model="form.deviceModelId"
                  :disabled="Boolean(editingId) || !form.categoryId"
                  required
                  class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-3 font-normal outline-none focus:border-primary disabled:opacity-60"
                  @change="loadTemplatePreview"
                >
                  <option value="">기기 모델 선택</option><option
                    v-for="item in models"
                    :key="item.deviceModelId"
                    :value="item.deviceModelId"
                  >{{ item.manufacturerName }} {{ item.modelName }}</option>
                </select>
              </label>
            </div>

            <p
              v-if="isGeneratingChecklist"
              role="status"
              class="mt-5 rounded-lg border border-border bg-bg px-4 py-5 text-center text-sm text-text-sub"
            >
              선택한 모델의 체크리스트와 공식 기능 자료를 확인하고 있습니다…
            </p>

            <div
              v-else-if="checklistGeneration"
              class="mt-5 rounded-lg border border-primary/30 bg-accent/60 p-4"
            >
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 class="text-sm font-bold text-text-main">
                    자동 생성 체크리스트
                  </h3>
                  <p class="mt-1 text-xs text-text-sub">
                    {{ checklistGeneration.manufacturer }} {{ checklistGeneration.modelName }}
                    · {{ checklistGeneration.osFamily }}
                    · {{ templateItems.length }}개 항목
                  </p>
                </div>
                <BaseBadge :variant="checklistGeneration.aiApplied ? 'primary' : 'gray'">
                  {{ checklistGeneration.aiApplied ? 'AI 공식자료 반영' : '기본 정책 적용' }}
                </BaseBadge>
              </div>
              <p
                v-if="!checklistGeneration.aiApplied"
                class="mt-3 rounded-md bg-white/80 px-3 py-2 text-xs leading-5 text-text-sub"
              >
                AI 연결 없이 검증된 Windows·Linux 기본 정책으로 생성했습니다. 상품 등록은 그대로 진행할 수 있습니다.
              </p>
              <p
                v-if="editingId"
                class="mt-3 rounded-md bg-white/80 px-3 py-2 text-xs leading-5 text-text-sub"
              >
                수정 중인 상품에는 최초 등록 시 고정된 체크리스트 스냅샷이 유지됩니다.
              </p>
            </div>

            <ul
              v-if="templateItems.length"
              class="mt-5 grid gap-2 sm:grid-cols-2"
            >
              <li
                v-for="item in templateItems"
                :key="item.itemCode"
                class="flex items-center gap-2 rounded-md border border-border bg-bg px-3 py-2.5 text-xs"
              >
                <svg
                  v-if="item.evidenceType === 'VIDEO'"
                  class="h-4 w-4 shrink-0 text-primary"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  aria-hidden="true"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
                  />
                </svg>
                <BaseBadge :variant="item.evidenceType === 'VIDEO' ? 'primary' : 'gray'">
                  {{ evidenceTypeLabel(item.evidenceType) }}
                </BaseBadge>
                <span class="text-text-main">
                  {{ item.name }}<span
                    v-if="isRequiredItem(item)"
                    class="text-red-500"
                  >*</span>
                </span>
              </li>
            </ul>

            <section
              v-if="checklistGeneration?.aiSuggestions?.length && !editingId"
              class="mt-5 rounded-lg border border-border p-4"
            >
              <div class="flex flex-wrap items-end justify-between gap-2">
                <div>
                  <h3 class="text-sm font-bold text-text-main">
                    AI 공식자료 확인 후보
                  </h3>
                  <p class="mt-1 text-xs leading-5 text-text-sub">
                    실제 기기에 있는 기능만 선택해 주세요. 선택한 기능은 촬영 체크리스트에 추가됩니다.
                  </p>
                </div>
                <span
                  v-if="confirmedFeatures.length"
                  class="text-xs font-semibold text-primary"
                >
                  {{ confirmedFeatures.length }}개 선택
                </span>
              </div>

              <ul class="mt-3 space-y-3">
                <li
                  v-for="suggestion in checklistGeneration.aiSuggestions"
                  :key="suggestion.featureCode"
                  class="rounded-md border border-border bg-bg p-3"
                >
                  <label class="flex cursor-pointer items-start gap-3">
                    <input
                      v-model="confirmedFeatures"
                      type="checkbox"
                      :value="suggestion.featureCode"
                      :disabled="confirmedFeatureLimitReached && !confirmedFeatures.includes(suggestion.featureCode)"
                      class="mt-1 h-4 w-4 rounded border-border"
                    >
                    <span class="min-w-0 flex-1">
                      <span class="flex flex-wrap items-center gap-2">
                        <span class="text-sm font-bold text-text-main">
                          {{ suggestion.featureName || suggestion.featureCode }}
                        </span>
                        <BaseBadge :variant="suggestion.evidenceStatus === 'VERIFIED' ? 'primary' : 'gray'">
                          {{ evidenceStatusLabel(suggestion.evidenceStatus) }}
                        </BaseBadge>
                      </span>
                      <span class="mt-2 block text-xs leading-5 text-text-sub">
                        <strong class="font-bold text-text-main">선정 이유</strong>
                        {{ suggestion.reason }}
                      </span>
                      <span class="mt-1 block text-xs leading-5 text-text-sub">
                        <strong class="font-bold text-text-main">점검 방법</strong>
                        {{ suggestion.checkGuide }}
                      </span>
                      <a
                        v-if="suggestion.sourceUrl"
                        :href="suggestion.sourceUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="mt-1 inline-block text-xs font-semibold text-primary underline underline-offset-2"
                        @click.stop
                      >
                        {{ suggestion.sourceTitle || '공식 자료 보기' }}
                      </a>
                    </span>
                  </label>
                </li>
              </ul>
            </section>

            <p
              v-if="checklistGeneration?.reviewCandidates?.length"
              class="mt-3 rounded-md bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-800"
            >
              <strong class="block font-bold">추가 검토가 필요한 기능</strong>
              AI가 공식 자료에서 찾았지만 아직 서비스에 전용 점검 방법이 정의되지 않아 체크리스트에는 넣지 않았습니다.
              관리자 검토 후보: {{ checklistGeneration.reviewCandidates.join(', ') }}
            </p>

            <div class="mt-6 grid gap-5 sm:grid-cols-2">
              <label class="text-sm font-semibold text-text-main">상품명<span class="ml-0.5 text-red-500">*</span><input
                v-model.trim="form.name"
                required
                maxlength="100"
                placeholder="예: 갤럭시 S24 256GB 자급제"
                class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
              ></label>
              <label class="text-sm font-semibold text-text-main">
                가격<span class="ml-0.5 text-red-500">*</span>
                <div class="relative mt-2">
                  <input
                    :value="priceFormatted"
                    required
                    type="text"
                    inputmode="numeric"
                    autocomplete="off"
                    placeholder="판매 가격"
                    class="w-full rounded-md border border-border px-3 py-3 pr-10 font-normal outline-none focus:border-primary"
                    @input="onPriceInput"
                  >
                  <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm font-normal text-text-sub">원</span>
                </div>
                <!-- 자리를 미리 잡아 두어 문구가 떠도 아래 입력칸이 밀리지 않게 합니다. -->
                <span
                  role="alert"
                  class="mt-1 block min-h-[1.125rem] text-xs font-normal text-red-600"
                >
                  {{ priceRejectionMessage }}
                </span>
              </label>
              <label class="text-sm font-semibold text-text-main">색상<input
                v-model.trim="form.color"
                maxlength="50"
                placeholder="예: 오닉스 블랙"
                class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
              ></label>
              <label class="text-sm font-semibold text-text-main">
                저장 용량
                <select
                  :value="storageSelectValue"
                  aria-label="저장 용량 선택"
                  class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-3 font-normal outline-none focus:border-primary"
                  @change="onStorageSelect"
                >
                  <option value="">용량 선택</option><option
                    v-for="gb in STORAGE_OPTIONS"
                    :key="gb"
                    :value="gb"
                  >{{ storageOptionLabel(gb) }}</option><option value="custom">직접 입력</option>
                </select>
                <input
                  v-if="showStorageInput"
                  v-model="form.storageGb"
                  min="1"
                  type="number"
                  inputmode="numeric"
                  placeholder="용량을 GB 단위 숫자로 입력 (예: 384)"
                  aria-label="저장 용량 직접 입력"
                  class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
                >
              </label>
              <label class="text-sm font-semibold text-text-main sm:col-span-2">
                상품 설명
                <textarea
                  v-model.trim="form.description"
                  maxlength="2000"
                  rows="5"
                  placeholder="외관 상태, 사용 기간, 구성품 등을 알려 주세요."
                  class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
                />
              </label>
            </div>
          </section>

          <section
            v-else-if="activeStep === 2"
            class="grid gap-6 lg:grid-cols-[1fr_1.15fr]"
          >
            <div>
              <h2 class="text-lg font-bold text-text-main">
                검수용 기기 촬영
              </h2>
              <p class="mt-1 text-sm text-text-sub">
                구매자가 믿고 살 수 있도록 {{ mediaChecklistItems.length }}가지 필수 항목의 실물 인증샷을 등록하세요.
              </p>

              <p class="mt-4 rounded-md bg-accent px-4 py-3 text-sm font-semibold text-primary-dark">
                현재 진행률: {{ mediaChecklistItems.length }}개 중 {{ capturedMediaCount }}개 촬영 완료
              </p>

              <!-- 항목이 많으면 화면이 길어져서 6개 정도만 보이고 나머지는 스크롤로 봅니다. -->
              <ul class="mt-4 max-h-[32rem] space-y-3 overflow-y-auto pr-1">
                <li
                  v-for="item in mediaChecklistItems"
                  :key="item.checklistItemId"
                >
                  <button
                    type="button"
                    class="w-full rounded-lg border p-4 text-left transition-colors"
                    :class="activeCaptureItemId === item.checklistItemId
                      ? 'border-primary bg-accent'
                      : 'border-border hover:border-primary'"
                    @click="activeCaptureItemId = item.checklistItemId"
                  >
                    <div class="flex items-center justify-between gap-3">
                      <div>
                        <p class="flex items-center gap-1.5 text-sm font-bold text-text-main">
                          <!-- 영상 항목은 사진과 헷갈리기 쉬워 아이콘으로 먼저 구분해 줍니다. -->
                          <svg
                            v-if="item.evidenceType === 'VIDEO'"
                            class="h-4 w-4 shrink-0 text-primary"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            aria-hidden="true"
                          >
                            <path
                              stroke-linecap="round"
                              stroke-linejoin="round"
                              stroke-width="2"
                              d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
                            />
                          </svg>
                          {{ item.name }}
                          <span
                            v-if="item.evidenceType === 'VIDEO'"
                            class="rounded-pill bg-accent px-1.5 py-0.5 text-[10px] font-bold text-primary"
                          >영상</span>
                        </p>
                        <p class="mt-1 text-xs text-text-sub">
                          {{ guideFor(item) }}
                        </p>
                      </div>
                      <span
                        class="shrink-0 text-xs font-semibold"
                        :class="captureStatusOf(item.checklistItemId) === 'captured' ? 'text-primary' : 'text-text-sub'"
                      >
                        <template v-if="captureStatusOf(item.checklistItemId) === 'captured'">
                          첨부 {{ mediaOf(item.checklistItemId).length }} / {{ MAX_MEDIA_PER_ITEM }}
                        </template>
                        <template v-else-if="captureStatusOf(item.checklistItemId) === 'optimizing'">최적화 중…</template>
                        <template v-else-if="captureStatusOf(item.checklistItemId) === 'uploading'">업로드 중…</template>
                        <template v-else-if="activeCaptureItemId === item.checklistItemId">촬영 대기</template>
                        <template v-else>미촬영</template>
                      </span>
                    </div>
                  </button>
                </li>
                <li
                  v-if="!mediaChecklistItems.length"
                  class="rounded-md bg-bg px-4 py-6 text-center text-sm text-text-sub"
                >
                  촬영이 필요한 항목이 없습니다.
                </li>
              </ul>
            </div>

            <div class="rounded-lg border border-border bg-surface p-6">
              <h2 class="text-base font-bold text-text-main">
                {{ activeCaptureItem ? `${activeCaptureItem.name} 촬영 프리뷰` : '촬영 프리뷰' }}
              </h2>

              <div class="relative mt-4 flex aspect-[4/3] items-center justify-center overflow-hidden rounded-lg border border-border bg-bg">
                <template v-if="activeItemLatestMedia">
                  <video
                    v-if="activeItemLatestMedia.evidenceType === 'VIDEO'"
                    :src="activeItemLatestMedia.previewUrl"
                    class="h-full w-full object-cover"
                    controls
                  />
                  <img
                    v-else
                    :src="activeItemLatestMedia.previewUrl"
                    :alt="activeCaptureItem.name"
                    class="h-full w-full object-cover"
                  >
                </template>
                <template v-else>
                  <span class="absolute left-3 top-3 h-6 w-6 border-l-2 border-t-2 border-primary/40" />
                  <span class="absolute right-3 top-3 h-6 w-6 border-r-2 border-t-2 border-primary/40" />
                  <span class="absolute bottom-3 left-3 h-6 w-6 border-b-2 border-l-2 border-primary/40" />
                  <span class="absolute bottom-3 right-3 h-6 w-6 border-b-2 border-r-2 border-primary/40" />
                  <p class="px-6 text-center text-sm text-text-sub">
                    {{ activeCaptureItem ? '아직 업로드된 파일이 없습니다.' : '왼쪽에서 촬영할 항목을 선택하세요.' }}
                  </p>
                </template>
              </div>

              <div class="mt-4 flex items-center gap-3">
                <label class="min-w-0 flex-1">
                  <input
                    type="file"
                    class="hidden"
                    :accept="captureAccept(activeCaptureItem)"
                    capture="environment"
                    :disabled="!activeCaptureItem || isActiveItemBusy || isActiveItemFull"
                    @change="onCaptureInput($event, activeCaptureItem)"
                  >
                  <span
                    class="block rounded-md bg-primary-gradient px-4 py-3 text-center text-sm font-bold text-white shadow-elevated transition hover:brightness-110"
                    :class="activeCaptureItem && !isActiveItemBusy && !isActiveItemFull
                      ? 'cursor-pointer'
                      : 'cursor-not-allowed opacity-60'"
                  >
                    {{ activeItemStatusLabel }}
                  </span>
                </label>

                <!-- 첨부한 파일은 버튼 옆 썸네일로 보여주고, 누르면 확인 모달을 엽니다. -->
                <ul
                  v-if="activeItemMedia.length"
                  class="flex shrink-0 items-center gap-2"
                >
                  <li
                    v-for="(media, index) in activeItemMedia"
                    :key="media.key"
                  >
                    <button
                      type="button"
                      class="block h-12 w-12 overflow-hidden rounded-md border border-border transition hover:border-primary"
                      :aria-label="`${activeCaptureItem.name} ${index + 1}번째 첨부 파일 확인`"
                      @click="openMediaPreview(activeCaptureItem, index)"
                    >
                      <video
                        v-if="media.evidenceType === 'VIDEO'"
                        :src="media.previewUrl"
                        class="h-full w-full object-cover"
                        muted
                      />
                      <img
                        v-else
                        :src="media.previewUrl"
                        alt=""
                        class="h-full w-full object-cover"
                      >
                    </button>
                  </li>
                </ul>
              </div>
              <p class="mt-2 text-center text-[11px] text-text-sub">
                항목당 최대 {{ MAX_MEDIA_PER_ITEM }}개까지 첨부할 수 있습니다.
                사진은 자동으로 리사이즈, 영상은 브라우저에서 자동 압축된 뒤 업로드됩니다.
              </p>

              <div class="mt-5 rounded-lg bg-bg p-4 text-xs leading-6 text-text-sub">
                <p class="mb-1 font-bold text-text-main">
                  촬영 꿀팁 가이드
                </p>
                <p v-if="guideFor(activeCaptureItem)">
                  • {{ guideFor(activeCaptureItem) }}
                </p>
                <p>• 흔들림을 줄이려면 촬영 순간 잠시 호흡을 멈추고 1초간 유지해 주세요.</p>
              </div>
            </div>
          </section>

          <section
            v-else-if="activeStep === 3"
            class="mx-auto max-w-2xl"
          >
            <h2 class="text-lg font-bold text-text-main">
              개인정보를 정리했는지 확인해 주세요.
            </h2>
            <p class="mt-1 text-sm text-text-sub">
              구매자에게 전달되기 전, 안전을 위해 기기의 계정·개인정보를 반드시 초기화해 주세요.
            </p>

            <p
              v-if="isLoadingHandoverGuide"
              class="mt-6 text-sm text-text-sub"
            >
              초기화 가이드를 불러오는 중...
            </p>
            <div
              v-else-if="handoverGuide"
              class="mt-6 rounded-lg border border-border bg-bg p-5"
            >
              <h3 class="text-sm font-bold text-text-main">
                {{ handoverGuide.title }}
              </h3>
              <ol class="mt-3 space-y-3">
                <li
                  v-for="step in handoverGuide.steps"
                  :key="step.order"
                  class="flex gap-3 text-sm"
                >
                  <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-white">
                    {{ step.order }}
                  </span>
                  <span>
                    <span class="block font-semibold text-text-main">{{ step.title }}</span>
                    <span class="mt-0.5 block text-xs text-text-sub">{{ step.description }}</span>
                  </span>
                </li>
              </ol>
              <p
                v-if="handoverGuide.disclaimer"
                class="mt-4 text-xs leading-5 text-text-sub"
              >
                ⚠ {{ handoverGuide.disclaimer }}
              </p>
            </div>

            <ul class="mt-6 space-y-3">
              <li
                v-for="item in confirmationChecklistItems"
                :key="item.checklistItemId"
                class="rounded-lg border border-border p-4"
              >
                <label class="flex cursor-pointer items-start gap-3">
                  <input
                    v-model="confirmState[item.checklistItemId]"
                    type="checkbox"
                    class="mt-1 h-4 w-4 rounded border-border"
                  >
                  <span>
                    <span class="block text-sm font-bold text-text-main">
                      {{ item.name }}<span
                        v-if="isRequiredItem(item)"
                        class="ml-1 text-red-500"
                      >*</span>
                    </span>
                    <span class="mt-1 block text-xs text-text-sub">{{ guideFor(item) }}</span>
                  </span>
                </label>
              </li>
              <li
                v-if="!confirmationChecklistItems.length"
                class="rounded-md bg-bg px-4 py-6 text-center text-sm text-text-sub"
              >
                확인할 개인정보 항목이 없습니다.
              </li>
            </ul>
          </section>

          <section
            v-else
            class="mx-auto max-w-2xl text-center"
          >
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary-gradient">
              <svg
                class="h-7 w-7 text-white"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M5 13l4 4L19 7"
                />
              </svg>
            </div>
            <h2 class="mt-4 text-lg font-bold text-text-main">
              체크리스트 등록이 완료되었습니다.
            </h2>
            <p class="mt-2 text-sm text-text-sub">
              ‘{{ form.name }}’ 등록이 끝났어요. 완료를 누르면 바로 판매가 시작되고 상품 상세 페이지로 이동합니다.
            </p>

            <dl class="mt-6 grid grid-cols-2 gap-4 rounded-lg border border-border bg-bg p-6 text-left text-sm">
              <div>
                <dt class="text-xs text-text-sub">
                  상품명
                </dt><dd class="mt-1 font-semibold text-text-main">
                  {{ form.name }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  가격
                </dt><dd class="mt-1 font-semibold text-text-main">
                  {{ Number(form.price).toLocaleString() }}원
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  촬영 완료
                </dt><dd class="mt-1 font-semibold text-text-main">
                  {{ capturedMediaCount }} / {{ mediaChecklistItems.length }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  개인정보 확인
                </dt><dd class="mt-1 font-semibold text-text-main">
                  {{ confirmedCount }} / {{ confirmationChecklistItems.length }}
                </dd>
              </div>
            </dl>
          </section>

          <div class="mt-8 flex items-center justify-between border-t border-border pt-5">
            <BaseButton
              v-if="activeStep > 1"
              type="button"
              variant="outline"
              @click="setStep(activeStep - 1)"
            >
              이전 단계로
            </BaseButton>
            <span v-else />

            <!-- 등록 도중 이탈해야 할 때만 쓰는 버튼입니다. 등록을 끝내면 '완료'가 바로 판매를 시작합니다. -->
            <BaseButton
              v-if="activeStep < 4"
              type="button"
              variant="ghost"
              class="ml-auto mr-3 px-3 py-2 text-sm"
              :disabled="isSaving"
              @click="saveDraft"
            >
              임시저장
            </BaseButton>

            <BaseButton
              v-if="activeStep === 1"
              type="button"
              :disabled="isSaving || isGeneratingChecklist"
              @click="goToStep2"
            >
              {{ isGeneratingChecklist ? '체크리스트 생성 중…' : isSaving ? '저장 중…' : '다음 단계' }}
            </BaseButton>
            <BaseButton
              v-else-if="activeStep === 2"
              type="button"
              @click="goToStep3"
            >
              다음 단계로
            </BaseButton>
            <BaseButton
              v-else-if="activeStep === 3"
              type="button"
              @click="goToStep4"
            >
              다음 단계로
            </BaseButton>
            <BaseButton
              v-else
              type="button"
              @click="finishWizard"
            >
              완료
            </BaseButton>
          </div>
        </div>
      </section>

      <p
        v-if="errorMessage"
        role="alert"
        class="mb-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ errorMessage }}
      </p>
      <p
        v-if="notice"
        role="status"
        class="mb-4 rounded-md bg-accent px-4 py-3 text-sm text-primary"
      >
        {{ notice }}
      </p>

      <!-- 체크리스트 진행이 덜 됐을 때 알리는 팝업 -->
      <div
        v-if="alertMessage"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
        role="alertdialog"
        aria-modal="true"
        aria-label="확인 필요"
        @click.self="closeAlert"
      >
        <div class="w-full max-w-sm rounded-lg bg-surface p-6 text-center shadow-elevated">
          <div class="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-accent text-xl font-bold text-primary">
            !
          </div>
          <p class="mt-4 whitespace-pre-line text-left text-sm leading-6 text-text-main">
            {{ alertMessage }}
          </p>
          <div class="mt-5 flex gap-3">
            <BaseButton
              v-if="alertProceed"
              variant="outline"
              class="flex-1"
              @click="closeAlert"
            >
              계속 작성하기
            </BaseButton>
            <BaseButton
              class="flex-1"
              @click="confirmAlert"
            >
              확인
            </BaseButton>
          </div>
        </div>
      </div>

      <!-- 첨부 파일 확인 모달 -->
      <div
        v-if="previewedMedia"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
        role="dialog"
        aria-modal="true"
        aria-label="첨부 파일 확인"
        @click.self="mediaPreview = null"
      >
        <div class="w-full max-w-md rounded-lg bg-surface p-5 shadow-elevated">
          <h2 class="text-base font-bold text-text-main">
            {{ mediaPreview.itemName }}
          </h2>
          <p class="mt-1 text-xs text-text-sub">
            {{ mediaPreview.index + 1 }} / {{ mediaOf(mediaPreview.checklistItemId).length }}번째 첨부 파일
          </p>

          <div class="mt-4 flex aspect-[4/3] items-center justify-center overflow-hidden rounded-lg border border-border bg-bg">
            <video
              v-if="previewedMedia.evidenceType === 'VIDEO'"
              :src="previewedMedia.previewUrl"
              class="h-full w-full object-contain"
              controls
            />
            <img
              v-else
              :src="previewedMedia.previewUrl"
              :alt="`${mediaPreview.itemName} 첨부 파일`"
              class="h-full w-full object-contain"
            >
          </div>
          <p class="mt-2 truncate text-xs text-text-sub">
            {{ previewedMedia.name }}
          </p>

          <div class="mt-5 flex gap-3">
            <BaseButton
              type="button"
              variant="outline"
              class="flex-1"
              @click="removePreviewedMedia"
            >
              삭제
            </BaseButton>
            <BaseButton
              type="button"
              class="flex-1"
              @click="mediaPreview = null"
            >
              확인
            </BaseButton>
          </div>
        </div>
      </div>
    </main>
  </DefaultLayout>
</template>
