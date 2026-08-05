<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseBadge from '../components/BaseBadge.vue'
import {
  completeReinspectionRequest,
  completeEvidence,
  completeProductImage,
  createEvidenceUploadUrl,
  createProductImageUploadUrl,
  createProduct,
  generateChecklist,
  getChecklistTemplate,
  getDeviceCategories,
  getDeviceModels,
  getHandoverGuide,
  getEvidenceHistory,
  getReinspectionRequest,
  getMyProduct,
  getProductChecklist,
  requestDeviceModel,
  getProductDraftProgress,
  getProductImages,
  deleteEvidence,
  deleteProductImage,
  transitionProductStatus,
  updateProduct,
  updateProductDraftProgress,
  updateProductImageOrder,
  uploadToPresignedUrl,
} from '../api/products'
import {
  confirmDiagnosisValue,
  extractOcrText,
  getDiagnosis,
  parseBatteryReport,
  parseDxdiag,
} from '../api/inspection'
import { createInspectionSession, getInspectionSession } from '../api/inspectionSessions'
import { compressImage, compressVideo } from '../utils/mediaOptimize'
import {
  cameraErrorMessage,
  captureFrameToFile,
  hasCameraDevice,
  openCameraStream,
  stopCameraStream,
} from '../utils/camera'
import { MAX_PRICE_DIGITS, formatPriceDigits, toPriceDigits } from '../utils/priceInput'
import { guideContentFor, guideImageFor } from '../utils/checklistGuideImages'
import { CHECKABLE_ITEM_CODES, toUniversalCheckItems } from '../features/deviceCheck/checkableItemCodes'

const WIZARD_STEPS = [
  { number: 1, label: '기기 등록' },
  { number: 2, label: '촬영 및 파일 업로드' },
  { number: 3, label: '개인정보 관리' },
  { number: 4, label: '등록완료' },
]

const CHECKLIST_LOADING_STAGES = [
  '등록된 모델과 기본 체크리스트를 확인하는 중',
  '공식 제조사 자료에서 모델 기능을 조사하는 중',
  '조사 결과와 검증 항목을 정리하는 중',
  '판매용 체크리스트 연결을 마무리하는 중',
]

// 체크리스트 항목 하나에 첨부할 수 있는 사진·영상 개수 상한입니다.
const DEFAULT_MAX_MEDIA_PER_ITEM = 3

// ListingImageUploadService.MAX_IMAGE_BYTES와 같은 값입니다. 서버가 거절하기 전에 안내하려고 둡니다.
const MAX_LISTING_IMAGE_BYTES = 15 * 1024 * 1024

/*
  '다 올렸다'는 통보만 한 번에 하나씩 보냅니다.
  ---------------------------------------------------------------------------
  여러 장을 한꺼번에 올리면 그 통보들이 동시에 도착하는데, 서버는 그때 같은 항목·상품
  줄을 함께 고칩니다. 그러다 DB에서 교착이 나서 한 장만 저장되고 나머지는 실패했습니다
  (운영 로그: Deadlock found when trying to get lock).

  그래서 압축과 전송은 지금처럼 동시에 두고 — 시간이 걸리는 건 이쪽입니다 — 마지막
  통보만 줄을 세웁니다. 통보는 짧아서 줄을 서도 체감이 거의 없고, 여러 장을 한꺼번에
  고르는 사용 방식은 그대로 유지됩니다.

  앞 통보가 실패해도 줄이 끊기지 않게 성공·실패 양쪽에서 이어 붙입니다.
*/
let completionQueue = Promise.resolve()

function queueCompletion(run) {
  const result = completionQueue.then(run, run)
  completionQueue = result.then(() => undefined, () => undefined)
  return result
}

/*
  한 번에 세 개까지만 처리합니다.
  ---------------------------------------------------------------------------
  열 장을 고르면 열 장을 동시에 줄이려 했습니다. 요즘 휴대폰 사진은 한 장이 5MB를
  넘는데, 그걸 한꺼번에 Canvas로 펼치면 메모리를 몇십 MB씩 잡아 기기에 따라 일부가
  조용히 실패합니다. 셋씩 처리하면 앞이 끝나는 대로 다음이 들어가서, 사용자에게는
  똑같이 '한꺼번에 올린' 것으로 보이고 실패는 줄어듭니다.

  개별 실패는 각 작업이 스스로 알리므로 여기서는 삼키고 다음으로 넘어갑니다.
  한 장이 실패해도 나머지가 멈추지 않아야 합니다.
*/
const UPLOAD_CONCURRENCY = 3

async function runWithUploadLimit(items, task) {
  let cursor = 0
  const worker = async () => {
    while (cursor < items.length) {
      const index = cursor
      cursor += 1
      try {
        await task(items[index], index)
      } catch {
        // 개별 실패는 각 작업이 화면에 알립니다.
      }
    }
  }
  const workerCount = Math.min(UPLOAD_CONCURRENCY, items.length)
  await Promise.all(Array.from({ length: workerCount }, worker))
}

// 대표 이미지는 1단계에서 받습니다(pendingThumbnail 참고). 아직 '최소 1장 필수'로는 두지 않았습니다 —
// 이미 이미지 없이 임시저장된 상품들이 있어서, 필수로 바꾸면 그 상품들이 수정 저장조차 못 하게 됩니다.
// 필수로 올릴 때는 기존 초안 처리 방침을 먼저 정하고 validateSaleInfo에 규칙을 붙이세요.
// 화면에서 거래 지역을 받지 않기로 했지만 CreateProductRequest의 tradeRegion에 @NotBlank가 남아 있어
// 값을 비우면 등록이 400으로 실패합니다. 백엔드에서 해당 제약이 풀리면 이 상수와 payload 항목을 함께 지우세요.
const DEFAULT_TRADE_REGION = '협의'

/*
  색상·저장 용량은 등록 화면에서 입력받지 않습니다.
  ---------------------------------------------------------------------------
  값 자체는 form에 남겨 두고 payload로도 계속 보냅니다. 입력칸만 없앴다고
  전송에서까지 빼 버리면, 이미 색상·용량이 들어 있는 상품을 수정할 때 그 값이
  null로 덮여 사라집니다. 화면에서 안 보일 뿐 기존 값은 그대로 지켜집니다.
*/

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
const modelKeyword = ref('')
const isLoadingModels = ref(false)
const modelLoadError = ref('')
let modelRequestId = 0
const isCustomModelInput = ref(false)
// 수정 중인 기존 상품이 직접 입력 모델인지 여부. 이 경우 기능 체크리스트는 서버가 수정을 거부한다.
const editingCustomModel = ref(false)
const isRequestingModel = ref(false)
const modelRequestResult = ref(null)
const customModel = reactive({
  manufacturer: '',
  modelName: '',
  modelCode: '',
  osFamily: 'ANDROID',
})
const isSaving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const reinspectionRequest = ref(null)
const reinspectionRequestKey = computed(
  () => String(route.query.reinspectionRequestKey || '').trim(),
)

/*
  잠깐 나갔다 돌아왔을 때 되돌아갈 단계.
  ---------------------------------------------------------------------------
  실동작 점검 화면이 ?step=2로 돌려보냅니다. 1~3만 받습니다 — 주소를 직접 고쳐
  step=9로 들어와도 없는 단계가 열려 화면이 비지 않게 합니다.
*/
const resumeProductId = computed(() => {
  const productId = Number(route.query.resumeProductId)
  return Number.isInteger(productId) && productId > 0 ? productId : null
})
const returnStep = computed(() => {
  // 가리키는 상품이 있어야 단계를 되살립니다. 수정 화면은 주소에 productId가 있고,
  // 점검을 마치고 등록 화면으로 돌아올 때는 resumeProductId가 붙습니다.
  // 상품이 없는데 3단계를 열면 카테고리·모델도 고르지 않은 채 '판매 준비' 화면이
  // 떠서, 저장할 수 없는 자리에 갇힙니다.
  if (!route.params.productId && !resumeProductId.value) return null
  const step = Number(route.query.step)
  return [1, 2, 3].includes(step) ? step : null
})
const isRegistrationResume = computed(() => Boolean(resumeProductId.value))
const editingId = ref(null)
// 수정 모드로 열린 상품의 현재 상태입니다. 판매 중인 상품을 고칠 때는 임시저장(초안) 진행도를
// 서버에 밀어 넣지 않아야 하므로 상태를 들고 있습니다.
const editingStatus = ref('')
const draftProductId = ref(null)
/*
  처음 그릴 때부터 되돌아갈 단계로 엽니다.
  ---------------------------------------------------------------------------
  ref(1)로 시작하면 상품을 불러오는 동안 1단계가 먼저 그려지고, startEdit이 끝난
  뒤에야 3단계로 바뀝니다. 실동작 점검에서 돌아올 때 1단계가 잠깐 번쩍이던 이유입니다.
  주소는 setup 시점에 이미 알 수 있으므로 처음부터 맞춰 둡니다.
*/
const activeStep = ref(returnStep.value || 1)
const form = reactive({
  categoryId: '', deviceModelId: '', name: '', description: '', price: '',
  color: '', storageGb: '',
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


// 체크리스트 관련: templateItems는 항목 가이드/허용 형식을 보여주기 위한 모델 템플릿,
// checklistItems는 상품 생성 시 고정된 실제 스냅샷(evidence API 호출에 필요한 checklistItemId 포함).
const templateItems = ref([])
const checklistGeneration = ref(null)
const confirmedFeatures = ref([])
const isGeneratingChecklist = ref(false)
const checklistLoadingProgress = ref(0)
const checklistLoadingStageIndex = ref(0)
const checklistLoadingDotCount = ref(1)
let checklistLoadingTimer = null
const checklistItems = ref([])
const windowsInspection = ref(null)
const windowsInspectionBusy = ref(false)
const windowsInspectionError = ref('')
let windowsInspectionTimer = null
const windowsScannerUrl = import.meta.env.VITE_WINDOWS_SCANNER_URL || '/downloads/LimitScanner.exe'
const activeCaptureItemId = ref(null)
const guideModalItem = ref(null)
// captureState[checklistItemId] = { media: [...], busy: '' | 'optimizing' | 'uploading', progress: 0..100 }
const captureState = reactive({})
const confirmState = reactive({})
const DEVICE_CHECK_RESULT = { SUCCESS: 'SUCCESS' }
const draftProgressResults = ref(new Map())
const draftDeviceResults = ref(new Map())
// diagnosisState[checklistItemId] = { status: 'parsing'|'ready'|'error', fields: [...], errorMessage }
// fields의 각 항목은 취합 응답(fieldName/ocrValue/fileParseValue/conflict/confirmedValue)에
// draftValue(입력창 값)와 saving(저장 중 여부)을 더한 것입니다.
const diagnosisState = reactive({})
const DIAGNOSIS_FIELD_LABELS = {
  MODEL_NAME: '모델명',
  CPU: 'CPU',
  RAM: 'RAM',
  GPU: 'GPU',
  GPU_MEMORY: 'GPU 메모리',
  STORAGE_CAPACITY: '저장용량',
  OS_VERSION: 'OS 버전',
  DRIVER_VERSION: '그래픽 드라이버 버전',
  SOUND_DEVICE: '사운드 장치',
  DESIGN_CAPACITY: '배터리 설계 용량',
  FULL_CHARGE_CAPACITY: '배터리 완전충전 용량',
  CYCLE_COUNT: '배터리 충전 사이클',
  BATTERY_MANUFACTURER: '배터리 제조사',
  CAPACITY_RATIO: '배터리 용량 비율(%)',
}

function diagnosisFieldLabel(fieldName) {
  return DIAGNOSIS_FIELD_LABELS[fieldName] || fieldName
}

// 항목의 자동화 종류가 다룰 수 있는 필드 전체 목록입니다. 자동 인식이 실패했거나(값 없음)
// 일부만 인식됐을 때도, 인식 못한 필드까지 빈 입력 칸으로 미리 보여줘서 드롭다운 없이
// 바로 타이핑해 저장할 수 있게 합니다.
const OCR_FIELD_NAMES = ['MODEL_NAME', 'STORAGE_CAPACITY', 'OS_VERSION', 'CPU']
const DXDIAG_FIELD_NAMES = ['RAM', 'GPU', 'GPU_MEMORY', 'DRIVER_VERSION', 'SOUND_DEVICE']
const BATTERY_REPORT_FIELD_NAMES = [
  'DESIGN_CAPACITY', 'FULL_CHARGE_CAPACITY', 'CYCLE_COUNT', 'BATTERY_MANUFACTURER', 'CAPACITY_RATIO',
]
const COMPLETION_FIELD_NAMES_BY_PARSER = {
  DXDIAG: ['RAM', 'GPU'],
  BATTERY_REPORT: ['DESIGN_CAPACITY', 'FULL_CHARGE_CAPACITY', 'CAPACITY_RATIO'],
}

function isDeviceInfoItem(item) {
  return ['LAP-SCR-013', 'SYS-003'].includes(item?.itemCode)
}

function hasAutomaticallyDetectedValue(field) {
  return String(field?.fileParseValue ?? field?.ocrValue ?? '').trim().length > 0
}

function diagnosisCompletionFieldNames(item) {
  if (isDeviceInfoItem(item)) return OCR_FIELD_NAMES
  return COMPLETION_FIELD_NAMES_BY_PARSER[item?.parserType] || []
}

function isAutomatedDiagnosisComplete(item) {
  const requiredFieldNames = diagnosisCompletionFieldNames(item)
  if (!requiredFieldNames.length) return false

  const fields = diagnosisState[item.checklistItemId]?.fields || []
  return requiredFieldNames.every((fieldName) => {
    const field = fields.find((candidate) => candidate.fieldName === fieldName)
    return hasAutomaticallyDetectedValue(field)
  })
}

function isCaptureItemComplete(item) {
  return mediaOf(item.checklistItemId).length > 0
    || isAutomatedDiagnosisComplete(item)
    || (CHECKABLE_ITEM_CODES[item.itemCode] && confirmState[item.checklistItemId])
}

function allDiagnosisFieldNamesFor(item) {
  if (!item) return []
  if (isDeviceInfoItem(item)) return OCR_FIELD_NAMES
  if (item.automationType === 'FILE_PARSE' && item.parserType === 'BATTERY_REPORT') return BATTERY_REPORT_FIELD_NAMES
  if (item.automationType === 'FILE_PARSE' && item.parserType === 'DXDIAG') return DXDIAG_FIELD_NAMES
  return []
}

function stopWindowsInspectionPolling() {
  if (windowsInspectionTimer) window.clearInterval(windowsInspectionTimer)
  windowsInspectionTimer = null
}

async function refreshAutomatedDiagnoses() {
  if (!currentProductId.value) return
  const [checklist, progress] = await Promise.all([
    getProductChecklist(currentProductId.value),
    getProductDraftProgress(currentProductId.value),
  ])
  checklistItems.value = checklist
  draftDeviceResults.value = new Map(Object.entries(progress.deviceResults || {}))
  checklistItems.value
    .filter((item) => CHECKABLE_ITEM_CODES[item.itemCode])
    .forEach((item) => {
      confirmState[item.checklistItemId] = item.status === 'COMPLETED'
    })
  await Promise.allSettled(checklistItems.value
    .filter((item) => allDiagnosisFieldNamesFor(item).length > 0)
    .map((item) => refreshDiagnosis(item, { revealEmptyFields: true })))
}

async function refreshDeviceCheckProgress() {
  if (!currentProductId.value) return
  const progress = await getProductDraftProgress(currentProductId.value)
  draftProgressResults.value = progressResultsMap(progress.results) || new Map()
  draftDeviceResults.value = new Map(Object.entries(progress.deviceResults || {}))
  checklistItems.value
    .filter((item) => CHECKABLE_ITEM_CODES[item.itemCode])
    .forEach((item) => {
      confirmState[item.checklistItemId]
        = draftProgressResults.value.get(item.checklistItemId) === 'SUCCESS'
    })
}

function beginWindowsInspectionPolling(sessionKey) {
  stopWindowsInspectionPolling()
  windowsInspectionTimer = window.setInterval(async () => {
    try {
      const session = await getInspectionSession(sessionKey)
      windowsInspection.value = { ...windowsInspection.value, ...session }
      windowsInspectionError.value = ''
      await refreshDeviceCheckProgress()
      if (session.status === 'COMPLETED') {
        stopWindowsInspectionPolling()
        await refreshAutomatedDiagnoses()
        notice.value = 'Windows 자동 검사 결과가 체크리스트에 반영되었습니다.'
      } else if (['FAILED', 'EXPIRED'].includes(session.status)) {
        stopWindowsInspectionPolling()
        windowsInspectionError.value = session.status === 'EXPIRED'
          ? '연결 코드가 만료됐습니다. 새 코드를 발급해 주세요.'
          : 'Windows 자동 검사를 완료하지 못했습니다.'
      }
    } catch (error) {
      stopWindowsInspectionPolling()
      windowsInspectionError.value = error.message || '자동 검사 상태를 확인하지 못했습니다.'
    }
  }, 2000)
}

async function startWindowsInspection() {
  if (!currentProductId.value || windowsInspectionBusy.value) return
  windowsInspectionBusy.value = true
  windowsInspectionError.value = ''
  stopWindowsInspectionPolling()
  try {
    windowsInspection.value = await createInspectionSession(currentProductId.value)
    beginWindowsInspectionPolling(windowsInspection.value.sessionKey)
  } catch (error) {
    windowsInspectionError.value = error.message || 'Windows 자동 검사를 시작하지 못했습니다.'
  } finally {
    windowsInspectionBusy.value = false
  }
}

const mediaPreview = ref(null)
const mediaDeleteInFlight = ref(false)
const listingImages = ref([])
const listingImageBusy = ref(false)
const listingImageProgress = ref(0)
let listingImageInFlight = 0

/*
  '올리는 중'과 '순서 바꾸는 중'을 따로 셉니다.
  ---------------------------------------------------------------------------
  둘 다 버튼을 잠가야 하지만, 진행 막대는 올릴 때만 뜻이 있습니다. 하나로 묶어
  두었더니 순서를 옮길 때마다 0% 막대가 깜빡였습니다.
*/
const listingImageUploading = ref(false)

// 목록의 사진을 눌렀을 때 크게 띄울 주소. 정사각으로 잘라 보여 주므로 잘린 부분은
// 여기서만 확인됩니다.
const expandedImage = ref('')

// 파일 입력은 label 안에 숨겨 둡니다. label에는 :disabled가 안 걸리므로,
// 못 누르는 상태를 라벨 쪽에도 따로 알려 줘야 버튼이 눌리는 것처럼 보이지 않습니다.
const listingImageAddDisabled = computed(
  () => listingImageBusy.value || listingImages.value.length >= 10,
)

// 대표 이미지는 고르는 즉시 서버에 올립니다. presigned URL이 productId 기준이라 상품이 없으면
// 올릴 수 없어서, 상품이 아직 없을 때는 초안을 먼저 만든 뒤 업로드합니다.
// 초안을 만들 수 없는 상태(카테고리·모델·글제목·가격 미입력)에서만 파일을 임시로 들고 있다가
// '다음 단계'/'임시저장' 시점에 올립니다. 이때는 새로고침하면 선택이 사라지므로 화면에 그렇게 안내합니다.
const pendingThumbnail = ref(null)
const listingThumbnail = computed(
  () => listingImages.value.find((image) => image.imageType === 'THUMBNAIL') || null,
)
const thumbnailPreviewUrl = computed(() => pendingThumbnail.value?.previewUrl
  || listingThumbnail.value?.previewUrl
  || listingThumbnail.value?.imageUrl
  || '')
const registrationMetrics = reactive({
  checklistMs: null,
  imageCompressionMs: null,
  videoCompressionMs: null,
  s3UploadMs: null,
})
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

function progressOf(checklistItemId) {
  return captureState[checklistItemId]?.progress || 0
}

function captureStatusOf(checklistItemId) {
  const busy = busyOf(checklistItemId)
  if (busy) return busy
  if (mediaOf(checklistItemId).length) return 'captured'

  const item = checklistItems.value.find(
    (candidate) => candidate.checklistItemId === checklistItemId,
  )
  return isAutomatedDiagnosisComplete(item) ? 'auto-completed' : 'idle'
}

function maxMediaFor(item) {
  return Number(item?.maxCount) > 0 ? Number(item.maxCount) : DEFAULT_MAX_MEDIA_PER_ITEM
}

function remainingSlots(checklistItemId) {
  const item = checklistItems.value.find((candidate) => candidate.checklistItemId === checklistItemId)
  return maxMediaFor(item) - mediaOf(checklistItemId).length
}
const handoverGuide = ref(null)
const isLoadingHandoverGuide = ref(false)

const currentProductId = computed(() => editingId.value || draftProductId.value)
const selectedModel = computed(
  () => models.value.find((item) => String(item.deviceModelId) === String(form.deviceModelId)) || null,
)
const checklistLoadingStage = computed(
  () => CHECKLIST_LOADING_STAGES[checklistLoadingStageIndex.value],
)
const checklistLoadingDots = computed(() => '.'.repeat(checklistLoadingDotCount.value))
const modelGroups = computed(() => {
  const keyword = modelKeyword.value.trim().toLowerCase()
  const groups = new Map()
  models.value
    .filter((model) => !String(model.modelCode || '').startsWith('ETC-'))
    .filter((model) => !keyword || [model.manufacturerName, model.modelName, model.modelCode]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword)))
    .forEach((model) => {
      const manufacturer = model.manufacturerName || '기타'
      if (!groups.has(manufacturer)) groups.set(manufacturer, [])
      groups.get(manufacturer).push(model)
    })
  return [...groups.entries()].map(([manufacturer, items]) => ({
    manufacturer,
    items: items.slice().sort((a, b) => String(a.modelName || '')
      .localeCompare(String(b.modelName || ''))),
  }))
})
const supportsGeneratedChecklist = computed(
  () => Boolean(selectedModel.value),
)
const selectedAiSuggestions = computed(() => {
  const selectedCodes = new Set(confirmedFeatures.value)
  return (checklistGeneration.value?.aiSuggestions || [])
    .filter((suggestion) => selectedCodes.has(suggestion.featureCode))
})
const previewChecklistItems = computed(
  () => [...templateItems.value, ...selectedAiSuggestions.value],
)
const previewMediaItemCount = computed(
  () => previewChecklistItems.value
    .filter((item) => item.evidenceType !== 'SELLER_CONFIRMATION').length,
)
const previewConfirmationItemCount = computed(
  () => previewChecklistItems.value
    .filter((item) => item.evidenceType === 'SELLER_CONFIRMATION').length,
)
const mediaChecklistItems = computed(
  () => checklistItems.value.filter((item) => item.evidenceType !== 'SELLER_CONFIRMATION'),
)
// SELLER_CONFIRMATION 항목 중 checkableItemCodes.js가 실동작 점검 대상으로 지정한 itemCode는
// DeviceCheckPage에서 실제로 눌러보고 받은 결과만 신뢰해야 합니다. 여기 체크박스로 노출하면
// 판매자가 점검 없이 그냥 체크해서 SUCCESS로 덮어버릴 수 있어 개인정보 확인 항목과 분리합니다.
const privacyChecklistItems = computed(
  () => checklistItems.value.filter(
    (item) => item.evidenceType === 'SELLER_CONFIRMATION' && !CHECKABLE_ITEM_CODES[item.itemCode],
  ),
)
const deviceCheckConfirmationItems = computed(
  () => toUniversalCheckItems(checklistItems.value),
)
const capturedMediaCount = computed(
  () => mediaChecklistItems.value.filter((item) => isCaptureItemComplete(item)).length,
)
const confirmedCount = computed(
  () => privacyChecklistItems.value.filter((item) => confirmState[item.checklistItemId]).length,
)
const deviceCheckConfirmedCount = computed(
  () => deviceCheckConfirmationItems.value.filter(
    (item) => deviceCheckStatus(item) === 'COMPLETED',
  ).length,
)
// draft 편집 중에는 draftProgressResults로 FAILED와 미점검을 구분할 수 있지만, 이미 판매 중인
// 상품을 고칠 때는 서버가 COMPLETED/PENDING만 내려줘 구분할 수 없어 미점검으로만 표시합니다.
function deviceCheckStatus(item) {
  const webResult = draftDeviceResults.value.get(item.testType)
  if (webResult === 'SUCCESS') return 'COMPLETED'
  if (webResult === 'FAILED') return 'FAILED'
  if (confirmState[item.checklistItemId]) return 'COMPLETED'
  if (draftProgressResults.value.get(item.checklistItemId) === 'FAILED') return 'FAILED'
  return 'PENDING'
}
function deviceCheckStatusLabel(item) {
  const status = deviceCheckStatus(item)
  if (status === 'FAILED') return '재점검 필요'
  if (status !== 'COMPLETED') return '직접 점검 가능'
  return draftDeviceResults.value.get(item.testType) === 'SUCCESS'
    ? '웹 점검 완료'
    : '자동 입력 완료'
}
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
const isActiveItemFull = computed(
  () => activeItemMedia.value.length >= maxMediaFor(activeCaptureItem.value),
)
const activeItemLatestMedia = computed(() => activeItemMedia.value[activeItemMedia.value.length - 1] || null)
const activeItemStatusLabel = computed(() => {
  const busy = activeCaptureItem.value && busyOf(activeCaptureItem.value.checklistItemId)
  // 몇 %인지는 바로 아래 막대가 말해 주므로, 버튼에는 무엇을 하는 중인지만 적습니다.
  if (busy === 'queued') return '차례 기다리는 중…'
  if (busy === 'optimizing') return '압축 중…'
  if (busy === 'uploading') return '업로드 중…'
  if (isActiveItemFull.value) return `최대 ${maxMediaFor(activeCaptureItem.value)}개까지 첨부했습니다`
  // 촬영을 못 하는 항목(영상이거나 카메라가 없는 기기)에서 '촬영'을 말하면 안 됩니다.
  // 할 수 없는 일을 안내하면 사용자는 버튼을 찾아 헤매게 됩니다.
  if (!canShootActiveItem.value) {
    return activeItemMedia.value.length ? '파일 추가하기' : '파일 업로드'
  }
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

function openGuideModal(item) {
  guideModalItem.value = item
}

function closeGuideModal() {
  guideModalItem.value = null
}

// 카테고리(상위 기기 분류) 이름입니다. checklistGuideImages.js의 카테고리 키와
// 정확히 같은 문자열이어야 필수 항목 안내가 매칭됩니다.
const selectedCategoryName = computed(
  () => categories.value.find((item) => String(item.categoryId) === String(form.categoryId))?.name || null
)

// 모달 전용 문구입니다. isRequired가 true고 checklistGuideImages.js에 이 카테고리·항목이
// 등록돼 있을 때만 이 값을 쓰고, 그 외에는 서버가 내려준 guide 문구로 채웁니다.
function guidePurposeFor(item) {
  return guideContentFor(item, selectedCategoryName.value)?.purpose || ''
}

function guideStepsFor(item) {
  return guideContentFor(item, selectedCategoryName.value)?.guide || guideFor(item)
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

function isReinspectionItem(item) {
  return reinspectionRequest.value?.items?.some(
    (requestedItem) => Number(requestedItem.checklistItemId) === Number(item.checklistItemId),
  ) || false
}

// 파일 선택창을 서버가 실제로 받는 형식으로 좁힙니다(EvidenceUploadService의 허용 목록과 같음).
// */*로 두면 거절될 파일을 고르게 되고, 사용자는 MEDIA_UPLOAD_INVALID만 보고 이유를 알 수 없습니다.
const DIAGNOSTIC_ACCEPT = [
  'image/jpeg', 'image/png', 'image/webp',
  'text/plain', 'text/html', 'text/xml', 'application/xml',
  '.txt', '.html', '.htm', '.xml',
].join(',')

function captureAccept(item) {
  if (!item) return ''
  if (item.evidenceType === 'VIDEO') return 'video/*'
  if (item.evidenceType === 'PHOTO') return 'image/*'
  // 진단 자료는 파일(txt·html·xml)과 사진 모두 받습니다. 진단 앱이 결과를 파일로 내보내지
  // 못하면 화면을 찍어 올리는 방법밖에 없습니다.
  if (item.evidenceType === 'DIAGNOSTIC_FILE') return DIAGNOSTIC_ACCEPT
  return '*/*'
}

function clearCaptureState() {
  Object.keys(captureState).forEach((key) => {
    mediaOf(key).forEach((media) => URL.revokeObjectURL(media.previewUrl))
    delete captureState[key]
  })
  Object.keys(diagnosisState).forEach((key) => delete diagnosisState[key])
  mediaPreview.value = null
}

function scrollToTop() {
  window.scrollTo(0, 0)
}

async function measureRegistrationPhase(name, task) {
  const startedAt = performance.now()
  try {
    return await task()
  } finally {
    registrationMetrics[name] = Math.round(performance.now() - startedAt)
  }
}

function stopChecklistLoading() {
  if (checklistLoadingTimer) window.clearInterval(checklistLoadingTimer)
  checklistLoadingTimer = null
}

function startChecklistLoading() {
  stopChecklistLoading()
  checklistLoadingProgress.value = 8
  checklistLoadingStageIndex.value = 0
  checklistLoadingDotCount.value = 1
  checklistLoadingTimer = window.setInterval(() => {
    checklistLoadingDotCount.value = (checklistLoadingDotCount.value % 3) + 1
    checklistLoadingProgress.value = Math.min(92, checklistLoadingProgress.value + 3)
    if (checklistLoadingProgress.value >= 78) checklistLoadingStageIndex.value = 3
    else if (checklistLoadingProgress.value >= 55) checklistLoadingStageIndex.value = 2
    else if (checklistLoadingProgress.value >= 28) checklistLoadingStageIndex.value = 1
  }, 450)
}

/*
  단계가 바뀌면 위자드 상단(단계 표시줄)부터 보이도록 항상 스크롤을 올립니다.

  3단계로 들어오는 길이 여럿이라(다음 단계로 / 4단계에서 이전 단계로 / 실동작 점검
  복귀) 초기화 가이드는 여기 한곳에서 받아 옵니다. 예전에는 goToStep3에만 있어,
  다른 길로 들어오면 '판매 준비' 안내가 통째로 비었습니다.
*/
function setStep(step) {
  activeStep.value = step
  if (step === 3) loadHandoverGuide()
  persistDraftProgress()
  scrollToTop()
}

async function persistDraftProgress() {
  if (!currentProductId.value || reinspectionRequestKey.value) return
  // 이미 판매 중·숨김인 상품은 초안 진행도를 갖지 않습니다. 서버가 409로 거절하므로 호출하지 않습니다.
  if (editingStatus.value && editingStatus.value !== 'DRAFT') return
  try {
    // 불변 조건: 베이스는 항상 draftProgressResults 전체(개인정보 + 실동작 점검 결과 모두)에서
    // 시작하고, 아래 루프는 privacyChecklistItems(개인정보 확인 항목)만 set/delete 합니다.
    // deviceCheckConfirmationItems는 이 함수가 절대 건드리지 않아야 DeviceCheckPage에서 받은
    // FAILED/SUCCESS가 여기서 조용히 덮이거나 사라지지 않습니다.
    const resultsByItemId = new Map(draftProgressResults.value)
    privacyChecklistItems.value.forEach((item) => {
      if (confirmState[item.checklistItemId]) {
        resultsByItemId.set(item.checklistItemId, DEVICE_CHECK_RESULT.SUCCESS)
      } else if (resultsByItemId.get(item.checklistItemId) === DEVICE_CHECK_RESULT.SUCCESS) {
        resultsByItemId.delete(item.checklistItemId)
      }
    })
    const response = await updateProductDraftProgress(currentProductId.value, {
      step: activeStep.value,
      results: [...resultsByItemId.entries()].map(([checklistItemId, result]) => ({
        checklistItemId,
        result,
      })),
    })
    draftProgressResults.value = progressResultsMap(response?.results) || resultsByItemId
  } catch {
    /*
      자동 저장은 단계를 옮길 때 화면이 스스로 부르는 것이라, 실패를 알리면 사용자는
      누르지도 않은 '임시저장'이 실패했다는 문구를 보게 됩니다. 특히 상품이 이미 없어져
      404가 오는 경우가 그렇습니다. 조용히 넘기고, 사용자가 직접 누르는 임시저장에서만
      결과를 알립니다.
    */
  }
}

function progressResultsMap(results) {
  if (!results) return null
  return new Map(Object.entries(results).map(([itemId, result]) => [Number(itemId), result]))
}

function resetForm() {
  editingId.value = null
  editingStatus.value = ''
  editingCustomModel.value = false
  draftProductId.value = null
  if (pendingThumbnail.value) URL.revokeObjectURL(pendingThumbnail.value.previewUrl)
  pendingThumbnail.value = null
  activeStep.value = 1
  Object.assign(form, {
    categoryId: '', deviceModelId: '', name: '', description: '', price: '',
    color: '', storageGb: '',
  })
  models.value = []
  modelKeyword.value = ''
  modelLoadError.value = ''
  isCustomModelInput.value = false
  modelRequestResult.value = null
  Object.assign(customModel, {
    manufacturer: '',
    modelName: '',
    modelCode: '',
    osFamily: 'ANDROID',
  })
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  isGeneratingChecklist.value = false
  checklistItems.value = []
  draftProgressResults.value = new Map()
  draftDeviceResults.value = new Map()
  activeCaptureItemId.value = null
  handoverGuide.value = null
  priceRejection.value = ''
  stopChecklistLoading()
  clearCaptureState()
  Object.keys(confirmState).forEach((key) => delete confirmState[key])
}

async function loadModels() {
  const requestId = ++modelRequestId
  form.deviceModelId = ''
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  isCustomModelInput.value = false
  modelRequestResult.value = null
  models.value = []
  modelKeyword.value = ''
  modelLoadError.value = ''
  if (!form.categoryId) {
    isLoadingModels.value = false
    return
  }
  isLoadingModels.value = true
  try {
    const response = await getDeviceModels({ categoryId: form.categoryId, page: 0, size: 100 })
    if (requestId !== modelRequestId) return
    models.value = Array.isArray(response) ? response : []
  } catch {
    if (requestId !== modelRequestId) return
    modelLoadError.value = '모델 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    if (requestId === modelRequestId) isLoadingModels.value = false
  }
}

async function searchModels() {
  if (!form.categoryId) return
  const requestId = ++modelRequestId
  isLoadingModels.value = true
  modelLoadError.value = ''
  try {
    const response = await getDeviceModels({
      categoryId: form.categoryId,
      keyword: modelKeyword.value.trim() || undefined,
      page: 0,
      size: 100,
    })
    if (requestId !== modelRequestId) return
    models.value = Array.isArray(response) ? response : []
  } catch {
    if (requestId !== modelRequestId) return
    modelLoadError.value = '모델 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    if (requestId === modelRequestId) isLoadingModels.value = false
  }
}

function onCustomCategoryChange() {
  modelRequestId += 1
  form.deviceModelId = ''
  models.value = []
  modelKeyword.value = ''
  modelLoadError.value = ''
  isLoadingModels.value = false
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  modelRequestResult.value = null
}

// 카테고리·기기 모델을 바꾸면 체크리스트가 그 모델 기준으로 새로 만들어집니다. 이미 올린
// 촬영 자료는 사라진 항목에 묶여 있어 함께 없어지므로, 되돌릴 수 없는 변경임을 먼저 알립니다.
function hasCapturedEvidence() {
  return mediaChecklistItems.value.some((item) => mediaOf(item.checklistItemId).length > 0)
}

function confirmDeviceChange() {
  if (!hasCapturedEvidence()) return true
  return window.confirm(
    '카테고리·기기 모델을 수정하게 되면 새로운 체크리스트가 만들어져 기존의 정보들이 사라지게 됩니다.'
    + '\n\n정말 다시 수정하시겠습니까?',
  )
}

// 되돌릴 값을 미리 들고 있어야 '아니오'를 눌렀을 때 선택을 되돌릴 수 있습니다.
// select는 change가 나기 전에 v-model이 이미 바뀌어 있기 때문입니다.
let lastCategoryId = ''
let lastDeviceModelId = ''

watch(() => form.categoryId, (value, previous) => { lastCategoryId = previous ?? value }, { flush: 'sync' })
watch(() => form.deviceModelId, (value, previous) => { lastDeviceModelId = previous ?? value }, { flush: 'sync' })

function onCategoryChange() {
  if (!confirmDeviceChange()) {
    form.categoryId = lastCategoryId
    return
  }
  if (isCustomModelInput.value) {
    onCustomCategoryChange()
    return
  }
  loadModels()
}

function onDeviceModelChange() {
  if (!confirmDeviceChange()) {
    form.deviceModelId = lastDeviceModelId
    return
  }
  loadTemplatePreview()
}

function toggleCustomModelInput() {
  isCustomModelInput.value = !isCustomModelInput.value
  modelRequestResult.value = null
  if (isCustomModelInput.value) {
    form.deviceModelId = ''
    checklistGeneration.value = null
    templateItems.value = []
  } else {
    loadModels()
  }
}

async function submitModelRequest() {
  if (!form.categoryId || !customModel.manufacturer || !customModel.modelName) {
    errorMessage.value = '카테고리, 제조사, 모델명을 입력해 주세요.'
    return
  }
  isRequestingModel.value = true
  errorMessage.value = ''
  try {
    const created = await requestDeviceModel({
      categoryId: Number(form.categoryId),
      manufacturer: customModel.manufacturer,
      modelName: customModel.modelName,
      modelCode: customModel.modelCode || null,
      osFamily: customModel.osFamily,
    })
    modelRequestResult.value = created
    const modelId = created.resolvedModelId || created.resolvedCategoryId
    models.value = [{
      deviceModelId: modelId,
      manufacturerName: created.manufacturer,
      categoryId: created.categoryId,
      modelCode: created.modelCode,
      modelName: created.modelName,
      defaultOs: created.osFamily,
      isActive: true,
    }]
    form.deviceModelId = modelId
    isCustomModelInput.value = false
    notice.value = '새 모델을 바로 사용할 수 있습니다. AI 추가 항목을 확인해 주세요.'
    await loadTemplatePreview()
  } catch (error) {
    errorMessage.value = error.message || '모델 검토 요청을 등록하지 못했습니다.'
  } finally {
    isRequestingModel.value = false
  }
}

async function loadTemplatePreview() {
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  if (!form.deviceModelId) return
  isGeneratingChecklist.value = true
  startChecklistLoading()
  errorMessage.value = ''
  try {
    if (supportsGeneratedChecklist.value) {
      const generated = await measureRegistrationPhase('checklistMs', () => generateChecklist({
        deviceModelId: Number(form.deviceModelId),
        confirmedFeatures: [],
      }))
      checklistGeneration.value = generated
      templateItems.value = generated.items || []
    } else {
      const template = await getChecklistTemplate(form.deviceModelId)
      templateItems.value = template.items || []
    }
  } catch (error) {
    errorMessage.value = error.message || '체크리스트를 불러오지 못했습니다.'
  } finally {
    checklistLoadingProgress.value = 100
    stopChecklistLoading()
    isGeneratingChecklist.value = false
  }
}

// 기기 등록(1단계)이 실제 필수 구간입니다. 여기서 빠진 값이 있으면 다음 단계로 넘기지 않습니다.
// 반대로 2단계 촬영 체크리스트는 필수가 아니어서 건너뛸 수 있습니다.
// requireStorage: '다음 단계'는 저장 용량까지 요구하고, 중간 이탈용 '임시저장'은 요구하지 않습니다.
// TODO(필수 항목): 저장 용량·대표 이미지는 아직 선택 입력입니다. 필수로 바꾸려면 이미지 없이
// 임시저장된 기존 상품의 처리 방침을 먼저 정하세요(위 대표 이미지 주석 참고).
function validateSaleInfo() {
  if (!form.name || form.price === '') {
    errorMessage.value = '글제목과 가격을 입력해 주세요.'
    return false
  }
  if (!Number.isFinite(Number(form.price)) || Number(form.price) < 1) {
    errorMessage.value = '가격은 1원 이상 입력해 주세요.'
    return false
  }
  if (form.storageGb !== '' && (
    !Number.isInteger(Number(form.storageGb))
    || Number(form.storageGb) < 1
    || Number(form.storageGb) > 16384
  )) {
    errorMessage.value = '저장 용량은 1 ~ 16,384GB 범위의 정수로 입력해 주세요.'
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
    // 직접 입력 모델은 서버가 기능 체크리스트 수정 자체를 거부하므로 아예 보내지 않습니다.
    await updateProduct(productId, editingCustomModel.value
      ? payload
      : { ...payload, confirmedFeatures: confirmedFeatures.value })
  } else {
    const created = await createProduct({
      ...payload,
      categoryId: Number(form.categoryId),
      deviceModelId: Number(form.deviceModelId),
      confirmedFeatures: confirmedFeatures.value,
    })
    productId = created.productId
    draftProductId.value = productId
  }
  // 상품이 생긴 다음이라야 대표 이미지 presigned URL을 받을 수 있습니다.
  await flushPendingThumbnail()
  checklistItems.value = await getProductChecklist(productId)
  await Promise.allSettled(checklistItems.value
    .filter((item) => allDiagnosisFieldNamesFor(item).length > 0)
    .map((item) => refreshDiagnosis(item)))
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
  // 가이드 조회는 setStep이 맡습니다.
  const proceed = () => setStep(3)
  const missingRequired = mediaChecklistItems.value.filter(
    (item) => isRequiredItem(item) && !isCaptureItemComplete(item),
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

// 개인정보 확인·실동작 점검은 기기를 넘긴 뒤에는 되돌릴 수 없어 필수로 둡니다.
// 촬영 체크리스트와 달리 건너뛸 수 없습니다.
function goToStep4() {
  errorMessage.value = ''
  const missingPrivacy = privacyChecklistItems.value.filter(
    (item) => isRequiredItem(item) && !confirmState[item.checklistItemId],
  )
  const missingDeviceCheck = deviceCheckConfirmationItems.value.filter(
    (item) => isRequiredItem(item) && !confirmState[item.checklistItemId],
  )
  if (missingPrivacy.length || missingDeviceCheck.length) {
    // 개인정보 확인과 실동작 점검은 사용자가 가야 할 곳이 다릅니다(체크박스 vs 점검 페이지)
    // — 문구를 나눠서 어디로 가야 하는지 바로 알 수 있게 합니다.
    const sections = []
    if (missingPrivacy.length) {
      sections.push(
        `[개인정보 정리 확인]\n${missingPrivacy.map((item) => `· ${item.name}`).join('\n')}\n개인 정보 보호를 위해 반드시 초기화를 진행해 주세요.`,
      )
    }
    if (missingDeviceCheck.length) {
      sections.push(
        `[실동작 점검]\n${missingDeviceCheck.map((item) => `· ${item.name}`).join('\n')}\n2단계의 "직접 점검하기" 버튼을 눌러 확인해 주세요.`,
      )
    }
    openAlert(sections.join('\n\n'))
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
    if (reinspectionRequestKey.value) {
      await completeReinspectionRequest(reinspectionRequestKey.value)
    } else if (!editingStatus.value || editingStatus.value === 'DRAFT') {
      await transitionProductStatus(productId, 'ON_SALE', '등록 완료')
    }
    // 이미 판매 중·숨김인 상품을 고친 경우에는 상태를 그대로 둡니다. 숨겨 둔 상품이 수정만으로
    // 다시 공개되면 판매자가 의도하지 않은 노출이 생기기 때문입니다.
  } catch (error) {
    if (reinspectionRequestKey.value) {
      errorMessage.value = error.message || '재검수 완료 처리에 실패했습니다.'
      return
    }
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

// 항목 하나의 취합된 진단값을 다시 조회해 draftValue(입력창 초기값)와 saving 상태를 붙여 저장하고,
// 인식되지 못한 필드도 빈 입력 칸(placeholder row)으로 함께 채워 바로 타이핑해 저장할 수 있게 합니다.
async function refreshDiagnosis(item, { revealEmptyFields = false } = {}) {
  const result = await getDiagnosis(item.checklistItemId)
  const detectedFields = (result.fields || []).map((field) => ({
    ...field,
    draftValue: field.confirmedValue ?? field.fileParseValue ?? field.ocrValue ?? '',
    saving: false,
    justSaved: false,
  }))
  const detectedFieldNames = new Set(detectedFields.map((field) => field.fieldName))
  const shouldRevealEmptyFields = isDeviceInfoItem(item) || revealEmptyFields || detectedFields.length > 0
  const placeholderFields = (shouldRevealEmptyFields ? allDiagnosisFieldNamesFor(item) : [])
    .filter((fieldName) => !detectedFieldNames.has(fieldName))
    .map((fieldName) => ({
      fieldName,
      ocrValue: null,
      fileParseValue: null,
      conflict: false,
      confirmedValue: null,
      draftValue: '',
      saving: false,
      justSaved: false,
    }))
  diagnosisState[item.checklistItemId] = {
    status: 'ready',
    fields: [...detectedFields, ...placeholderFields],
    errorMessage: '',
  }
}

// 업로드가 끝난 증거를 자동화 유형에 맞춰 OCR/DxDiag/배터리 리포트 파싱 API로 넘기고,
// 결과(성공하든 실패하든)를 취합 조회로 다시 불러와 진단 패널에 보여줍니다.
async function runDiagnosisAutomation(item, evidenceId) {
  const automationType = item.automationType
  if (!automationType || automationType === 'NONE') return

  diagnosisState[item.checklistItemId] = {
    status: 'parsing',
    fields: diagnosisState[item.checklistItemId]?.fields || [],
    errorMessage: '',
  }

  let parseErrorMessage = ''
  try {
    if (automationType === 'OCR') {
      await extractOcrText(evidenceId)
    } else if (item.parserType === 'BATTERY_REPORT') {
      await parseBatteryReport(evidenceId)
    } else if (item.parserType === 'DXDIAG') {
      await parseDxdiag(evidenceId)
    }
  } catch (error) {
    parseErrorMessage = error.message || '자동 인식에 실패했습니다. 값을 직접 입력해 주세요.'
  }

  try {
    await refreshDiagnosis(item, { revealEmptyFields: true })
  } catch {
    // 취합 조회 실패는 아래 에러 메시지로 안내합니다.
  }

  if (parseErrorMessage) {
    diagnosisState[item.checklistItemId] = {
      ...diagnosisState[item.checklistItemId],
      status: 'error',
      errorMessage: parseErrorMessage,
    }
  }
}

async function saveDiagnosisValue(checklistItemId, field) {
  const confirmedValue = field.draftValue?.trim()
  if (!confirmedValue) return
  field.saving = true
  field.justSaved = false
  clearTimeout(field.justSavedTimer)
  try {
    const result = await confirmDiagnosisValue(checklistItemId, {
      fieldName: field.fieldName,
      confirmedValue,
    })
    field.confirmedValue = result.confirmedValue
    field.draftValue = result.confirmedValue
    field.justSaved = true
    field.justSavedTimer = setTimeout(() => { field.justSaved = false }, 2500)
  } catch (error) {
    errorMessage.value = error.message || '진단값을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    field.saving = false
  }
}

/*
  진행 상태 한 줄로 묶기.
  ---------------------------------------------------------------------------
  영상은 줄이는 데 몇십 초가 걸립니다. 예전에는 '최적화 중…'만 떠서 멈춘 것인지
  일하는 중인지 알 수 없었습니다. 이제 세 단계를 %로 보여 줍니다.
    대기 중  - 앞 영상을 줄이는 중이라 자기 차례를 기다림
    압축 N%  - ffmpeg이 알려 주는 실제 진행률
    업로드 N% - S3로 올리는 진행률
*/
const CAPTURE_PHASE_LABELS = {
  queued: '대기 중',
  optimizing: '압축',
  uploading: '업로드',
}

function captureProgressLabel(checklistItemId) {
  const busy = busyOf(checklistItemId)
  const label = CAPTURE_PHASE_LABELS[busy]
  if (!label) return ''
  if (busy === 'queued') return label
  return `${label} ${progressOf(checklistItemId)}%`
}

async function handleCaptureFile(item, file) {
  if (!item || !file || !currentProductId.value) return
  const itemId = item.checklistItemId
  if (!captureState[itemId]) captureState[itemId] = { media: [], busy: '', progress: 0 }
  if (captureState[itemId].media.length >= maxMediaFor(item)) {
    alertMessage.value = `‘${item.name}’ 항목은 최대 ${maxMediaFor(item)}개까지 첨부할 수 있습니다.`
    return
  }
  errorMessage.value = ''

  /*
    영상 길이는 압축하기 전에 원본에서 읽습니다.
    ---------------------------------------------------------------------------
    서버는 영상에 길이를 반드시 요구하는데, 압축 결과물에서 길이를 못 읽는 경우가 있어
    null이 되면 422로 거절당했습니다. 한참 압축한 끝에 이유 없이 실패하던 원인입니다.
    길이는 압축해도 그대로이므로 원본에서 읽는 편이 정확합니다.

    제한을 넘긴 영상은 여기서 바로 알립니다. 압축부터 시작하면 수십 초를 기다린 뒤에야
    거절당합니다.
  */
  let durationSeconds = null
  if (item.evidenceType === 'VIDEO') {
    durationSeconds = await readVideoDuration(file)
    const maxDuration = Number(item.maxDurationSec) || null
    if (maxDuration && durationSeconds && durationSeconds > maxDuration) {
      openAlert(`‘${item.name}’ 항목은 ${maxDuration}초 이내 영상만 올릴 수 있습니다.`)
      return
    }
  }

  // 영상은 한 번에 하나씩 줄이므로, 차례가 오기 전에는 '대기 중'입니다.
  captureState[itemId].busy = item.evidenceType === 'VIDEO' ? 'queued' : 'optimizing'
  captureState[itemId].progress = 0

  // 업로드 용량과 서버 비용을 줄이기 위해 사진은 Canvas로, 영상은 ffmpeg.wasm으로
  // 브라우저에서 먼저 압축한 뒤 업로드합니다. 압축에 실패하면 원본으로 계속 진행합니다.
  let optimizedFile = file
  try {
    if (item.evidenceType === 'VIDEO') {
      optimizedFile = await measureRegistrationPhase('videoCompressionMs', () => compressVideo(file, {
        onStart: () => {
          captureState[itemId].busy = 'optimizing'
          captureState[itemId].progress = 0
        },
        onProgress: (percent) => { captureState[itemId].progress = percent },
      }))
    } else {
      // 항목 종류가 아니라 파일 종류로 판단합니다. 진단 자료 항목에도 사진이 올라올 수 있고,
      // compressImage는 이미지가 아닌 파일(txt·html)은 그대로 돌려주므로 안전합니다.
      optimizedFile = await measureRegistrationPhase('imageCompressionMs', () => compressImage(file))
    }
  } catch {
    optimizedFile = file
  }

  const previewUrl = URL.createObjectURL(optimizedFile)
  captureState[itemId].busy = 'uploading'
  captureState[itemId].progress = 0

  try {
    const uploadUrl = await createEvidenceUploadUrl(currentProductId.value, item.checklistItemId, {
      filename: optimizedFile.name,
      contentType: optimizedFile.type || 'application/octet-stream',
      fileSize: optimizedFile.size,
      durationSeconds,
    })
    await measureRegistrationPhase('s3UploadMs', () => uploadToPresignedUrl(
      uploadUrl.presignedUrl,
      optimizedFile,
      uploadUrl.requiredHeaders || {},
      (progress) => { captureState[itemId].progress = progress },
    ))
    const completed = await queueCompletion(() => completeEvidence(
      currentProductId.value,
      item.checklistItemId,
      { uploadId: uploadUrl.uploadId },
    ))
    mediaKeySeq += 1
    captureState[itemId].media.push({
      key: completed.evidenceId || mediaKeySeq,
      previewUrl,
      mediaUrl: completed.mediaUrl,
      evidenceType: item.evidenceType,
      name: optimizedFile.name,
      attemptNo: completed.attemptNo,
    })
    // 업로드 자체는 끝났으니 버튼은 바로 풀어 주고, 자동 인식은 진단 패널에서 별도로 진행 상태를 보여줍니다.
    if (completed.evidenceId) runDiagnosisAutomation(item, completed.evidenceId)
  } catch {
    URL.revokeObjectURL(previewUrl)
    errorMessage.value = `‘${item.name}’ 자료를 업로드하지 못했습니다. 잠시 후 다시 시도해 주세요.`
  } finally {
    captureState[itemId].busy = ''
    captureState[itemId].progress = 0
  }
}

async function handleListingImage(file, displayOrder = listingImages.value.length) {
  if (!file || !currentProductId.value || listingImages.value.length >= 10) return
  listingImageInFlight += 1
  listingImageBusy.value = true
  listingImageUploading.value = true
  listingImageProgress.value = 0
  errorMessage.value = ''
  let optimizedFile = file
  try {
    optimizedFile = await measureRegistrationPhase('imageCompressionMs', () => compressImage(file))
    // 서버 상한과 같은 값을 미리 걸러 냅니다. 그냥 보내면 MEDIA_UPLOAD_INVALID로만 돌아와
    // 사용자는 무엇이 문제인지 알 수 없습니다.
    if (optimizedFile.size > MAX_LISTING_IMAGE_BYTES) {
      errorMessage.value = '대표 이미지는 15MB를 넘을 수 없습니다. 더 작은 용량의 파일을 올려 주세요.'
      return
    }
    const upload = await createProductImageUploadUrl(currentProductId.value, {
      filename: optimizedFile.name,
      contentType: optimizedFile.type || 'application/octet-stream',
      fileSize: optimizedFile.size,
    })
    await measureRegistrationPhase('s3UploadMs', () => uploadToPresignedUrl(
      upload.presignedUrl,
      optimizedFile,
      upload.requiredHeaders || {},
      (progress) => { listingImageProgress.value = progress },
    ))
    const image = await queueCompletion(() => completeProductImage(currentProductId.value, {
      uploadId: upload.uploadId,
      imageType: displayOrder === 0 ? 'THUMBNAIL' : 'DETAIL',
      displayOrder,
    }))
    listingImages.value.push({
      ...image,
      previewUrl: URL.createObjectURL(optimizedFile),
    })
    /*
      displayOrder로 다시 줄 세웁니다.
      -----------------------------------------------------------------------
      여러 장을 한 번에 올리면 업로드가 동시에 돌아, 먼저 끝난 것부터 push됩니다.
      그러면 화면에 늘어선 순서가 고른 순서가 아니라 '빨리 올라간 순서'가 되어,
      맨 왼쪽 사진이 매번 달라집니다. 대표 사진도 그래서 뒤바뀌어 보였습니다.
    */
    listingImages.value.sort((first, second) => first.displayOrder - second.displayOrder)
  } catch {
    errorMessage.value = '상품 이미지를 업로드하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    listingImageInFlight -= 1
    listingImageBusy.value = listingImageInFlight > 0
    listingImageUploading.value = listingImageInFlight > 0
    if (!listingImageBusy.value) listingImageProgress.value = 0
  }
}

// 대표 이미지를 고르면 바로 서버에 저장합니다. 상품이 없으면 초안을 먼저 만들어서
// 새로고침이나 이탈로 선택이 날아가지 않게 합니다.
async function onThumbnailInput(event) {
  const file = (event.target.files || [])[0]
  event.target.value = ''
  if (!file) return
  errorMessage.value = ''
  notice.value = ''

  if (!currentProductId.value) {
    // 초안 생성에 필요한 값이 아직 없으면 서버에 올릴 방법이 없습니다. 파일만 들고 있다가
    // 다음 저장 시점에 올리고, 지금은 아직 저장되지 않았다는 사실을 분명히 알립니다.
    if (!validateDeviceStep()) {
      if (pendingThumbnail.value) URL.revokeObjectURL(pendingThumbnail.value.previewUrl)
      pendingThumbnail.value = { file, previewUrl: URL.createObjectURL(file) }
      errorMessage.value = `${errorMessage.value} 이 항목을 채우면 대표 이미지가 바로 저장됩니다.`
      return
    }

    isSaving.value = true
    try {
      pendingThumbnail.value = { file, previewUrl: URL.createObjectURL(file) }
      // persistSaleInfo가 상품을 만든 뒤 flushPendingThumbnail로 업로드까지 이어집니다.
      await persistSaleInfo()
      // 업로드 실패는 예외를 던지지 않고 errorMessage만 세웁니다(1단계 입력을 살려야 하므로).
      // 여기서 직접 확인하지 않으면 실패했는데 저장됐다고 알리게 됩니다.
      if (!errorMessage.value) notice.value = '대표 이미지를 저장했습니다.'
    } catch (error) {
      errorMessage.value = error.message || '대표 이미지를 저장하지 못했습니다.'
    } finally {
      isSaving.value = false
    }
    return
  }

  const existing = listingThumbnail.value
  if (existing) await removeListingImage(existing)
  await handleListingImage(file, 0)
  if (!errorMessage.value) notice.value = '대표 이미지를 저장했습니다.'
}

async function removeThumbnail() {
  if (pendingThumbnail.value) {
    URL.revokeObjectURL(pendingThumbnail.value.previewUrl)
    pendingThumbnail.value = null
    return
  }
  if (listingThumbnail.value) await removeListingImage(listingThumbnail.value)
}

// 상품이 만들어진 직후 보류 중인 대표 이미지를 올립니다. 업로드가 실패해도 상품 저장 자체는
// 유지하고 안내만 남깁니다 — 여기서 예외를 던지면 1단계 입력이 통째로 날아가기 때문입니다.
async function flushPendingThumbnail() {
  if (!pendingThumbnail.value || !currentProductId.value) return
  const { file, previewUrl } = pendingThumbnail.value
  pendingThumbnail.value = null
  await handleListingImage(file, 0)
  URL.revokeObjectURL(previewUrl)
}

async function onListingImageInput(event) {
  const picked = [...(event.target.files || [])]
  const files = picked.slice(0, 10 - listingImages.value.length)
  const startOrder = listingImages.value.length
  event.target.value = ''
  // 자리가 부족해 잘린 파일을 알립니다. 그냥 버리면 사진이 사라진 것처럼 보입니다.
  if (picked.length > files.length) {
    openAlert(`${picked.length - files.length}개는 최대 개수(10개)를 넘어 올리지 못했습니다.`)
  }
  await runWithUploadLimit(files, (file, index) => handleListingImage(file, startOrder + index))
}

// 올린 사진 중 첫 장이 목록·상세에 보이는 대표 이미지입니다. 업로드 때는 서버가 첫 장을
// THUMBNAIL로 저장하지만, 그 사진을 지우면 대표가 없는 상태가 되므로 여기서 다시 세웁니다.
async function ensureThumbnail() {
  const images = listingImages.value
  if (!images.length) return
  if (images.some((image) => image.imageType === 'THUMBNAIL')) return
  await saveListingImageOrder(images.map((image) => image.imageId), images[0].imageId)
}

async function removeListingImage(image) {
  if (!currentProductId.value || !image?.imageId) return
  listingImageBusy.value = true
  try {
    await deleteProductImage(currentProductId.value, image.imageId)
    if (image.previewUrl) URL.revokeObjectURL(image.previewUrl)
    listingImages.value = await getProductImages(currentProductId.value)
    // 대표로 쓰이던 사진을 지우면 목록·상세의 썸네일이 통째로 비어 버립니다.
    // 남은 사진의 첫 장을 대표로 올려 항상 하나는 대표가 되게 합니다.
    await ensureThumbnail()
  } catch {
    errorMessage.value = '상품 이미지를 삭제하지 못했습니다.'
  } finally {
    listingImageBusy.value = false
  }
}

async function saveListingImageOrder(imageIds, thumbnailImageId) {
  if (!currentProductId.value || listingImageBusy.value) return
  listingImageBusy.value = true
  try {
    listingImages.value = await updateProductImageOrder(currentProductId.value, {
      imageIds,
      thumbnailImageId,
    })
  } catch {
    errorMessage.value = '상품 이미지 순서를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    listingImageBusy.value = false
  }
}

/*
  맨 왼쪽 사진이 대표입니다.
  ---------------------------------------------------------------------------
  예전에는 순서를 옮겨도 대표가 그대로 따라다녀서, 사진을 맨 앞으로 끌어와도 대표가
  바뀌지 않았습니다. 판매자가 대표를 정하는 방법이 '대표' 버튼 하나뿐이었고, 화면의
  왼쪽 사진과 실제 대표가 서로 다른 상태가 될 수 있었습니다.

  지금은 순서가 곧 대표입니다. 옮기면 첫 장이 대표가 됩니다.
*/
async function moveListingImage(image, offset) {
  const currentIndex = listingImages.value.findIndex((item) => item.imageId === image.imageId)
  const nextIndex = currentIndex + offset
  if (currentIndex < 0 || nextIndex < 0 || nextIndex >= listingImages.value.length) return
  const imageIds = listingImages.value.map((item) => item.imageId)
  ;[imageIds[currentIndex], imageIds[nextIndex]] = [imageIds[nextIndex], imageIds[currentIndex]]
  await saveListingImageOrder(imageIds, imageIds[0])
}

/** 한 칸씩 옮기지 않고 바로 맨 앞으로 보냅니다. 사진이 열 장이면 아홉 번 눌러야 합니다. */
async function makeListingThumbnail(image) {
  const others = listingImages.value
    .map((item) => item.imageId)
    .filter((imageId) => imageId !== image.imageId)
  await saveListingImageOrder([image.imageId, ...others], image.imageId)
}

async function onCaptureInput(event, item) {
  const picked = [...(event.target.files || [])]
  const files = picked.slice(0, remainingSlots(item.checklistItemId))
  event.target.value = ''
  if (picked.length > files.length) {
    openAlert(
      `${picked.length - files.length}개는 이 항목의 최대 개수(${maxMediaFor(item)}개)를 넘어 올리지 못했습니다.`,
    )
  }
  if (files.length) await runWithUploadLimit(files, (file) => handleCaptureFile(item, file))
}

// ── 그 자리에서 사진 찍기 ────────────────────────────────────────────────
// 사진은 미리보기 박스 안에서 바로 찍습니다. 파일 관리자를 열고 앨범을 뒤지는 것보다
// 기기를 손에 든 채 찍는 흐름이 짧습니다.
// 영상은 이 경로를 쓰지 않습니다(녹화는 별개 문제라 파일 업로드만 둡니다).
const isCameraReady = ref(false)
const isCapturing = ref(false)
const isShooting = ref(false)
const cameraError = ref('')
const cameraVideo = ref(null)
// 찍은 직후 바로 올리지 않고 한 번 보여 줍니다. 흔들리거나 잘린 사진을 그대로 올리면
// 구매자가 판단할 수 없고, 판매자는 올린 뒤에야 알게 됩니다.
const pendingShot = ref(null)
let cameraStream = null

// 촬영 버튼은 사진 항목에만 둡니다.
// 배터리 리포트·시스템 진단 정보 같은 진단 자료는 기기가 내보낸 파일(html·txt·xml)을 그대로
// 올려야 값을 신뢰할 수 있습니다. 화면을 찍은 사진은 업로드로는 받아 주되, 촬영 버튼을 앞세워
// 권하지는 않습니다.
const canShootActiveItem = computed(() => (
  isCameraReady.value
  && activeCaptureItem.value?.evidenceType === 'PHOTO'
  && !isActiveItemBusy.value
  && !isActiveItemFull.value
))

async function startCapture() {
  cameraError.value = ''
  try {
    cameraStream = await openCameraStream()
    isCapturing.value = true
    // v-if로 그려지는 video라서 DOM에 붙은 뒤에 스트림을 연결합니다.
    await nextTick()
    if (cameraVideo.value) {
      cameraVideo.value.srcObject = cameraStream
      await cameraVideo.value.play().catch(() => {})
    }
  } catch (error) {
    cameraError.value = cameraErrorMessage(error)
    stopCapture()
  }
}

function stopCapture() {
  stopCameraStream(cameraStream)
  cameraStream = null
  if (cameraVideo.value) cameraVideo.value.srcObject = null
  if (pendingShot.value) URL.revokeObjectURL(pendingShot.value.previewUrl)
  pendingShot.value = null
  isCapturing.value = false
  isShooting.value = false
}

async function shootPhoto() {
  const item = activeCaptureItem.value
  if (!item || isShooting.value) return
  isShooting.value = true
  cameraError.value = ''
  try {
    const file = await captureFrameToFile(
      cameraVideo.value,
      `checklist-${item.checklistItemId}-${captureFileStamp()}.jpg`,
    )
    pendingShot.value = { file, previewUrl: URL.createObjectURL(file) }
  } catch (error) {
    cameraError.value = error.message || '사진을 찍지 못했습니다. 다시 시도해 주세요.'
  } finally {
    isShooting.value = false
  }
}

// 카메라는 켜 둔 채로 방금 찍은 사진만 버립니다. 바로 다시 찍을 수 있어야 합니다.
function discardShot() {
  if (pendingShot.value) URL.revokeObjectURL(pendingShot.value.previewUrl)
  pendingShot.value = null
}

async function confirmShot() {
  const item = activeCaptureItem.value
  const shot = pendingShot.value
  if (!item || !shot || isShooting.value) return
  isShooting.value = true
  cameraError.value = ''
  try {
    // 촬영도 파일 선택과 같은 경로를 타서 압축·검증·업로드를 그대로 씁니다.
    await handleCaptureFile(item, shot.file)
    discardShot()
    // 저장한 뒤에도 카메라를 유지합니다. 여러 각도를 잇달아 찍는 흐름을 끊지 않기 위함입니다.
    // 첨부 상한을 채웠을 때만 자동으로 닫습니다.
    if (isActiveItemFull.value) stopCapture()
  } finally {
    isShooting.value = false
  }
}

// 파일명이 겹치지 않게만 하면 되므로 시각을 씁니다.
function captureFileStamp() {
  return new Date().toISOString().replace(/[:.]/g, '-')
}

// 다른 항목을 고르거나 단계를 옮기면 카메라를 놓아 줍니다. 켜 둔 채로 두면 기기의
// 카메라 표시등이 남고 다른 앱이 카메라를 쓸 수 없습니다.
watch(activeCaptureItemId, () => stopCapture())
watch(activeStep, () => stopCapture())
onBeforeUnmount(() => stopCapture())

function openMediaPreview(item, index) {
  mediaPreview.value = { checklistItemId: item.checklistItemId, itemName: item.name, index }
}

async function removePreviewedMedia() {
  const target = mediaPreview.value
  if (!target || !currentProductId.value || mediaDeleteInFlight.value) return
  const media = mediaOf(target.checklistItemId)
  const item = media[target.index]
  if (!item) return
  mediaDeleteInFlight.value = true
  try {
    await deleteEvidence(currentProductId.value, target.checklistItemId, item.key)
  } catch (error) {
    errorMessage.value = error.message || '첨부 파일을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.'
    return
  } finally {
    mediaDeleteInFlight.value = false
  }
  media.splice(target.index, 1)
  if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  delete diagnosisState[target.checklistItemId]
  mediaPreview.value = null
}


async function startEdit(productId) {
  errorMessage.value = ''
  try {
    const product = await getMyProduct(productId)
    listingImages.value = await getProductImages(productId)
    editingId.value = productId
    editingStatus.value = product.status || ''
    editingCustomModel.value = !!product.customModel
    draftProductId.value = null
    Object.assign(form, {
      categoryId: product.category?.categoryId || '',
      deviceModelId: product.device?.deviceModelId || '',
      name: product.name || '', description: product.description || '', price: product.price || '',
      color: product.device?.color || '', storageGb: product.device?.storageGb || '',
    })
    if (form.categoryId) models.value = await getDeviceModels({ categoryId: form.categoryId, page: 0, size: 100 })
    if (form.deviceModelId) await loadTemplatePreview()
    clearCaptureState()
    Object.keys(confirmState).forEach((key) => delete confirmState[key])
    checklistItems.value = await getProductChecklist(productId)
    // 서버가 확정된 선택 기능 코드를 그대로 내려주므로 itemCode 역추론 없이 바로 복원합니다.
    if (!editingCustomModel.value) {
      confirmedFeatures.value = product.confirmedFeatures || []
    }
    await Promise.all(checklistItems.value.map(async (item) => {
      if (CHECKABLE_ITEM_CODES[item.itemCode]) {
        confirmState[item.checklistItemId] = item.status === 'COMPLETED'
      }
      if (item.evidenceType === 'SELLER_CONFIRMATION') {
        return
      }
      const history = await getEvidenceHistory(productId, item.checklistItemId)
      captureState[item.checklistItemId] = {
        busy: '',
        progress: 0,
        media: history.map((evidence) => ({
          key: evidence.evidenceId,
          previewUrl: evidence.mediaUrl,
          mediaUrl: evidence.mediaUrl,
          evidenceType: evidence.evidenceType,
          name: `증빙 ${evidence.attemptNo}`,
          attemptNo: evidence.attemptNo,
          restored: true,
        })),
      }
      if (item.automationType && item.automationType !== 'NONE') {
        try {
          await refreshDiagnosis(item, { revealEmptyFields: true })
        } catch {
          // 조회 실패는 조용히 넘어가고, 새로 업로드하면 다시 자동 인식을 시도합니다.
        }
      }
    }))
    const draftProgress = product.status === 'DRAFT'
      ? await getProductDraftProgress(productId)
      : null
    if (draftProgress) {
      draftProgressResults.value = progressResultsMap(draftProgress.results) || new Map()
      draftDeviceResults.value = new Map(Object.entries(draftProgress.deviceResults || {}))
      checklistItems.value
        .filter((item) => item.evidenceType === 'SELLER_CONFIRMATION' || CHECKABLE_ITEM_CODES[item.itemCode])
        .forEach((item) => {
          const savedResult = draftProgressResults.value.get(item.checklistItemId)
          confirmState[item.checklistItemId] = savedResult
            ? savedResult === DEVICE_CHECK_RESULT.SUCCESS
            : item.status === 'COMPLETED'
        })
    }
    activeCaptureItemId.value = mediaChecklistItems.value[0]?.checklistItemId || null
    const hasEvidence = mediaChecklistItems.value.some(
      (item) => mediaOf(item.checklistItemId).length > 0,
    )
    /*
      수정은 1단계부터 엽니다. 이어서 쓰는 게 아니라 고치러 들어온 것이라, 기기 정보부터
      훑어보고 필요한 단계로 옮겨 가는 편이 자연스럽습니다.

      다만 이 화면에서 잠깐 나갔다 돌아온 경우는 다릅니다(실동작 점검 등). 주소에
      step이 실려 있으면 하던 자리로 되돌립니다.
    */
    activeStep.value = returnStep.value || 1
    /*
      3단계로 바로 들어오면 초기화 가이드를 아무도 불러 주지 않습니다.
      -----------------------------------------------------------------------
      가이드는 goToStep3()에서만 받아 왔습니다. 단계를 밟아 오는 길만 있다고 봤던
      것인데, 실동작 점검에서 돌아오면 그 함수를 지나지 않아 '판매 준비' 안내가
      통째로 비고 체크박스만 남았습니다.
    */
    if (activeStep.value === 3) await loadHandoverGuide()
    if (editingStatus.value && editingStatus.value !== 'DRAFT') {
      notice.value = '판매 중인 상품을 수정하고 있습니다.'
    } else if (hasEvidence) {
      notice.value = '임시저장된 상품 정보와 기존 S3 증빙을 복구했습니다.'
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    errorMessage.value = error.message || '상품 상세를 불러오지 못했습니다.'
  }
}

// 확대 보기는 Esc로도 닫습니다. 바깥을 누르는 것만 두면 화면을 가득 채운 사진에서
// 어디를 눌러야 닫히는지 알기 어렵습니다.
function closeExpandedImageOnEscape({ key: pressed }) {
  if (pressed === 'Escape') expandedImage.value = ''
}

onMounted(() => {
  window.addEventListener('keydown', closeExpandedImageOnEscape)
})

onBeforeUnmount(() => {
  stopChecklistLoading()
  stopWindowsInspectionPolling()
  modelRequestId += 1
  window.removeEventListener('keydown', closeExpandedImageOnEscape)
})

onMounted(async () => {
  // 카메라가 없는 기기에서 '촬영' 버튼을 보여주면 눌러도 실패만 하므로 미리 확인합니다.
  isCameraReady.value = await hasCameraDevice()

  try {
    categories.value = await getDeviceCategories({ activeOnly: true })
  } catch (error) {
    errorMessage.value = error.message || '기기 카테고리를 불러오지 못했습니다.'
  }

  // 직접 점검에서 등록 화면으로 돌아올 때는 /new 주소를 유지한 채 초안 ID로 이어서 엽니다.
  if (resumeProductId.value) {
    await startEdit(resumeProductId.value)
    return
  }

  // /seller/products/:productId/edit 로 들어오면 기존 데이터를 불러 수정 모드로 엽니다.
  if (route.params.productId) {
    await startEdit(Number(route.params.productId))
    if (reinspectionRequestKey.value) {
      try {
        reinspectionRequest.value = await getReinspectionRequest(reinspectionRequestKey.value)
        notice.value = '재검수 요청 항목에 새 증빙을 업로드한 뒤 완료해 주세요.'
        activeStep.value = 2
      } catch (error) {
        errorMessage.value = error.message || '재검수 요청을 불러오지 못했습니다.'
      }
    }
  }
})
</script>

<template>
  <DefaultLayout>
    <main class="page-shell">
      <!--
        머리말은 카드 밖으로 뺐습니다. 안에 넣으면 이 화면만 제목이 흰 판 안에서
        시작해, 다른 화면과 제목 위치가 어긋나 보입니다.
      -->
      <PageHeader
        eyebrow="ITEM REGISTER"
        :title="editingId && !isRegistrationResume ? '상품 수정' : '상품 등록'"
        description="기기 정보와 검증 체크리스트를 순서대로 완료하면 바로 판매가 시작됩니다.
        여러 번 나눠 작성하는 경우 임시저장을 꼭 해주세요."
      >
        <template #action>
          <RouterLink
            :to="{ name: 'seller-products' }"
            class="shrink-0 text-sm font-semibold text-primary hover:underline"
          >
            상품 관리로 이동
          </RouterLink>
        </template>
      </PageHeader>

      <!-- 흰 배경 패널은 유지하고 테두리만 없애 페이지에 자연스럽게 얹힙니다. -->
      <section class="mb-10 overflow-hidden rounded-lg bg-surface shadow-card">
        <div
          v-if="reinspectionRequest"
          class="border-b border-amber-200 bg-amber-50 px-6 py-4 text-sm text-amber-900"
        >
          <strong>재검수 요청:</strong> {{ reinspectionRequest.reason }}
          <ul class="mt-2 list-disc pl-5 text-xs">
            <li
              v-for="item in reinspectionRequest.items"
              :key="item.checklistItemId"
            >
              {{ item.itemName }}
            </li>
          </ul>
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
              선택한 모델에 맞는 검증 체크리스트가 자동으로 연결됩니다. 다음 단계에서는 항목별로 사진·영상을 등록해요.
            </p>

            <!--
              글제목과 가격을 맨 위로 올렸습니다.
              -------------------------------------------------------------------
              둘 다 반드시 있어야 다음 단계로 넘어가는 값인데, 예전에는 카테고리·모델
              선택과 체크리스트가 다 끝난 뒤 화면 맨 아래에 있었습니다. 무엇을 써야
              하는지 알려면 한참 내려가야 했습니다.
            -->
            <div class="mt-6 grid gap-5 sm:grid-cols-2">
              <!-- 서버 필드명은 name이지만, 판매자가 쓰는 것은 판매글의 제목이라 화면에서는 '글제목'으로 부릅니다. -->
              <label class="text-sm font-semibold text-text-main">글제목<span class="ml-0.5 text-red-500">*</span><input
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
            </div>

            <div class="mt-5 grid gap-5 sm:grid-cols-2">
              <label class="text-sm font-semibold text-text-main">카테고리<span class="ml-0.5 text-red-500">*</span>
                <select
                  v-model="form.categoryId"
                  required
                  class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-3 font-normal outline-none focus:border-primary"
                  @change="onCategoryChange"
                >
                  <option value="">카테고리 선택</option><option
                    v-for="item in categories"
                    :key="item.categoryId"
                    :value="item.categoryId"
                  >{{ item.name }}</option>
                </select>
              </label>
              <label class="text-sm font-semibold text-text-main">기기 모델<span class="ml-0.5 text-red-500">*</span>
                <div class="mt-2 flex gap-2">
                  <input
                    v-model="modelKeyword"
                    type="search"
                    placeholder="제조사·모델명·모델 코드 검색"
                    aria-label="기기 모델 검색"
                    :disabled="!form.categoryId"
                    class="min-w-0 flex-1 rounded-md border border-border bg-bg px-3 py-2.5 font-normal outline-none focus:border-primary disabled:opacity-60"
                    @keydown.enter.prevent="searchModels"
                  >
                  <BaseButton
                    type="button"
                    variant="secondary"
                    :disabled="!form.categoryId || isLoadingModels"
                    @click="searchModels"
                  >
                    {{ isLoadingModels ? '검색 중…' : '검색' }}
                  </BaseButton>
                </div>
                <select
                  v-model="form.deviceModelId"
                  :disabled="!form.categoryId || isLoadingModels"
                  required
                  class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-3 font-normal outline-none focus:border-primary disabled:opacity-60"
                  @change="onDeviceModelChange"
                >
                  <option value="">
                    {{ isLoadingModels ? '모델 목록 불러오는 중…' : '기기 모델 선택' }}
                  </option>
                  <optgroup
                    v-for="group in modelGroups"
                    :key="group.manufacturer"
                    :label="group.manufacturer"
                  >
                    <option
                      v-for="item in group.items"
                      :key="item.deviceModelId"
                      :value="item.deviceModelId"
                    >
                      {{ item.modelName }}{{ item.modelCode ? ` (${item.modelCode})` : '' }}
                    </option>
                  </optgroup>
                </select>
                <span
                  v-if="modelLoadError"
                  class="mt-2 block text-xs font-normal text-red-600"
                >{{ modelLoadError }}</span>
                <span
                  v-else-if="form.categoryId && !isLoadingModels && modelGroups.length === 0 && modelKeyword"
                  class="mt-2 block text-xs font-normal text-text-muted"
                >검색 결과가 없습니다.</span>
                <span
                  v-else-if="form.categoryId"
                  class="mt-2 block text-xs font-normal text-text-muted"
                >
                  등록된 모델은 이 목록에 즉시 추가됩니다. 제조사·모델명·모델 코드로 검색할 수 있습니다.
                </span>
              </label>
            </div>

            <div
              v-if="!editingId"
              class="mt-3"
            >
              <button
                type="button"
                class="text-sm font-semibold text-primary underline underline-offset-2"
                @click="toggleCustomModelInput"
              >
                {{ isCustomModelInput ? '등록된 모델에서 선택' : '찾는 모델이 없나요? 직접 입력' }}
              </button>

              <div
                v-if="isCustomModelInput"
                class="mt-3 rounded-lg border border-border bg-bg p-4"
              >
                <div class="grid gap-3 sm:grid-cols-2">
                  <label class="text-sm font-semibold text-text-main sm:col-span-2">
                    카테고리<span class="ml-0.5 text-red-500">*</span>
                    <select
                      v-model="form.categoryId"
                      aria-label="신규 모델 카테고리"
                      required
                      class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2.5 font-normal"
                      @change="onCustomCategoryChange"
                    >
                      <option value="">
                        카테고리 선택
                      </option>
                      <option
                        v-for="item in categories"
                        :key="item.categoryId"
                        :value="item.categoryId"
                      >
                        {{ item.name }}
                      </option>
                    </select>
                  </label>
                  <label class="text-sm font-semibold text-text-main">
                    제조사
                    <input
                      v-model.trim="customModel.manufacturer"
                      class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2.5 font-normal"
                      maxlength="50"
                      placeholder="예: Samsung"
                    >
                  </label>
                  <label class="text-sm font-semibold text-text-main">
                    모델명
                    <input
                      v-model.trim="customModel.modelName"
                      class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2.5 font-normal"
                      maxlength="100"
                      placeholder="예: Galaxy S25"
                    >
                  </label>
                  <label class="text-sm font-semibold text-text-main">
                    모델 코드(선택)
                    <input
                      v-model.trim="customModel.modelCode"
                      class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2.5 font-normal"
                      maxlength="50"
                    >
                  </label>
                  <label class="text-sm font-semibold text-text-main">
                    운영체제
                    <select
                      v-model="customModel.osFamily"
                      class="mt-2 w-full rounded-md border border-border bg-white px-3 py-2.5 font-normal"
                    >
                      <option value="ANDROID">Android</option>
                      <option value="IOS">iOS</option>
                      <option value="WINDOWS">Windows</option>
                      <option value="MACOS">macOS</option>
                      <option value="LINUX">Linux</option>
                    </select>
                  </label>
                </div>
                <BaseButton
                  class="mt-4"
                  type="button"
                  :disabled="isRequestingModel || Boolean(modelRequestResult)"
                  @click="submitModelRequest"
                >
                  {{ isRequestingModel ? '요청 등록 중...' : '모델 검토 요청' }}
                </BaseButton>
                <p
                  v-if="modelRequestResult"
                  class="mt-3 rounded-md bg-emerald-50 px-3 py-2 text-xs leading-5 text-emerald-700"
                >
                  모델이 즉시 등록되었습니다. 관리자 검토와 관계없이 현재 판매 등록을 계속할 수 있습니다.
                </p>
              </div>
            </div>

            <div
              v-if="isGeneratingChecklist"
              role="status"
              aria-live="polite"
              class="mt-5 rounded-lg border border-border bg-bg px-4 py-5"
            >
              <p class="text-center text-sm font-semibold text-text-main">
                {{ checklistLoadingStage }}<span
                  aria-hidden="true"
                  class="inline-block w-5 text-left"
                >{{ checklistLoadingDots }}</span>
                <span class="sr-only">진행 중</span>
              </p>
              <div
                class="mt-4 h-2 overflow-hidden rounded-full bg-border"
                role="progressbar"
                aria-label="AI 체크리스트 조사 진행률"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-valuenow="checklistLoadingProgress"
              >
                <div
                  class="h-full rounded-full bg-primary transition-[width] duration-500 ease-out"
                  :style="{ width: `${checklistLoadingProgress}%` }"
                />
              </div>
              <p class="mt-2 text-center text-xs text-text-sub">
                {{ checklistLoadingProgress }}% · 조사 중에도 이 페이지는 정상적으로 동작하고 있습니다.
              </p>
            </div>

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
                  </p>
                  <p class="mt-2 text-sm font-semibold text-text-main">
                    기본 {{ templateItems.length }}개 + AI 선택 {{ selectedAiSuggestions.length }}개
                    = 전체 {{ previewChecklistItems.length }}개
                  </p>
                  <p class="mt-1 text-xs text-text-sub">
                    촬영·업로드 {{ previewMediaItemCount }}개 · 직접 확인 {{ previewConfirmationItemCount }}개
                  </p>
                </div>
                <BaseBadge
                  :variant="checklistGeneration.researchStatus === 'FAILED'
                    ? 'danger'
                    : checklistGeneration.aiApplied ? 'primary' : 'gray'"
                >
                  {{
                    checklistGeneration.aiSuggestions?.length
                      ? 'AI 추가 항목 선택 가능'
                      : checklistGeneration.researchStatus === 'FAILED'
                        ? 'AI 조사 실패 · 기본 정책 적용'
                        : checklistGeneration.aiApplied
                          ? 'AI 공식자료 반영'
                          : '기본 정책 적용'
                  }}
                </BaseBadge>
              </div>
              <p
                v-if="checklistGeneration.researchStatus === 'PENDING_REVIEW'"
                class="mt-3 rounded-md bg-white/80 px-3 py-2 text-xs leading-5 text-text-sub"
              >
                공식 자료 조사 결과는 관리자에게도 사후 보고됩니다.
                아래 AI 추가 항목 중 실제 기기에 해당하는 항목만 선택해 주세요.
              </p>
              <p
                v-else-if="checklistGeneration.researchStatus === 'FAILED'"
                class="mt-3 rounded-md bg-red-50 px-3 py-2 text-xs leading-5 text-red-700"
                role="alert"
              >
                공식 자료 AI 조사에 실패해 검증된 기본 체크리스트를 적용했습니다.
                관리자가 실패 원인을 확인하고 재조사할 수 있으며 상품 등록은 그대로 진행할 수 있습니다.
              </p>
              <p
                v-else-if="!checklistGeneration.aiSuggestions?.length && !checklistGeneration.aiApplied"
                class="mt-3 rounded-md bg-white/80 px-3 py-2 text-xs leading-5 text-text-sub"
              >
                AI 연결 없이 검증된 기기별 기본 정책으로 생성했습니다.
                상품 등록은 그대로 진행할 수 있습니다.
              </p>
              <p
                v-if="editingId && editingCustomModel"
                class="mt-3 rounded-md bg-white/80 px-3 py-2 text-xs leading-5 text-text-sub"
              >
                직접 입력한 기기는 등록 후 기능 체크리스트를 수정할 수 없습니다.
              </p>
            </div>

            <div
              v-if="checklistGeneration?.aiSuggestions?.length"
              class="mt-5 rounded-lg border border-primary/30 bg-white p-4"
            >
              <h3 class="text-sm font-bold text-text-main">
                AI가 공식 자료에서 찾은 추가 항목
              </h3>
              <p class="mt-1 text-xs leading-5 text-text-sub">
                기본 항목은 항상 적용됩니다. 아래 항목은 실제 기기에 해당하는 경우에만 선택해 주세요.
                <template v-if="editingId && editingCustomModel">
                  직접 입력한 기기는 이 목록을 수정할 수 없습니다.
                </template>
              </p>
              <ul class="mt-3 grid gap-3 sm:grid-cols-2">
                <li
                  v-for="suggestion in checklistGeneration.aiSuggestions"
                  :key="suggestion.featureCode"
                  class="rounded-md border border-border bg-bg p-3"
                >
                  <label
                    class="flex items-start gap-3"
                    :class="editingCustomModel ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'"
                  >
                    <input
                      v-model="confirmedFeatures"
                      type="checkbox"
                      :value="suggestion.featureCode"
                      :disabled="editingCustomModel"
                      class="mt-1 h-4 w-4 rounded border-border text-primary"
                    >
                    <span>
                      <strong class="text-sm text-text-main">
                        {{ suggestion.featureName || suggestion.featureCode }}
                      </strong>
                      <span class="ml-2 rounded-pill bg-white px-2 py-0.5 text-[10px] font-bold text-primary-dark">
                        {{ evidenceTypeLabel(suggestion.evidenceType) }}
                      </span>
                      <span class="mt-1 block text-xs leading-5 text-text-sub">
                        {{ suggestion.checkGuide || suggestion.reason }}
                      </span>
                    </span>
                  </label>
                </li>
              </ul>
            </div>

            <h3
              v-if="templateItems.length"
              class="mt-5 text-sm font-bold text-text-main"
            >
              필수 기본 체크리스트
            </h3>
            <ul
              v-if="templateItems.length"
              class="mt-2 grid gap-2 sm:grid-cols-2"
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

            <label class="mt-6 block text-sm font-semibold text-text-main sm:col-span-2">
              상품 설명
              <textarea
                v-model.trim="form.description"
                maxlength="2000"
                rows="5"
                placeholder="외관 상태, 사용 기간, 구성품 등을 알려 주세요."
                class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
              />
            </label>
          </section>

          <section
            v-else-if="activeStep === 2"
            class="grid gap-6 lg:grid-cols-[1fr_1.15fr]"
          >
            <div class="rounded-lg border border-border bg-white p-4 lg:col-span-2">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 class="font-bold text-text-main">
                    상품 이미지
                  </h2>
                  <p class="mt-1 text-sm text-text-sub">
                    <strong class="font-semibold text-text-main">맨 왼쪽 사진이 대표 이미지</strong>입니다.
                    최대 10개까지 올리고, ← → 로 순서를 바꿔 대표를 정하세요.
                  </p>
                </div>
                <!-- 다른 주요 버튼(BaseButton primary)과 같은 그라데이션·번짐을 씁니다. -->
                <label
                  class="btn-glow inline-flex cursor-pointer items-center justify-center rounded-md bg-primary-gradient px-4 py-2 text-sm font-semibold text-white shadow-elevated transition-all hover:brightness-110"
                  :class="listingImageAddDisabled ? 'pointer-events-none opacity-55' : ''"
                >
                  {{ listingImageBusy ? '업로드 중…' : '이미지 추가' }}
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    class="sr-only"
                    :disabled="listingImageAddDisabled"
                    @change="onListingImageInput"
                  >
                </label>
              </div>
              <!--
                올리는 중에만 띄웁니다.
                ---------------------------------------------------------------
                예전에는 listingImageBusy로 열어 두어, 순서를 바꿀 때도 막대가
                0%로 깜빡였습니다. 올릴 것이 없으니 채워지지도 않고 사라져
                화면만 어수선했습니다.
              -->
              <div
                v-if="listingImageUploading"
                class="mt-3 h-2 overflow-hidden rounded-pill bg-slate-100"
                role="progressbar"
                :aria-valuenow="listingImageProgress"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div
                  class="h-full bg-primary transition-all"
                  :style="{ width: `${listingImageProgress}%` }"
                />
              </div>
              <p class="mt-2 text-[11px] text-text-sub">
                최근 처리시간:
                체크리스트 {{ registrationMetrics.checklistMs ?? '-' }}ms ·
                이미지 압축 {{ registrationMetrics.imageCompressionMs ?? '-' }}ms ·
                영상 압축 {{ registrationMetrics.videoCompressionMs ?? '-' }}ms ·
                S3 업로드 {{ registrationMetrics.s3UploadMs ?? '-' }}ms
              </p>
              <p
                v-if="!listingImages.length"
                class="mt-4 rounded-md bg-bg px-4 py-5 text-center text-sm text-text-sub"
              >
                등록된 상품 이미지가 없습니다.
              </p>
              <!--
                순서를 바꾸면 사진이 제자리로 미끄러져 들어갑니다.
                ---------------------------------------------------------------
                TransitionGroup이 옮기기 전후 위치를 재어 그 사이를 채워 줍니다(FLIP).
                두 장이 순간이동하면 무엇과 무엇이 바뀐 것인지 눈으로 못 따라갑니다.
              -->
              <TransitionGroup
                v-else
                tag="ul"
                name="thumb"
                class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5"
              >
                <!--
                  화면에 늘어선 순서(index)로 판단합니다. image.displayOrder를 쓰면
                  서버 값과 방금 옮긴 화면 순서가 어긋나는 순간에 버튼이 잘못 잠깁니다.
                -->
                <li
                  v-for="(image, index) in listingImages"
                  :key="image.imageId"
                  class="relative overflow-hidden rounded-md border border-border"
                >
                  <!-- 눌러서 크게 봅니다. 정사각으로 잘라 두어 잘린 부분은 확대로만 확인됩니다. -->
                  <button
                    type="button"
                    class="block w-full cursor-zoom-in"
                    :aria-label="`${index + 1}번째 상품 이미지 확대 보기`"
                    @click="expandedImage = image.previewUrl || image.imageUrl"
                  >
                    <img
                      :src="image.previewUrl || image.imageUrl"
                      alt="상품 등록 이미지"
                      class="aspect-square w-full object-cover"
                    >
                  </button>
                  <span
                    v-if="image.imageType === 'THUMBNAIL'"
                    class="absolute left-1 top-1 rounded bg-primary px-2 py-1 text-[11px] font-bold text-white"
                  >대표</span>
                  <button
                    type="button"
                    class="absolute right-1 top-1 rounded bg-black/65 px-2 py-1 text-xs text-white"
                    aria-label="상품 이미지 삭제"
                    :disabled="listingImageBusy"
                    @click="removeListingImage(image)"
                  >
                    삭제
                  </button>
                  <!-- 화살표는 양 끝으로 붙입니다. 가운데 모여 있으면 어느 쪽으로 가는지 헷갈립니다. -->
                  <div class="flex items-center justify-between border-t border-border bg-white px-1 py-1">
                    <button
                      type="button"
                      class="rounded px-2 py-1 text-xs font-semibold text-text-sub hover:bg-bg"
                      :disabled="listingImageBusy || index === 0"
                      aria-label="이미지 순서를 앞으로 이동"
                      @click="moveListingImage(image, -1)"
                    >
                      ←
                    </button>
                    <button
                      type="button"
                      class="rounded px-2 py-1 text-xs font-semibold text-text-sub hover:bg-bg"
                      :disabled="listingImageBusy || index === listingImages.length - 1"
                      aria-label="이미지 순서를 뒤로 이동"
                      @click="moveListingImage(image, 1)"
                    >
                      →
                    </button>
                  </div>
                </li>
              </TransitionGroup>
            </div>
            <div>
              <h2 class="text-lg font-bold text-text-main">
                검수용 기기 촬영
              </h2>
              <p class="mt-1 text-sm text-text-sub">
                구매자가 확인할 수 있도록 촬영·업로드 항목 {{ mediaChecklistItems.length }}개에
                사진·영상·진단파일을 등록하세요.
              </p>

              <section
                v-if="checklistItems.some((item) => item.automationType === 'FILE_PARSE')"
                class="mt-4 rounded-lg border border-primary/30 bg-accent p-4"
                aria-labelledby="windows-inspection-title"
              >
                <h3
                  id="windows-inspection-title"
                  class="text-sm font-bold text-text-main"
                >
                  Windows 자동 진단
                </h3>
                <!-- 두 문장을 각각 한 줄로 둡니다. 뒤 문장이 개인정보에 관한 안내라
                     앞 문장에 붙어 흐르면 눈에 걸리지 않습니다. -->
                <p class="mt-1 text-xs leading-5 text-text-sub">
                  Limit 진단 프로그램으로 기기 정보와 점검 결과를 자동으로 입력할 수 있습니다.<br>
                  비밀번호와 개인 파일은 수집하지 않습니다.
                </p>
                <div class="mt-3 flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    class="rounded-md bg-primary-gradient px-4 py-2 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="windowsInspectionBusy"
                    @click="startWindowsInspection"
                  >
                    {{ windowsInspectionBusy ? '코드 발급 중…' : '연결 코드 발급' }}
                  </button>
                  <a
                    :href="windowsScannerUrl"
                    class="rounded-md border border-primary px-4 py-2 text-sm font-bold text-primary"
                    download
                  >
                    진단 프로그램 다운로드
                  </a>
                </div>
                <div
                  v-if="windowsInspection"
                  class="mt-3 rounded-md bg-surface p-3"
                >
                  <p class="text-xs text-text-sub">
                    진단 프로그램에 아래 코드를 입력해 주세요.
                  </p>
                  <p class="mt-1 font-mono text-3xl font-bold tracking-[0.35em] text-primary-dark">
                    {{ windowsInspection.pairingCode || '연결됨' }}
                  </p>
                  <p class="mt-2 text-xs font-semibold text-text-sub">
                    상태: {{ windowsInspection.status }}
                  </p>
                </div>
                <p
                  v-if="windowsInspectionError"
                  class="mt-3 text-xs font-semibold text-red-700"
                  role="alert"
                >
                  {{ windowsInspectionError }}
                </p>
              </section>

              <p class="mt-4 rounded-md bg-accent px-4 py-3 text-sm font-semibold text-primary-dark">
                현재 진행률: {{ mediaChecklistItems.length }}개 중 {{ capturedMediaCount }}개 등록 완료
              </p>

              <!-- 항목이 많으면 화면이 길어져서 6개 정도만 보이고 나머지는 스크롤로 봅니다. -->
              <ul class="mt-4 max-h-[32rem] space-y-3 overflow-y-auto pr-1">
                <li
                  v-for="item in mediaChecklistItems"
                  :key="item.testType"
                >
                  <div
                    role="button"
                    tabindex="0"
                    class="w-full cursor-pointer rounded-lg border p-4 text-left transition-colors"
                    :class="activeCaptureItemId === item.checklistItemId
                      ? 'border-primary bg-accent'
                      : 'border-border hover:border-primary'"
                    @click="activeCaptureItemId = item.checklistItemId"
                    @keydown.enter="activeCaptureItemId = item.checklistItemId"
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
                          <span
                            v-if="isReinspectionItem(item)"
                            class="rounded-pill bg-red-50 px-1.5 py-0.5 text-[10px] font-bold text-red-600"
                          >재검수</span>
                          <!-- 목록 선택과 별개로, 이 버튼만 눌러야 촬영 가이드 모달이 뜨도록 stop으로 막습니다. -->
                          <button
                            type="button"
                            class="flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-full border border-primary text-[9px] font-bold text-primary transition hover:bg-accent"
                            :aria-label="`${item.name} 촬영 가이드 보기`"
                            @click.stop="openGuideModal(item)"
                          >
                            i
                          </button>
                        </p>
                        <p class="mt-1 text-xs text-text-sub">
                          {{ guideFor(item) }}
                        </p>
                      </div>
                      <span
                        class="shrink-0 text-xs font-semibold"
                        :class="['captured', 'auto-completed'].includes(captureStatusOf(item.checklistItemId)) ? 'text-primary' : 'text-text-sub'"
                      >
                        <template v-if="captureStatusOf(item.checklistItemId) === 'captured'">
                          첨부 {{ mediaOf(item.checklistItemId).length }} / {{ maxMediaFor(item) }}
                        </template>
                        <template v-else-if="captureProgressLabel(item.checklistItemId)">
                          {{ captureProgressLabel(item.checklistItemId) }}
                        </template>
                        <template v-else-if="captureStatusOf(item.checklistItemId) === 'auto-completed'">자동 입력 완료</template>
                        <template v-else-if="activeCaptureItemId === item.checklistItemId">촬영 대기</template>
                        <template v-else>미촬영</template>
                      </span>
                    </div>
                  </div>
                </li>
                <li
                  v-if="!mediaChecklistItems.length"
                  class="rounded-md bg-bg px-4 py-6 text-center text-sm text-text-sub"
                >
                  촬영이 필요한 항목이 없습니다.
                </li>
              </ul>
            </div>

            <div class="card-soft rounded-lg bg-surface p-6">
              <h2 class="text-base font-bold text-text-main">
                {{ activeCaptureItem ? `${activeCaptureItem.name} 촬영 프리뷰` : '촬영 프리뷰' }}
              </h2>

              <div class="relative mt-4 flex aspect-[4/3] items-center justify-center overflow-hidden rounded-lg border border-border bg-bg">
                <!--
                  촬영 중에는 이 박스가 그대로 카메라 화면이 됩니다. 따로 창을 띄우지 않고
                  한 화면에서 찍도록 두는 편이 흐름이 짧습니다.

                  방금 찍은 사진은 이 video를 '덮어서' 보여 줍니다. v-if로 갈아 끼우면 video가
                  DOM에서 빠지면서 카메라 스트림 연결이 끊기고, 다시 찍을 때 화면이 비어 버립니다.
                -->
                <template v-if="isCapturing">
                  <video
                    ref="cameraVideo"
                    class="h-full w-full object-cover"
                    autoplay
                    playsinline
                    muted
                  />
                  <img
                    v-if="pendingShot"
                    :src="pendingShot.previewUrl"
                    alt="방금 촬영한 사진"
                    class="absolute inset-0 h-full w-full object-cover"
                  >
                </template>
                <template v-else-if="activeItemLatestMedia && activeItemLatestMedia.evidenceType === 'VIDEO'">
                  <video
                    :src="activeItemLatestMedia.previewUrl"
                    class="h-full w-full object-cover"
                    controls
                  />
                </template>
                <template v-else-if="activeItemLatestMedia && activeItemLatestMedia.evidenceType === 'PHOTO'">
                  <img
                    :src="activeItemLatestMedia.previewUrl"
                    :alt="activeCaptureItem.name"
                    class="h-full w-full object-cover"
                  >
                </template>
                <template v-else-if="activeItemLatestMedia">
                  <p class="px-6 text-center text-sm text-text-sub">
                    {{ activeItemLatestMedia.name }} 파일이 첨부되었습니다.
                  </p>
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

              <p
                v-if="cameraError"
                role="alert"
                class="mt-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700"
              >
                {{ cameraError }}
              </p>

              <!-- 찍은 사진을 쓸지 먼저 묻습니다. 저장해도 카메라는 켜 둔 채로 이어서 찍습니다. -->
              <div v-if="pendingShot">
                <p class="mt-4 text-center text-sm font-semibold text-text-main">
                  이 사진으로 하시겠습니까?
                </p>
                <div class="mt-3 flex items-center gap-3">
                  <button
                    type="button"
                    class="min-w-0 flex-1 rounded-md bg-primary-gradient px-4 py-3 text-sm font-bold text-white shadow-elevated transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isShooting"
                    @click="confirmShot"
                  >
                    {{ isShooting ? '저장 중…' : '예 (저장)' }}
                  </button>
                  <button
                    type="button"
                    class="min-w-0 flex-1 rounded-md border border-border px-4 py-3 text-sm font-bold text-text-sub transition hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isShooting"
                    @click="discardShot"
                  >
                    아니오 (다시 촬영)
                  </button>
                </div>
              </div>

              <!--
                촬영 중에는 찍기·취소만 남깁니다. 이때 파일 선택까지 같이 두면 화면이 복잡해집니다.
              -->
              <div
                v-else-if="isCapturing"
                class="mt-4 flex items-center gap-3"
              >
                <button
                  type="button"
                  class="min-w-0 flex-1 rounded-md bg-primary-gradient px-4 py-3 text-sm font-bold text-white shadow-elevated transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="isShooting"
                  @click="shootPhoto"
                >
                  {{ isShooting ? '처리 중…' : '사진 찍기' }}
                </button>
                <button
                  type="button"
                  class="shrink-0 rounded-md border border-border px-4 py-3 text-sm font-bold text-text-sub transition hover:border-primary hover:text-primary"
                  @click="stopCapture"
                >
                  촬영 끝내기
                </button>
              </div>

              <div
                v-else
                class="mt-4 flex items-center gap-3"
              >
                <!--
                  사진 항목이고 카메라가 있으면 '촬영'과 '파일 업로드' 두 개를 둡니다.
                  영상 항목이나 카메라가 없는 기기에서는 파일 업로드 하나만 남습니다.
                -->
                <button
                  v-if="canShootActiveItem"
                  type="button"
                  class="min-w-0 flex-1 rounded-md bg-primary-gradient px-4 py-3 text-sm font-bold text-white shadow-elevated transition hover:brightness-110"
                  @click="startCapture"
                >
                  촬영하기
                </button>
                <label class="min-w-0 flex-1">
                  <input
                    type="file"
                    class="hidden"
                    aria-label="검증 항목 파일 업로드"
                    :accept="captureAccept(activeCaptureItem)"
                    multiple
                    :disabled="!activeCaptureItem || isActiveItemBusy || isActiveItemFull"
                    @change="onCaptureInput($event, activeCaptureItem)"
                  >
                  <span
                    class="block rounded-md px-4 py-3 text-center text-sm font-bold transition"
                    :class="[
                      canShootActiveItem
                        ? 'border border-primary bg-surface text-primary hover:bg-accent'
                        : 'bg-primary-gradient text-white shadow-elevated hover:brightness-110',
                      activeCaptureItem && !isActiveItemBusy && !isActiveItemFull
                        ? 'cursor-pointer'
                        : 'cursor-not-allowed opacity-60',
                    ]"
                  >
                    {{ canShootActiveItem ? '파일 업로드' : activeItemStatusLabel }}
                  </span>
                  <!--
                    압축과 업로드를 같은 막대로 보여 줍니다. 예전에는 업로드 %만 있어서,
                    영상을 줄이는 몇십 초 동안은 아무 표시가 없었습니다. 차례를 기다리는
                    동안에는 채울 값이 없으므로 막대를 비우고 '대기 중'만 적습니다.
                  -->
                  <span
                    v-if="activeCaptureItem && captureProgressLabel(activeCaptureItem.checklistItemId)"
                    class="mt-2 block"
                  >
                    <span class="mb-1 flex items-center justify-between text-xs font-medium text-primary">
                      <span>{{ captureProgressLabel(activeCaptureItem.checklistItemId) }}</span>
                    </span>
                    <span
                      class="block h-1.5 w-full overflow-hidden rounded-full bg-border"
                      role="progressbar"
                      :aria-label="`${activeCaptureItem.name} 처리 진행률`"
                      :aria-valuenow="progressOf(activeCaptureItem.checklistItemId)"
                      aria-valuemin="0"
                      aria-valuemax="100"
                    >
                      <span
                        class="block h-full rounded-full bg-primary-gradient transition-[width] duration-200"
                        :style="{ width: `${progressOf(activeCaptureItem.checklistItemId)}%` }"
                      />
                    </span>
                  </span>
                </label>
              </div>

              <!--
                첨부한 파일은 버튼 아래에 모아 보여 줍니다. 버튼 옆에 두면 촬영 중에는 카메라
                화면에 가려 저장이 됐는지 확인할 수 없습니다. 누르면 확인 모달을 엽니다.
              -->
              <div class="mt-3">
                <ul
                  v-if="activeItemMedia.length"
                  class="flex flex-wrap items-center gap-2"
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
                        v-else-if="media.evidenceType === 'PHOTO'"
                        :src="media.previewUrl"
                        alt=""
                        class="h-full w-full object-cover"
                      >
                      <span
                        v-else
                        class="flex h-full w-full items-center justify-center bg-bg text-[10px] font-medium text-text-sub"
                      >
                        파일
                      </span>
                    </button>
                  </li>
                </ul>
                <!-- 아직 없을 때도 자리를 남겨 둡니다. 어디에 쌓이는지 미리 보이게 하려는 것입니다. -->
                <div
                  v-else
                  class="h-12 w-12 rounded-md border border-dashed border-border bg-bg"
                  aria-hidden="true"
                />
              </div>

              <div
                v-if="activeCaptureItem && diagnosisState[activeCaptureItem.checklistItemId]"
                class="mt-4 rounded-lg border border-border bg-bg p-4"
              >
                <p
                  v-if="diagnosisState[activeCaptureItem.checklistItemId].status === 'parsing'"
                  class="mt-2 text-xs text-text-sub"
                >
                  업로드한 파일에서 사양을 인식하는 중입니다…
                </p>
                <p
                  v-if="diagnosisState[activeCaptureItem.checklistItemId].errorMessage"
                  class="mt-2 text-xs text-red-600"
                >
                  {{ diagnosisState[activeCaptureItem.checklistItemId].errorMessage }}
                </p>

                <ul
                  v-if="diagnosisState[activeCaptureItem.checklistItemId].fields.length"
                  class="mt-3 space-y-3"
                >
                  <li
                    v-for="field in diagnosisState[activeCaptureItem.checklistItemId].fields"
                    :key="field.fieldName"
                  >
                    <div class="flex items-center justify-between gap-2">
                      <span class="text-xs font-semibold text-text-main">
                        {{ diagnosisFieldLabel(field.fieldName) }}
                      </span>
                      <BaseBadge
                        v-if="field.conflict"
                        variant="danger"
                      >
                        값이 서로 다름
                      </BaseBadge>
                    </div>
                    <p
                      v-if="field.conflict"
                      class="mt-1 text-[11px] text-text-sub"
                    >
                      스크린샷 인식값: {{ field.ocrValue || '-' }} · 파일 인식값: {{ field.fileParseValue || '-' }}
                    </p>
                    <div class="mt-1 flex items-center gap-2">
                      <input
                        v-model="field.draftValue"
                        type="text"
                        :aria-label="`${diagnosisFieldLabel(field.fieldName)} 값`"
                        class="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm"
                        @input="field.justSaved = false"
                      >
                      <BaseButton
                        type="button"
                        variant="outline"
                        class="shrink-0 px-3 py-2 text-xs"
                        :aria-label="`${diagnosisFieldLabel(field.fieldName)} 저장`"
                        :disabled="field.saving || !field.draftValue"
                        @click="saveDiagnosisValue(activeCaptureItem.checklistItemId, field)"
                      >
                        {{ field.saving ? '저장 중…' : '저장' }}
                      </BaseButton>
                    </div>
                    <p
                      v-if="field.justSaved"
                      role="status"
                      class="mt-1 text-[11px] font-semibold text-primary"
                    >
                      ✓ 저장됐습니다.
                    </p>
                  </li>
                </ul>
              </div>

              <p class="mt-2 text-center text-[11px] text-text-sub">
                항목별 최대 파일 개수와 크기·영상 길이를 적용합니다.
                파일은 자동으로 압축됩니다.
              </p>
              <p class="mt-1 text-center text-[11px] text-text-sub">
                영상은 항목마다 1개씩, 60초 이내·100MB 이하만 올릴 수 있습니다. (판매글 전체로는 최대 6개)
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

            <div class="col-span-full rounded-lg border border-border bg-surface p-6">
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h2 class="text-lg font-bold text-text-main">
                    실동작 점검
                  </h2>
                  <p class="mt-1 text-sm text-text-sub">
                    진단 프로그램으로 완료된 항목은 자동 반영됩니다. 나머지 항목은 웹에서 직접 점검할 수 있습니다.
                  </p>
                </div>
                <BaseButton
                  v-if="currentProductId"
                  variant="outline"
                  :to="{ name: 'seller-product-device-check', params: { productId: currentProductId } }"
                >
                  카메라·마이크·키보드 등 직접 점검하기
                </BaseButton>
              </div>

              <ul class="mt-4 grid gap-3 md:grid-cols-2">
                <li
                  v-for="item in deviceCheckConfirmationItems"
                  :key="item.checklistItemId"
                  class="flex items-start justify-between gap-3 rounded-lg border border-border p-4"
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
                  <BaseBadge
                    class="shrink-0"
                    :variant="deviceCheckStatus(item) === 'COMPLETED'
                      ? 'success'
                      : deviceCheckStatus(item) === 'FAILED' ? 'danger' : 'gray'"
                  >
                    {{ deviceCheckStatusLabel(item) }}
                  </BaseBadge>
                </li>
              </ul>
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
              구매자에게 전달되기 전 개인정보 보호를 위해 기기의 계정·개인정보를 반드시 초기화해 주세요.
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

            <h3 class="mt-6 text-sm font-bold text-text-main">
              개인정보 확인 항목
            </h3>
            <ul class="mt-3 space-y-3">
              <li
                v-for="item in privacyChecklistItems"
                :key="item.checklistItemId"
                class="rounded-lg border border-border p-4"
              >
                <label class="flex cursor-pointer items-start gap-3">
                  <input
                    v-model="confirmState[item.checklistItemId]"
                    type="checkbox"
                    class="mt-1 h-4 w-4 rounded border-border"
                    @change="persistDraftProgress"
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
                v-if="!privacyChecklistItems.length"
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
                  글제목
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
                  촬영·업로드 완료
                </dt><dd class="mt-1 font-semibold text-text-main">
                  {{ capturedMediaCount }} / {{ mediaChecklistItems.length }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  개인정보 확인
                </dt><dd class="mt-1 font-semibold text-text-main">
                  {{ confirmedCount }} / {{ privacyChecklistItems.length }}
                </dd>
              </div>
              <div v-if="deviceCheckConfirmationItems.length">
                <dt class="text-xs text-text-sub">
                  실동작 점검
                </dt><dd class="mt-1 font-semibold text-text-main">
                  {{ deviceCheckConfirmedCount }} / {{ deviceCheckConfirmationItems.length }}
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
              v-else-if="previewedMedia.evidenceType === 'PHOTO'"
              :src="previewedMedia.previewUrl"
              :alt="`${mediaPreview.itemName} 첨부 파일`"
              class="h-full w-full object-contain"
            >
            <p
              v-else
              class="px-6 text-center text-sm text-text-sub"
            >
              미리보기를 지원하지 않는 파일입니다.
            </p>
          </div>
          <p class="mt-2 truncate text-xs text-text-sub">
            {{ previewedMedia.name }}
          </p>

          <div class="mt-5 flex gap-3">
            <BaseButton
              type="button"
              variant="outline"
              class="flex-1"
              :disabled="mediaDeleteInFlight"
              @click="removePreviewedMedia"
            >
              {{ mediaDeleteInFlight ? '삭제 중...' : '삭제' }}
            </BaseButton>
            <BaseButton
              type="button"
              class="flex-1"
              :disabled="mediaDeleteInFlight"
              @click="mediaPreview = null"
            >
              확인
            </BaseButton>
          </div>
        </div>
      </div>

      <!-- 체크리스트 항목 촬영 가이드 모달 -->
      <div
        v-if="guideModalItem"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
        role="dialog"
        aria-modal="true"
        aria-label="촬영 가이드"
        @click.self="closeGuideModal"
      >
        <div class="w-full max-w-md rounded-lg bg-surface p-5 shadow-elevated">
          <div class="flex items-start justify-between gap-3">
            <h2 class="text-base font-bold text-text-main">
              {{ guideModalItem.name }}
            </h2>
            <button
              type="button"
              class="shrink-0 text-text-sub transition hover:text-text-main"
              aria-label="닫기"
              @click="closeGuideModal"
            >
              ✕
            </button>
          </div>

          <div class="mt-4 flex aspect-[4/3] items-center justify-center overflow-hidden rounded-lg border border-border bg-bg">
            <img
              v-if="guideImageFor(guideModalItem, selectedCategoryName)"
              :src="guideImageFor(guideModalItem, selectedCategoryName)"
              :alt="`${guideModalItem.name} 촬영 예시`"
              class="h-full w-full object-contain"
            >
            <p
              v-else
              class="px-6 text-center text-sm text-text-sub"
            >
              예시 이미지가 준비되지 않았습니다.
            </p>
          </div>

          <div class="mt-4 space-y-2">
            <p
              v-if="guidePurposeFor(guideModalItem)"
              class="text-sm text-text-main"
            >
              {{ guidePurposeFor(guideModalItem) }}
            </p>
            <p
              v-if="guideStepsFor(guideModalItem)"
              class="rounded-md bg-accent px-3 py-2 text-sm text-primary-dark"
            >
              {{ guideStepsFor(guideModalItem) }}
            </p>
          </div>

          <BaseButton
            type="button"
            class="mt-5 w-full"
            @click="closeGuideModal"
          >
            확인했어요
          </BaseButton>
        </div>
      </div>

      <!--
        올린 사진 크게 보기.
        -------------------------------------------------------------------------
        목록에서는 정사각으로 잘라 보여 주기 때문에, 실제로 무엇이 찍혔는지는
        여기서만 확인됩니다. 바깥을 누르거나 Esc로 닫습니다.
      -->
      <div
        v-if="expandedImage"
        role="dialog"
        aria-modal="true"
        aria-label="상품 이미지 확대 보기"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/85 p-4 sm:p-8"
        @click.self="expandedImage = ''"
      >
        <button
          type="button"
          class="absolute right-4 top-4 flex h-11 w-11 items-center justify-center rounded-full bg-white/15 text-2xl text-white transition hover:bg-white/25"
          aria-label="확대 이미지 닫기"
          @click="expandedImage = ''"
        >
          ×
        </button>
        <img
          :src="expandedImage"
          alt="상품 이미지 확대"
          class="max-h-full max-w-full rounded-lg object-contain shadow-2xl"
        >
      </div>
    </main>
  </DefaultLayout>
</template>

<style scoped>
/*
  사진 순서 바꾸기 애니메이션.
  ---------------------------------------------------------------------------
  TransitionGroup이 옮기기 전 위치와 옮긴 뒤 위치를 재어, 그 사이를 transform으로
  채웁니다. 두 장이 순간이동하면 무엇과 무엇이 자리를 바꿨는지 눈으로 못 따라갑니다.
  240ms에 살짝 튀는 곡선을 써서 "밀려 들어갔다"는 느낌만 줍니다.
*/
.thumb-move {
  transition: transform 0.24s cubic-bezier(0.34, 1.2, 0.64, 1);
}

@media (prefers-reduced-motion: reduce) {
  .thumb-move {
    transition: none;
  }
}
</style>
