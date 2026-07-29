<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseTable from '../components/BaseTable.vue'
import {
  completeEvidence,
  createEvidenceUploadUrl,
  createProduct,
  deleteProduct,
  generateChecklist,
  getChecklistTemplate,
  getDeviceCategories,
  getDeviceModels,
  getHandoverGuide,
  getMyProduct,
  getMyProducts,
  getProductChecklist,
  transitionProductStatus,
  updateProduct,
} from '../api/products'
import { compressImage, compressVideo } from '../utils/mediaOptimize'
import { searchPlaces } from '../api/places'

const WIZARD_STEPS = [
  { number: 1, label: '기기 등록' },
  { number: 2, label: '촬영 및 파일 업로드' },
  { number: 3, label: '개인정보 관리' },
  { number: 4, label: '등록완료' },
]

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

const categories = ref([])
const models = ref([])
const products = ref([])
const statusFilter = ref('')
const isLoading = ref(false)
const isSaving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const editingId = ref(null)
const draftProductId = ref(null)
const activeStep = ref(1)
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false })
const form = reactive({
  categoryId: '', deviceModelId: '', name: '', description: '', price: '',
  color: '', storageGb: '', tradeRegion: '',
})

const tradeRegionResults = ref([])
const isSearchingTradeRegion = ref(false)
const showTradeRegionResults = ref(false)
let tradeRegionSearchTimer = null

function onTradeRegionInput() {
  showTradeRegionResults.value = true
  clearTimeout(tradeRegionSearchTimer)
  const keyword = form.tradeRegion.trim()
  if (!keyword) {
    tradeRegionResults.value = []
    return
  }
  tradeRegionSearchTimer = setTimeout(async () => {
    isSearchingTradeRegion.value = true
    try {
      tradeRegionResults.value = await searchPlaces(keyword)
    } catch {
      tradeRegionResults.value = []
    } finally {
      isSearchingTradeRegion.value = false
    }
  }, 300)
}

function selectTradeRegion(place) {
  const address = place.roadAddressName || place.addressName
  form.tradeRegion = place.placeName && place.placeName !== address
    ? `${place.placeName} (${address})`
    : address
  tradeRegionResults.value = []
  showTradeRegionResults.value = false
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
const captureState = reactive({})
const confirmState = reactive({})
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
  () => mediaChecklistItems.value.filter((item) => captureState[item.checklistItemId]?.status === 'captured').length,
)
const confirmedCount = computed(
  () => confirmationChecklistItems.value.filter((item) => confirmState[item.checklistItemId]).length,
)
const activeCaptureItem = computed(
  () => mediaChecklistItems.value.find((item) => item.checklistItemId === activeCaptureItemId.value)
    || mediaChecklistItems.value[0]
    || null,
)
const isActiveItemBusy = computed(() => {
  const status = activeCaptureItem.value && captureState[activeCaptureItem.value.checklistItemId]?.status
  return status === 'optimizing' || status === 'uploading'
})
const activeItemStatusLabel = computed(() => {
  const status = activeCaptureItem.value && captureState[activeCaptureItem.value.checklistItemId]?.status
  if (status === 'optimizing') return '최적화 중…'
  if (status === 'uploading') return '업로드 중…'
  return '촬영 또는 파일 업로드'
})

function templateFor(itemCode) {
  return templateItems.value.find((item) => item.itemCode === itemCode) || null
}

function evidenceTypeLabel(type) {
  return {
    PHOTO: '사진',
    VIDEO: '영상',
    DIAGNOSTIC_FILE: '진단파일',
    SELLER_CONFIRMATION: '확인',
  }[type] || type
}

function evidenceStatusLabel(status) {
  return {
    VERIFIED: '공식 확인',
    LIKELY: '제품군 확인',
    UNKNOWN: '근거 부족',
    CONFLICTED: '자료 충돌',
  }[status] || status
}

function isRequiredItem(item) {
  return item.required ?? item.isRequired ?? false
}

function captureAccept(item) {
  if (!item) return ''
  if (item.evidenceType === 'VIDEO') return 'video/*'
  if (item.evidenceType === 'PHOTO') return 'image/*'
  return '*/*'
}

function resetForm() {
  editingId.value = null
  draftProductId.value = null
  activeStep.value = 1
  Object.assign(form, {
    categoryId: '', deviceModelId: '', name: '', description: '', price: '',
    color: '', storageGb: '', tradeRegion: '',
  })
  models.value = []
  templateItems.value = []
  checklistGeneration.value = null
  confirmedFeatures.value = []
  isGeneratingChecklist.value = false
  checklistItems.value = []
  activeCaptureItemId.value = null
  handoverGuide.value = null
  Object.keys(captureState).forEach((key) => delete captureState[key])
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

function validateSaleInfo() {
  if (!form.name || form.price === '' || !form.tradeRegion) {
    errorMessage.value = '상품명, 가격, 거래 지역을 입력해 주세요.'
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

async function goToStep2() {
  errorMessage.value = ''
  if (!form.categoryId || !form.deviceModelId) {
    errorMessage.value = '카테고리와 기기 모델을 선택해 주세요.'
    return
  }
  if (!validateSaleInfo()) return

  isSaving.value = true
  try {
    const payload = {
      name: form.name,
      description: form.description || null,
      price: Number(form.price),
      color: form.color || null,
      storageGb: form.storageGb ? Number(form.storageGb) : null,
      tradeRegion: form.tradeRegion,
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
    activeStep.value = 2
  } catch (error) {
    errorMessage.value = error.message || '상품 정보를 저장하지 못했습니다.'
  } finally {
    isSaving.value = false
  }
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

function goToStep3() {
  errorMessage.value = ''
  const missingRequired = mediaChecklistItems.value.some(
    (item) => isRequiredItem(item) && captureState[item.checklistItemId]?.status !== 'captured',
  )
  if (missingRequired) {
    errorMessage.value = '필수 촬영 항목을 모두 완료해 주세요.'
    return
  }
  activeStep.value = 3
  loadHandoverGuide()
}

function goToStep4() {
  errorMessage.value = ''
  const missingConfirm = confirmationChecklistItems.value.some(
    (item) => isRequiredItem(item) && !confirmState[item.checklistItemId],
  )
  if (missingConfirm) {
    errorMessage.value = '개인정보 정리 항목을 모두 확인해 주세요.'
    return
  }
  activeStep.value = 4
}

function finishWizard() {
  notice.value = editingId.value ? '상품 정보를 수정했습니다.' : '상품 초안을 등록했습니다.'
  resetForm()
  loadProducts(0)
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
  const rawPreviewUrl = URL.createObjectURL(file)
  captureState[item.checklistItemId] = { file, previewUrl: rawPreviewUrl, status: 'optimizing' }

  // 업로드 용량과 서버 비용을 줄이기 위해 사진은 Canvas로, 영상은 ffmpeg.wasm으로
  // 브라우저에서 먼저 압축한 뒤 업로드합니다. 압축에 실패하면 원본으로 계속 진행합니다.
  let optimizedFile = file
  try {
    if (item.evidenceType === 'PHOTO') optimizedFile = await compressImage(file)
    else if (item.evidenceType === 'VIDEO') optimizedFile = await compressVideo(file)
  } catch {
    optimizedFile = file
  }

  const previewUrl = optimizedFile === file ? rawPreviewUrl : URL.createObjectURL(optimizedFile)
  if (previewUrl !== rawPreviewUrl) URL.revokeObjectURL(rawPreviewUrl)
  captureState[item.checklistItemId] = { file: optimizedFile, previewUrl, status: 'uploading' }

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
    captureState[item.checklistItemId] = { file: optimizedFile, previewUrl, status: 'captured' }
  }
}

function onCaptureInput(event, item) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) handleCaptureFile(item, file)
}

async function loadProducts(page = 0) {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getMyProducts({
      status: statusFilter.value,
      page,
      size: 20,
      sort: 'updatedAt,desc',
    })
    products.value = response?.data || []
    pageMeta.value = response?.meta || { page, totalPages: 0, hasNext: false }
  } catch (error) {
    errorMessage.value = error.message || '상품 목록을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
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
      tradeRegion: product.tradeRegion || '',
    })
    if (form.categoryId) models.value = await getDeviceModels({ categoryId: form.categoryId, page: 0, size: 100 })
    if (form.deviceModelId) await loadTemplatePreview()
    checklistItems.value = await getProductChecklist(productId)
    activeCaptureItemId.value = mediaChecklistItems.value[0]?.checklistItemId || null
    Object.keys(captureState).forEach((key) => delete captureState[key])
    Object.keys(confirmState).forEach((key) => delete confirmState[key])
    activeStep.value = 1
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    errorMessage.value = error.message || '상품 상세를 불러오지 못했습니다.'
  }
}

async function remove(product) {
  if (!window.confirm(`‘${product.name}’ 상품을 삭제할까요?`)) return
  try {
    await deleteProduct(product.productId)
    notice.value = '상품을 삭제했습니다.'
    const targetPage = products.value.length === 1 && pageMeta.value.page > 0
      ? pageMeta.value.page - 1
      : pageMeta.value.page
    await loadProducts(targetPage)
  } catch (error) {
    errorMessage.value = error.message || '상품을 삭제하지 못했습니다.'
  }
}

async function publish(product) {
  try {
    await transitionProductStatus(product.productId, 'ON_SALE', '판매 등록')
    notice.value = '상품을 판매중으로 전환했습니다.'
    await loadProducts(pageMeta.value.page)
  } catch (error) {
    errorMessage.value = error.message || '필수 체크리스트를 먼저 완료해 주세요.'
  }
}

watch(statusFilter, () => loadProducts(0))
onMounted(async () => {
  try {
    categories.value = await getDeviceCategories({ activeOnly: true })
  } catch (error) {
    errorMessage.value = error.message || '기기 카테고리를 불러오지 못했습니다.'
  }
  await loadProducts(0)
})
</script>

<template>
  <MyPageLayout>
    <section class="mb-10 overflow-hidden rounded-lg border border-border bg-surface shadow-card">
      <div class="flex flex-col gap-5 border-b border-border bg-bg px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">
            SELL YOUR DEVICE
          </p>
          <h1 class="mt-1 text-2xl font-bold text-text-main">
            {{ editingId ? '상품 수정' : '상품 등록' }}
          </h1>
          <p class="mt-1 text-sm text-text-sub">
            기기 정보와 검증 체크리스트를 순서대로 완료하면 안전하게 초안으로 저장됩니다.
          </p>
        </div>
        <button
          v-if="editingId || draftProductId"
          type="button"
          class="text-sm font-semibold text-primary"
          @click="resetForm"
        >
          등록으로 돌아가기
        </button>
      </div>

      <ol
        class="grid grid-cols-4 border-b border-border"
        aria-label="상품 등록 단계"
      >
        <li
          v-for="step in WIZARD_STEPS"
          :key="step.number"
          class="flex flex-col items-center justify-center gap-2 border-r border-border px-2 py-4 text-center text-[11px] font-semibold last:border-r-0 sm:text-xs"
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
            <label class="text-sm font-semibold text-text-main">카테고리
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
            <label class="text-sm font-semibold text-text-main">기기 모델
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
              <span class="text-xs font-semibold text-primary">
                {{ confirmedFeatures.length }} / 5개 선택
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
                      <span class="text-sm font-bold text-text-main">{{ suggestion.featureCode }}</span>
                      <BaseBadge :variant="suggestion.evidenceStatus === 'VERIFIED' ? 'primary' : 'gray'">
                        {{ evidenceStatusLabel(suggestion.evidenceStatus) }}
                      </BaseBadge>
                    </span>
                    <span class="mt-1 block text-xs leading-5 text-text-sub">{{ suggestion.reason }}</span>
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
            승인 목록 밖의 기능은 자동 추가하지 않았습니다:
            {{ checklistGeneration.reviewCandidates.join(', ') }}
          </p>

          <div class="mt-6 grid gap-5 sm:grid-cols-2">
            <label class="text-sm font-semibold text-text-main">상품명<input
              v-model.trim="form.name"
              required
              maxlength="100"
              placeholder="예: 갤럭시 S24 256GB 자급제"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="text-sm font-semibold text-text-main">가격<input
              v-model="form.price"
              required
              min="1"
              type="number"
              placeholder="판매 가격"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="text-sm font-semibold text-text-main">색상<input
              v-model.trim="form.color"
              maxlength="50"
              placeholder="예: 오닉스 블랙"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="text-sm font-semibold text-text-main">저장 용량(GB)<input
              v-model="form.storageGb"
              min="1"
              type="number"
              placeholder="예: 256"
              class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
            ></label>
            <label class="relative text-sm font-semibold text-text-main sm:col-span-2">
              거래 지역
              <input
                v-model.trim="form.tradeRegion"
                required
                maxlength="100"
                placeholder="역, 랜드마크로 검색 (예: 상동역)"
                autocomplete="off"
                class="mt-2 w-full rounded-md border border-border px-3 py-3 font-normal outline-none focus:border-primary"
                @input="onTradeRegionInput"
                @focus="showTradeRegionResults = true"
                @blur="showTradeRegionResults = false"
              >
              <ul
                v-if="showTradeRegionResults && (tradeRegionResults.length || isSearchingTradeRegion)"
                class="absolute z-10 mt-1 max-h-60 w-full overflow-y-auto rounded-md border border-border bg-white text-left shadow-lg"
              >
                <li
                  v-if="isSearchingTradeRegion"
                  class="px-3 py-2 text-xs font-normal text-text-sub"
                >
                  검색 중...
                </li>
                <li
                  v-for="place in tradeRegionResults"
                  :key="`${place.placeName}-${place.addressName}`"
                >
                  <button
                    type="button"
                    class="w-full px-3 py-2 text-left text-sm font-normal hover:bg-accent"
                    @mousedown.prevent="selectTradeRegion(place)"
                  >
                    <span class="block font-semibold text-text-main">{{ place.placeName || place.addressName }}</span>
                    <span class="block text-xs text-text-sub">{{ place.roadAddressName || place.addressName }}</span>
                  </button>
                </li>
              </ul>
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

            <ul class="mt-4 space-y-3">
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
                      <p class="text-sm font-bold text-text-main">
                        {{ item.name }}
                      </p>
                      <p class="mt-1 text-xs text-text-sub">
                        {{ templateFor(item.itemCode)?.guide }}
                      </p>
                    </div>
                    <span
                      class="shrink-0 text-xs font-semibold"
                      :class="captureState[item.checklistItemId]?.status === 'captured' ? 'text-primary' : 'text-text-sub'"
                    >
                      <template v-if="captureState[item.checklistItemId]?.status === 'captured'">촬영완료</template>
                      <template v-else-if="captureState[item.checklistItemId]?.status === 'optimizing'">최적화 중…</template>
                      <template v-else-if="captureState[item.checklistItemId]?.status === 'uploading'">업로드 중…</template>
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
              <template v-if="activeCaptureItem && captureState[activeCaptureItem.checklistItemId]?.previewUrl">
                <video
                  v-if="activeCaptureItem.evidenceType === 'VIDEO'"
                  :src="captureState[activeCaptureItem.checklistItemId].previewUrl"
                  class="h-full w-full object-cover"
                  controls
                />
                <img
                  v-else
                  :src="captureState[activeCaptureItem.checklistItemId].previewUrl"
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

            <label class="mt-4 block">
              <input
                type="file"
                class="hidden"
                :accept="captureAccept(activeCaptureItem)"
                capture="environment"
                :disabled="!activeCaptureItem || isActiveItemBusy"
                @change="onCaptureInput($event, activeCaptureItem)"
              >
              <span
                class="block rounded-md bg-primary-gradient px-4 py-3 text-center text-sm font-bold text-white shadow-elevated transition hover:brightness-110"
                :class="activeCaptureItem && !isActiveItemBusy ? 'cursor-pointer' : 'cursor-not-allowed opacity-60'"
              >
                {{ activeItemStatusLabel }}
              </span>
            </label>
            <p class="mt-2 text-center text-[11px] text-text-sub">
              사진은 자동으로 리사이즈, 영상은 브라우저에서 자동 압축된 뒤 업로드됩니다.
            </p>

            <div class="mt-5 rounded-lg bg-bg p-4 text-xs leading-6 text-text-sub">
              <p class="mb-1 font-bold text-text-main">
                촬영 꿀팁 가이드
              </p>
              <p v-if="templateFor(activeCaptureItem?.itemCode)?.guide">
                • {{ templateFor(activeCaptureItem.itemCode).guide }}
              </p>
              <p>• 흔들림을 예방하기 위해 촬영 순간 숨을 참고 1초간 유지해 주세요.</p>
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
                  <span class="mt-1 block text-xs text-text-sub">{{ templateFor(item.itemCode)?.guide }}</span>
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
            ‘{{ form.name }}’ 상품이 초안으로 저장됐어요. 판매 시작 전 남은 항목을 마저 확인해 주세요.
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
            @click="activeStep -= 1"
          >
            이전 단계로
          </BaseButton>
          <span v-else />

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

    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-lg font-bold text-text-main">
        내 상품
      </h2>
      <select
        v-model="statusFilter"
        aria-label="상품 상태 필터"
        class="rounded-md border border-border bg-white px-3 py-2 text-sm"
      >
        <option value="">
          전체 상태
        </option><option value="DRAFT">
          초안
        </option><option value="ON_SALE">
          판매중
        </option><option value="HIDDEN">
          숨김
        </option>
      </select>
    </div>
    <p
      v-if="isLoading"
      class="py-8 text-center text-sm text-text-sub"
    >
      상품을 불러오는 중입니다.
    </p>
    <BaseTable
      v-else
      :columns="['상품', '상태', '체크리스트', '관리']"
    >
      <tr
        v-for="product in products"
        :key="product.productId"
      >
        <td class="px-4 py-3">
          <p class="font-semibold text-text-main">
            {{ product.name }}
          </p><p class="text-xs text-text-sub">
            #{{ product.productId }}
          </p>
        </td>
        <td class="px-4 py-3">
          <BaseBadge :variant="product.status === 'ON_SALE' ? 'primary' : 'gray'">
            {{ product.status }}
          </BaseBadge>
        </td>
        <td class="px-4 py-3 text-sm text-text-sub">
          {{ product.completedItemCount }} / {{ product.requiredItemCount }}
        </td>
        <td class="space-x-3 px-4 py-3 text-sm">
          <button
            class="text-primary"
            @click="startEdit(product.productId)"
          >
            수정
          </button><button
            v-if="product.status === 'DRAFT'"
            class="text-primary"
            @click="publish(product)"
          >
            판매 시작
          </button><button
            class="text-red-600"
            @click="remove(product)"
          >
            삭제
          </button>
        </td>
      </tr>
      <tr v-if="!products.length">
        <td
          colspan="4"
          class="px-4 py-12 text-center text-sm text-text-sub"
        >
          등록한 상품이 없습니다.
        </td>
      </tr>
    </BaseTable>
    <nav
      v-if="!isLoading && pageMeta.totalPages > 1"
      class="mt-6 flex items-center justify-center gap-4"
      aria-label="내 상품 페이지"
    >
      <BaseButton
        variant="outline"
        :disabled="pageMeta.page === 0"
        @click="loadProducts(pageMeta.page - 1)"
      >
        이전
      </BaseButton>
      <span class="text-sm text-text-sub">
        {{ pageMeta.page + 1 }} / {{ pageMeta.totalPages }}
      </span>
      <BaseButton
        variant="outline"
        :disabled="!pageMeta.hasNext"
        @click="loadProducts(pageMeta.page + 1)"
      >
        다음
      </BaseButton>
    </nav>
  </MyPageLayout>
</template>
