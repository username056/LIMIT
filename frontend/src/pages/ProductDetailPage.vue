<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import ProfileAvatar from '../components/ProfileAvatar.vue'
import DiagnosisSpecList from '../components/DiagnosisSpecList.vue'
import { addFavorite, getFavoriteStatus, removeFavorite } from '../api/favorites'
import {
  createReinspectionRequest,
  getEvidenceHistory,
  getMyProduct,
  getProduct,
  getProductChecklist,
  getProductImages,
  transitionProductStatus,
} from '../api/products'
import { createOrGetChatRoom } from '../api/chat'
import { getProductDiagnosisSummary } from '../api/inspection'
import { getSellerProfile } from '../api/seller'
import { getAccessToken, getSessionMember } from '../auth/session'
import { canSellerMarkSold, canSellerReopen, isSoldOut } from '../utils/productStatus'
import { isDiagnosisComplete } from '../utils/diagnosisCompletion'
import {
  ALL_BATTERY_FIELDS,
  BASIC_INFO_FIELDS,
  BATTERY_DETAIL_FIELDS,
  BATTERY_FALLBACK_FIELDS,
  BATTERY_GRADE_BASIS,
  BATTERY_GRADE_DISCLAIMER,
  BATTERY_UNAVAILABLE_DESCRIPTION,
  BATTERY_UNAVAILABLE_TITLE,
  BATTERY_UNMEASURABLE_DESCRIPTION,
  BATTERY_UNMEASURABLE_TITLE,
  CYCLE_COUNT_DISCLAIMER,
  GRAPHICS_DEVICE_FIELDS,
  batteryGradeFor,
  diagnosisFieldValue,
} from '../utils/diagnosisFields'

const route = useRoute()
const router = useRouter()
const product = ref(null)
const isLoading = ref(true)
const isFavorite = ref(false)
const isUpdatingFavorite = ref(false)
const isOpeningChat = ref(false)
const errorMessage = ref('')
const productImages = ref([])
const activeImageUrl = ref('')
// 구매자에게 공개되는 판매자 정보입니다(닉네임·개인/사업자·판매 중 수).
const sellerProfile = ref(null)
// 소유자 전용 조회로 불러온 경우(비공개 상품)와, 판매 중인 내 상품을 공개 조회로 본 경우를 함께 다룹니다.
const loadedViaOwnerApi = ref(false)
const isOwner = computed(() => {
  if (loadedViaOwnerApi.value) return true
  const memberId = getSessionMember()?.memberId
  return Boolean(memberId && product.value?.sellerId
    && String(product.value.sellerId) === String(memberId))
})

const checklist = computed(() => product.value?.checklistSummary || {})
const canPurchase = computed(() => product.value?.status === 'ON_SALE')
const purchaseButtonLabel = computed(() => {
  if (canPurchase.value) return '상품 구매하기'
  if (product.value?.status === 'RESERVED') return '예약 중인 상품입니다'
  if (product.value?.status === 'DRAFT') return '임시 저장 중인 상품입니다'
  if (product.value?.status === 'HIDDEN') return '숨김 상태인 상품입니다'
  return '판매가 완료된 상품입니다'
})

// 재촬영 요청 팝업
const checklistItems = ref([])
const evidenceByChecklistItem = ref({})
const buyerChecklistItems = computed(
  () => checklistItems.value.filter((item) => item.visibleToBuyer !== false),
)

// 판매글에 스냅샷된 체크리스트를 그대로 보여줍니다. 항목 순서·이름·필수 여부는 판매자가 등록할 때
// 본 것과 동일한 목록(getProductChecklist)이고, 증빙만 항목별로 묶어 붙입니다.
const buyerChecklist = computed(() => buyerChecklistItems.value.map((item) => {
  const evidence = evidenceByChecklistItem.value[item.checklistItemId] || []
  return {
    ...item,
    required: item.required ?? item.isRequired ?? false,
    // 자료가 한 장이라도 올라와 있으면 확인된 것으로 봅니다. 서버는 항목별 최소 장수·길이까지
    // 채워야 COMPLETED로 두는데, 구매자에게는 "자료가 있느냐"가 먼저 궁금한 정보입니다.
    //
    // 진단 프로그램이 채운 항목은 증빙 파일이 남지 않습니다. 값은 옆 '자동 인식된 사양'
    // 카드에 멀쩡히 떠 있는데 여기만 '자료 준비 중'이 되던 이유입니다. 판매하기 화면이
    // '자동 입력 완료'라고 부르는 것과 같은 기준으로 봅니다.
    completed: item.status === 'COMPLETED' || evidence.length > 0 || isAutoFilled(item),
    evidence,
  }
}))

// 판단 기준은 utils/diagnosisCompletion에 있고, 값을 찾는 방법만 여기서 알려 줍니다.
// 등록 화면은 항목별 fields를, 이 화면은 상품 전체의 사양 요약을 들고 있습니다.
function isAutoFilled(item) {
  return isDiagnosisComplete(item, (fieldName) => {
    const field = findDiagnosisItem(fieldName)
    return field?.status === 'AVAILABLE' && String(field.value ?? '').trim().length > 0
  })
}

// 위 기준으로 다시 셉니다. 서버가 준 요약을 그대로 쓰면 항목에는 ✓가 떠 있는데 개수는 안 올라가
// 화면 안에서 숫자가 어긋납니다.
const buyerChecklistProgress = computed(() => {
  const required = buyerChecklist.value.filter((item) => item.required)
  return {
    required: required.length,
    completed: required.filter((item) => item.completed).length,
  }
})

const checklistRate = computed(() => {
  const { required, completed } = buyerChecklistProgress.value
  return required ? Math.min(100, Math.round((completed / required) * 100)) : 0
})

const EVIDENCE_TYPE_LABELS = {
  PHOTO: '사진',
  VIDEO: '영상',
  DIAGNOSTIC_FILE: '진단파일',
  SELLER_CONFIRMATION: '판매자 확인',
}

function evidenceTypeLabel(type) {
  return EVIDENCE_TYPE_LABELS[type] || type || '자료'
}

// ocr_result/dxdiag_result/battery_report_result에서 취합된 필드별 자동 인식 사양입니다.
const diagnosisSummaryItems = ref([])
const diagnosisDisclaimer = ref('')

// 인식에 성공한 항목 수입니다. 옆 검증 카드와 같은 배지·막대로 보여 주어 두 카드를 같은
// 눈으로 읽게 합니다. 실패한 항목도 목록에는 남기므로 분모는 전체 항목 수입니다.
const diagnosisProgress = computed(() => {
  const total = diagnosisSummaryItems.value.length
  const available = diagnosisSummaryItems.value
    .filter((item) => item.status === 'AVAILABLE').length
  return { total, available }
})

const diagnosisRate = computed(() => {
  const { total, available } = diagnosisProgress.value
  return total ? Math.round((available / total) * 100) : 0
})

function findDiagnosisItem(fieldName) {
  return diagnosisSummaryItems.value.find((item) => item.fieldName === fieldName)
}

function pickDiagnosisItems(fieldNames) {
  return fieldNames.map(findDiagnosisItem).filter(Boolean)
}

// 배터리 외 사양은 그대로 필드 단위 목록입니다.
const basicInfoItems = computed(() => pickDiagnosisItems(BASIC_INFO_FIELDS))
const graphicsDeviceItems = computed(() => pickDiagnosisItems(GRAPHICS_DEVICE_FIELDS))

/*
  배터리는 셋 중 하나 상태로 보여줍니다.
  ---------------------------------------------------------------------------
  구매자가 궁금한 건 원시 수치가 아니라 "이 배터리를 믿고 써도 되는가"입니다. 그래서
  용량 비율(건강도)이 있으면 등급 카드로, 없으면 확보된 값만 참고 정보로, 그마저 없으면
  한 줄 안내로 대체합니다. 값이 없는 필드를 추정하지 않는 게 원칙입니다.
*/
const capacityRatioItem = computed(() => findDiagnosisItem('CAPACITY_RATIO'))
const cycleCountItem = computed(() => findDiagnosisItem('CYCLE_COUNT'))

/*
  값 자체를 파싱해 확인합니다. status만 AVAILABLE이고 값이 비정상(빈 문자열, 숫자가 아님)이면
  등급을 매길 수 없으니 "측정 불가" 분기로 보냅니다. status만 믿고 넘어가면 batteryGrade가
  null인 채로 .label/.scenario에 접근해 화면이 깨집니다.
*/
const batteryRatioValue = computed(() => {
  if (capacityRatioItem.value?.status !== 'AVAILABLE') return null
  const raw = capacityRatioItem.value.value
  // Number('')는 0으로 변환되어 isFinite를 그냥 통과합니다. 빈 값은 여기서 먼저 걸러 냅니다.
  if (raw === null || raw === undefined || String(raw).trim() === '') return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
})

const hasBatteryHealth = computed(() => batteryRatioValue.value !== null)
const hasAnyBatteryInfo = computed(() => ALL_BATTERY_FIELDS
  .some((fieldName) => findDiagnosisItem(fieldName)?.status === 'AVAILABLE'))

// 등급 판정은 반드시 원본 값 기준입니다. 반올림한 값으로 판정하면 84.6%가 85%로 반올림되어
// "우수" 구간(85% 이상)에 걸쳐 있지도 않은데 우수로 뜨는 식의 오판정이 생깁니다.
const batteryGrade = computed(() => (
  batteryRatioValue.value === null ? null : batteryGradeFor(batteryRatioValue.value)
))

// 표시는 소수 첫째 자리까지 내림합니다. 반올림은 84.95%처럼 등급 경계(85) 바로 아래 값을
// "85.0%"로 보이게 만들어 배지(양호)와 숫자(85%)가 모순돼 보일 수 있습니다. 내림은 정수
// 경계에 대해 항상 안전합니다(x >= B이면 floor(x*10)/10 >= B가 수학적으로 보장됩니다).
const batteryRatioDisplay = computed(() => (
  batteryRatioValue.value === null ? null : (Math.floor(batteryRatioValue.value * 10) / 10).toFixed(1)
))

const BATTERY_TONE_BADGE_CLASSES = {
  excellent: 'bg-emerald-50 text-emerald-600',
  good: 'bg-sky-50 text-sky-600',
  caution: 'bg-amber-50 text-amber-600',
  replace: 'bg-rose-50 text-rose-600',
}
const BATTERY_TONE_BAR_CLASSES = {
  excellent: 'bg-emerald-500',
  good: 'bg-sky-500',
  caution: 'bg-amber-500',
  replace: 'bg-rose-500',
}

const batteryGradeBadgeClass = computed(() => BATTERY_TONE_BADGE_CLASSES[batteryGrade.value?.tone] || '')
const batteryGradeBarClass = computed(() => BATTERY_TONE_BAR_CLASSES[batteryGrade.value?.tone] || '')

// 건강도만 못 구했을 때 그나마 확보된 배터리 정보입니다. 실패한 필드까지 늘어놓으면
// "측정할 수 없습니다" 안내와 중복돼 오히려 헷갈리므로 성공한 값만 보여줍니다.
const batteryFallbackItems = computed(() => pickDiagnosisItems(BATTERY_FALLBACK_FIELDS)
  .filter((item) => item.status === 'AVAILABLE'))

const batteryDetailItems = computed(() => pickDiagnosisItems(BATTERY_DETAIL_FIELDS))

// 증빙 원본을 크게 보는 팝업입니다. 목록 안 썸네일은 56px이라 영상 재생에는 너무 작습니다.
const mediaViewer = ref(null)

// 상품 사진 원본. 위 4:3 틀이 사진을 잘라 채우기 때문에, 잘린 부분은 여기서만 보입니다.
const expandedImage = ref('')

/*
  크게 보기 창에서 좌우로 넘겨 봅니다.
  ---------------------------------------------------------------------------
  목록은 자료를 세 개까지만 보여 주고 나머지를 '+3'으로 접어 둡니다. 그 '+3'이 글자일
  뿐이라 눌러도 아무 일이 없었고, 크게 보기 창도 한 장만 띄우고 넘길 수 없었습니다.
  그래서 판매자가 사진을 여섯 장 올려도 구매자는 세 장까지만 볼 수 있었습니다.

  창이 그 항목의 자료 전체를 들고 있게 하고, 좌우로 넘기게 합니다. '+3'을 누르면 접혀
  있던 네 번째 자료부터 열립니다. 목록은 지금처럼 세 개만 두어 짧게 유지합니다.
*/
const mediaViewerIndex = ref(0)

const mediaViewerEvidence = computed(() => {
  const list = mediaViewer.value?.evidenceList || []
  return list[mediaViewerIndex.value] || null
})

function openMediaViewer(item, startIndex = 0) {
  mediaViewer.value = { itemName: item.name, evidenceList: item.evidence }
  mediaViewerIndex.value = startIndex
}

function moveMediaViewer(step) {
  const total = mediaViewer.value?.evidenceList?.length || 0
  if (total < 2) return
  // 끝에서 다음을 누르면 처음으로 돌아옵니다. 막다른 길에서 버튼이 죽어 있으면
  // 고장난 것처럼 보입니다.
  mediaViewerIndex.value = (mediaViewerIndex.value + step + total) % total
}

// 설명은 기본 4줄로 접어 두고, 길면 펼쳐 봅니다. 설명이 길어도 아래 검증 자료까지 한 화면에
// 들어오게 하려는 것입니다.
const isDescriptionExpanded = ref(false)

/*
  '더 보기'는 실제로 잘렸을 때만 띄웁니다.
  ---------------------------------------------------------------------------
  전에는 글자 수가 180자를 넘는지로 판단했는데, 자르는 쪽은 CSS가 네 줄을 넘는지로
  봅니다. 두 기준이 어긋나 짧지만 줄바꿈이 많은 글(80자·5줄)은 잘리는데 버튼이 안 나와,
  뒷부분을 볼 방법이 없었습니다.

  글자 수 대신 실제 높이를 재서 판단합니다. 화면 폭에 따라 줄 수가 달라지는 것까지
  그대로 반영됩니다.
*/
const descriptionEl = ref(null)
const isDescriptionClamped = ref(false)

function measureDescription() {
  const element = descriptionEl.value
  // 펼친 상태에서는 잘린 곳이 없으므로 재지 않습니다. 재면 false가 되어 '접기'가 사라집니다.
  if (!element || isDescriptionExpanded.value) return
  isDescriptionClamped.value = element.scrollHeight > element.clientHeight + 1
}
const isRecaptureModalOpen = ref(false)
const checkedItemIds = ref([])
const recaptureReason = ref('')
const isSubmittingRecapture = ref(false)
const recaptureError = ref('')
const recaptureSubmitted = ref(false)

async function openRecaptureModal() {
  if (!await requireLogin()) return
  checkedItemIds.value = []
  recaptureReason.value = ''
  recaptureError.value = ''
  recaptureSubmitted.value = false
  isRecaptureModalOpen.value = true
}

function toggleRecaptureItem(checklistItemId) {
  const index = checkedItemIds.value.indexOf(checklistItemId)
  if (index === -1) checkedItemIds.value.push(checklistItemId)
  else checkedItemIds.value.splice(index, 1)
}

async function submitRecaptureRequest() {
  recaptureError.value = ''
  if (!checkedItemIds.value.length) {
    recaptureError.value = '재촬영을 요청할 항목을 하나 이상 선택해 주세요.'
    return
  }
  if (!recaptureReason.value.trim()) {
    recaptureError.value = '어떤 부분이 궁금한지 자유롭게 적어 주세요.'
    return
  }

  isSubmittingRecapture.value = true
  try {
    await createReinspectionRequest(product.value.productId, {
      reason: recaptureReason.value.trim(),
      items: checkedItemIds.value.map((checklistItemId) => ({
        checklistItemId,
        requestContent: recaptureReason.value.trim(),
      })),
    })
    recaptureSubmitted.value = true
  } catch (error) {
    recaptureError.value = error.message || '재촬영 요청을 보내지 못했습니다.'
  } finally {
    isSubmittingRecapture.value = false
  }
}

function formatPrice(price) {
  return Number(price || 0).toLocaleString('ko-KR')
}

/*
  이 매물에 몰린 관심도 셋.
  ---------------------------------------------------------------------------
  조회수는 listing.view_count, 좋아요는 wishlist, 문의는 chat_room의 행 수입니다.
  서버가 아직 안 내려주는 상황(구버전 배포)에서도 0으로 떨어지게 두어, 숫자 자리가
  비거나 undefined가 그대로 찍히지 않게 합니다.
*/
const engagementStats = computed(() => [
  { label: '조회', value: Number(product.value?.viewCount || 0).toLocaleString('ko-KR') },
  { label: '좋아요', value: Number(product.value?.favoriteCount || 0).toLocaleString('ko-KR') },
  { label: '문의', value: Number(product.value?.chatRoomCount || 0).toLocaleString('ko-KR') },
])

async function requireLogin() {
  if (getAccessToken()) return true
  await router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function toggleFavorite() {
  if (!await requireLogin()) return
  isUpdatingFavorite.value = true
  errorMessage.value = ''
  try {
    if (isFavorite.value) await removeFavorite(product.value.productId)
    else await addFavorite(product.value.productId)
    isFavorite.value = !isFavorite.value
  } catch (error) {
    errorMessage.value = error.message || '좋아요한 상품 상태를 변경하지 못했습니다.'
  } finally {
    isUpdatingFavorite.value = false
  }
}

// 직거래로 팔린 매물을 판매자가 직접 닫습니다. 구매자 화면에는 이 버튼이 보이지 않습니다.
const isMarkingSold = ref(false)
const canMarkSold = computed(
  () => isOwner.value && canSellerMarkSold(product.value?.status),
)

async function markSold() {
  const confirmed = window.confirm(
    '판매 완료로 바꿀까요?\n\n'
    + '구매자에게 더 이상 노출되지 않습니다. 거래가 무산되면 다시 판매 중으로 되돌릴 수 있습니다.',
  )
  if (!confirmed) return
  isMarkingSold.value = true
  errorMessage.value = ''
  try {
    await transitionProductStatus(product.value.productId, 'SOLD', '판매자 직거래 판매 완료')
    await loadProduct(product.value.productId)
  } catch (error) {
    errorMessage.value = error.message || '판매 완료로 처리하지 못했습니다.'
  } finally {
    isMarkingSold.value = false
  }
}

// 직거래 약속이 깨졌을 때 원래 판매글로 돌아갑니다.
const canReopen = computed(() => isOwner.value && canSellerReopen(product.value?.status))

async function reopen() {
  isMarkingSold.value = true
  errorMessage.value = ''
  try {
    await transitionProductStatus(product.value.productId, 'ON_SALE', '거래 파기로 판매 재개')
    await loadProduct(product.value.productId)
  } catch (error) {
    errorMessage.value = error.message || '판매 중으로 되돌리지 못했습니다.'
  } finally {
    isMarkingSold.value = false
  }
}

// 1:1 영상 확인은 상세 화면의 세 번째 버튼으로 두지 않습니다. 구매·문의 두 갈래만 남기고,
// 영상 요청은 '판매자에게 문의하기'로 연결되는 채팅방 안에서 하도록 동선을 모았습니다.
async function openChat() {
  if (!await requireLogin()) return
  isOpeningChat.value = true
  errorMessage.value = ''
  try {
    const room = await createOrGetChatRoom(product.value.productId)
    await router.push({ name: 'chat', params: { roomId: room.roomId } })
  } catch (error) {
    errorMessage.value = error.message || '채팅방을 열지 못했습니다.'
  } finally {
    isOpeningChat.value = false
  }
}

// 공개 상세 API는 ON_SALE 상품만 반환합니다. 판매자가 자기 초안·숨김 상품을 열었을 때는
// 소유자 전용 조회로 한 번 더 시도해서 등록 직후 상세 확인이 끊기지 않게 합니다.
async function loadProduct(productId) {
  try {
    product.value = await getProduct(productId)
  } catch (error) {
    if (!getAccessToken()) throw error
    try {
      product.value = await getMyProduct(productId)
      loadedViaOwnerApi.value = true
    } catch {
      // 소유자도 아니면 공개 조회 실패 사유를 그대로 보여줍니다.
      throw error
    }
  }
}

// 화면을 가득 채운 사진에서는 어디를 눌러야 닫히는지 알기 어려워 Esc도 받습니다.
function closeViewersOnEscape({ key: pressed }) {
  // 여러 장을 넘겨 보는 창에서는 좌우 화살표도 받습니다. 사진을 훑을 때 마우스를
  // 버튼까지 옮기는 것보다 짧습니다.
  if (mediaViewer.value && !expandedImage.value) {
    if (pressed === 'ArrowLeft') return moveMediaViewer(-1)
    if (pressed === 'ArrowRight') return moveMediaViewer(1)
  }
  if (pressed !== 'Escape') return
  if (expandedImage.value) expandedImage.value = ''
  else if (mediaViewer.value) mediaViewer.value = null
}

onBeforeUnmount(() => {
  window.removeEventListener('keydown', closeViewersOnEscape)
  window.removeEventListener('resize', measureDescription)
})

// 설명이 들어오면 그린 뒤에 잽니다. 창 폭이 바뀌면 줄 수도 달라지므로 다시 잽니다.
watch(() => product.value?.description, async () => {
  await nextTick()
  measureDescription()
})

onMounted(async () => {
  window.addEventListener('keydown', closeViewersOnEscape)
  window.addEventListener('resize', measureDescription)
  try {
    await loadProduct(route.params.productId)
    try {
      productImages.value = await getProductImages(route.params.productId)
      activeImageUrl.value = productImages.value.find((image) => image.imageType === 'THUMBNAIL')?.imageUrl
        || productImages.value[0]?.imageUrl
        || product.value.thumbnailUrl
        || ''
    } catch {
      productImages.value = []
      activeImageUrl.value = product.value.thumbnailUrl || ''
    }
    // 판매자 프로필은 곁들이는 정보입니다. 실패해도 상품 화면 자체는 그대로 보여 줍니다.
    if (product.value?.sellerId) {
      try {
        sellerProfile.value = await getSellerProfile(product.value.sellerId)
      } catch {
        sellerProfile.value = null
      }
    }
    if (getAccessToken()) {
      const favoriteStatus = await getFavoriteStatus(route.params.productId)
      isFavorite.value = Boolean(favoriteStatus?.favorite)
    }
    try {
      checklistItems.value = await getProductChecklist(product.value.productId)
      const histories = await Promise.all(checklistItems.value.map(async (item) => [
        item.checklistItemId,
        await getEvidenceHistory(product.value.productId, item.checklistItemId),
      ]))
      evidenceByChecklistItem.value = Object.fromEntries(histories)
    } catch {
      checklistItems.value = []
      evidenceByChecklistItem.value = {}
    }
    try {
      const summary = await getProductDiagnosisSummary(product.value.productId)
      // 사운드 장치는 중고 기기를 고를 때 판단에 쓰이지 않아 목록에서 뺍니다. 서버는 계속
      // 내려주지만(다른 화면에서 쓰일 수 있어) 구매자 화면에서만 감춥니다.
      diagnosisSummaryItems.value = (summary.items || [])
        .filter((item) => item.fieldName !== 'SOUND_DEVICE')
      diagnosisDisclaimer.value = summary.disclaimer || ''
    } catch {
      diagnosisSummaryItems.value = []
      diagnosisDisclaimer.value = ''
    }
  } catch (error) {
    errorMessage.value = error.message || '상품을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <DefaultLayout>
    <main class="page-shell">
      <nav
        class="mb-6 flex items-center gap-2 text-xs text-text-sub"
        aria-label="현재 위치"
      >
        <RouterLink
          to="/"
          class="hover:text-primary"
        >
          홈
        </RouterLink>
        <span>/</span>
        <RouterLink
          to="/products"
          class="hover:text-primary"
        >
          전체 상품
        </RouterLink>
        <template v-if="product">
          <span>/</span>
          <span class="max-w-48 truncate text-text-main">{{ product.name }}</span>
        </template>
      </nav>

      <p
        v-if="isOwner && !canPurchase"
        role="status"
        class="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-md bg-accent px-4 py-3 text-sm text-primary-dark"
      >
        임시 저장 중인 상품이라 다른 사용자에게는 보이지 않는 화면입니다.
        <RouterLink
          :to="{ name: 'seller-products' }"
          class="font-semibold text-primary hover:underline"
        >
          상품 관리로 이동
        </RouterLink>
      </p>

      <div
        v-if="isLoading"
        class="grid animate-pulse gap-10 lg:grid-cols-[1.08fr_0.92fr]"
      >
        <div class="aspect-[4/3] rounded-lg bg-slate-100" />
        <div class="space-y-5 pt-3">
          <div class="h-4 w-2/5 rounded bg-slate-100" />
          <div class="h-8 w-4/5 rounded bg-slate-100" />
          <div class="h-8 w-1/2 rounded bg-slate-100" />
          <div class="h-40 rounded-lg bg-slate-100" />
        </div>
      </div>

      <div
        v-else-if="!product"
        class="card-soft rounded-lg bg-surface px-6 py-20 text-center"
      >
        <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600">
          !
        </div>
        <h1 class="mt-4 text-lg font-bold text-text-main">
          상품을 찾을 수 없습니다.
        </h1>
        <p
          role="alert"
          class="mt-2 text-sm text-red-700"
        >
          {{ errorMessage }}
        </p>
        <BaseButton
          class="mt-6"
          variant="outline"
          to="/products"
        >
          목록으로 돌아가기
        </BaseButton>
      </div>

      <template v-else>
        <!-- 상단: 사진을 크게 보고, 옆에서 바로 살 수 있게 둡니다. -->
        <!--
          첫 줄에 대표 사진과 오른쪽 정보를, 둘째 줄에 추가 사진 목록을 둡니다.
          -------------------------------------------------------------------------
          추가 사진 줄을 왼쪽 칸 안에 두면 그 높이까지 첫 줄에 더해져, 오른쪽 정보가
          사진 바닥이 아니라 썸네일 바닥까지 늘어납니다. 줄을 나누면 오른쪽 열의
          높이가 대표 사진 하나에 맞춰집니다.
        -->
        <div class="grid gap-x-8 gap-y-3 lg:grid-cols-[6fr_4fr]">
          <section aria-label="상품 이미지">
            <!--
              4:3을 그대로 두면 넓은 화면에서 사진 높이가 600px를 넘어, 오른쪽 정보 카드가 끝난
              아래로 빈 공간이 크게 생깁니다. 최대 높이를 두어 설명이 화면 안으로 올라오게 합니다.
            -->
            <div class="relative flex aspect-[4/3] max-h-[460px] items-center justify-center overflow-hidden rounded-md bg-slate-50">
              <!--
                눌러서 잘리지 않은 원본을 봅니다.
                ---------------------------------------------------------------
                4:3 틀에 object-cover로 채우기 때문에 세로로 긴 사진은 위아래가,
                가로로 긴 사진은 좌우가 잘립니다. 구매자가 기기 상태를 보러 온
                화면인데 잘린 부분을 확인할 방법이 없었습니다.
              -->
              <button
                v-if="activeImageUrl"
                type="button"
                class="h-full w-full cursor-zoom-in"
                aria-label="상품 이미지 확대 보기"
                @click="expandedImage = activeImageUrl"
              >
                <img
                  :src="activeImageUrl"
                  :alt="product.name"
                  class="h-full w-full object-cover"
                >
              </button>
              <div
                v-else
                class="flex flex-col items-center text-slate-300"
              >
                <span class="text-6xl">▣</span>
                <span class="mt-3 text-sm">등록된 상품 이미지가 없습니다.</span>
              </div>
              <!-- 상태 배지는 이미지 위에 두지 않습니다. 사진을 가리고, 구매 버튼이 이미 상태를 말해 줍니다. -->
              <div
                v-if="isSoldOut(product.status)"
                class="absolute inset-0 flex items-center justify-center bg-black/55"
              >
                <span class="text-xl font-bold text-white">판매 완료</span>
              </div>
            </div>
          </section>

          <div class="space-y-4">
            <!--
              바깥 카드는 두지 않습니다. 사진 옆이라 테두리를 한 겹 더 두르면 답답해 보이고,
              안에 판매자 카드가 또 들어가 상자가 겹칩니다.

              위에서부터 차례로 쌓습니다. 아래로 밀어 사진 바닥에 맞춰 본 적이 있는데,
              제목·가격과 판매자 사이가 크게 벌어져 오른쪽 열이 두 조각으로 읽혔습니다.
              사진보다 짧아 남는 자리는 그대로 둡니다.
            -->
            <section>
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <!-- 목록 카드와 같은 줄입니다. 한쪽만 회색이면 같은 정보가 화면마다 달라 보입니다. -->
                  <p class="truncate text-sm font-semibold text-primary">
                    {{ product.device?.manufacturer || '제조사 미등록' }}
                    · {{ product.device?.model || '모델 미등록' }}
                  </p>
                  <h1 class="mt-2 text-3xl font-bold leading-tight tracking-tight text-text-main">
                    {{ product.name }}
                  </h1>
                </div>
                <button
                  type="button"
                  class="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-md border text-xl leading-none transition"
                  :class="isFavorite
                    ? 'favorite-button--on border-primary bg-accent text-primary'
                    : 'border-border bg-surface text-text-sub hover:border-primary hover:text-primary'"
                  :disabled="isUpdatingFavorite"
                  :aria-label="isFavorite ? '좋아요한 상품 해제' : '좋아요한 상품 등록'"
                  @click="toggleFavorite"
                >
                  {{ isFavorite ? '♥' : '♡' }}
                </button>
              </div>
              <p class="mt-5 text-3xl font-bold text-text-main">
                {{ formatPrice(product.price) }}원
              </p>

              <!--
              가격 바로 아래에 판매자를 둡니다. 누구에게 사는지가 수치보다 먼저 읽혀야 합니다.
              누르면 그 판매자의 판매 목록으로 갑니다.
              판매 중 개수는 여기서 보여주지 않습니다 — 이 화면의 관심은 '이 상품'이고, 판매자의
              재고 규모는 프로필 페이지에서 볼 내용입니다.
              정산 계좌 같은 값은 공개 프로필에 담기지 않습니다.
            -->
              <div class="mt-6">
                <RouterLink
                  v-if="sellerProfile"
                  :to="{ name: 'seller-profile', params: { sellerId: sellerProfile.sellerId } }"
                  class="card-soft card-soft--hover flex items-center gap-3 rounded-lg bg-surface p-3"
                >
                  <ProfileAvatar
                    :src="sellerProfile.profileImageUrl"
                    :name="sellerProfile.nickname"
                  />
                  <!--
                    개인/사업자 구분은 빼 두었습니다. 구매자가 이 화면에서 판단하는
                    것은 "누구에게 사는가"이고, 사업자 여부는 그 이름을 눌러 들어간
                    판매자 페이지에서 확인할 값입니다.
                  -->
                  <span class="min-w-0 flex-1 truncate text-sm font-semibold text-text-main">
                    {{ sellerProfile.nickname }}
                  </span>
                  <span class="shrink-0 text-xs font-semibold text-primary">판매자 상품 보기 →</span>
                </RouterLink>
                <p
                  v-else
                  class="text-xs text-text-sub"
                >
                  판매자 정보를 불러오지 못했습니다.
                </p>

                <!--
                  색상·저장 용량이 있던 자리입니다.
                  ---------------------------------------------------------------
                  등록 화면에서 두 값을 더 이상 받지 않아 계속 '미입력'으로 남았습니다.
                  대신 이 매물에 얼마나 관심이 몰렸는지를 보여 줍니다. 중고 거래에서는
                  기기 옵션보다 "다른 사람도 보고 있나"가 사는 판단에 더 붙습니다.
                  저장 용량은 아래 '자동 인식 사양'에서 검수 결과로 보여 줍니다.
                -->
                <dl class="mt-6 grid grid-cols-3 border-t border-border pt-5">
                  <!--
                    가운데 정렬입니다. 왼쪽 정렬로 두면 세 값이 왼쪽 2/3에 몰리고
                    오른쪽 1/3이 비어, 줄 전체가 왼쪽으로 쏠려 보입니다.
                    칸을 셋으로 똑같이 나눠 두면 세로선 없이도 각자 한 칸으로 읽힙니다.
                  -->
                  <div
                    v-for="stat in engagementStats"
                    :key="stat.label"
                    class="text-center"
                  >
                    <dt class="text-xs text-text-sub">
                      {{ stat.label }}
                    </dt>
                    <dd class="mt-1 text-base font-bold text-text-main">
                      {{ stat.value }}
                    </dd>
                  </div>
                </dl>

                <!-- 구매자 쪽과 같이 한 줄에 하나씩 쌓습니다. 폭이 같아 눌 곳이 분명합니다. -->
                <div
                  v-if="isOwner"
                  class="mt-5 space-y-3"
                >
                  <BaseButton
                    block
                    :to="{ name: 'seller-product-edit', params: { productId: product.productId } }"
                  >
                    수정하기
                  </BaseButton>
                  <!-- 서비스 결제를 거치지 않은 직거래를 정리하는 버튼입니다. -->
                  <BaseButton
                    v-if="canMarkSold"
                    block
                    variant="outline"
                    :disabled="isMarkingSold"
                    @click="markSold"
                  >
                    {{ isMarkingSold ? '처리 중…' : '판매 완료 처리하기' }}
                  </BaseButton>
                  <!-- 직거래가 깨졌을 때 상품을 새로 등록하지 않고 이 글로 돌아옵니다. -->
                  <BaseButton
                    v-if="canReopen"
                    block
                    variant="outline"
                    :disabled="isMarkingSold"
                    @click="reopen"
                  >
                    {{ isMarkingSold ? '처리 중…' : '다시 판매하기' }}
                  </BaseButton>
                </div>

                <div
                  v-else
                  class="mt-5 space-y-3"
                >
                  <!-- 구매하기와 문의하기 두 개만 둡니다. 영상 확인은 채팅방 안에서 요청합니다. -->
                  <BaseButton
                    block
                    :to="canPurchase ? { name: 'purchase', params: { productId: product.productId } } : ''"
                    :disabled="!canPurchase"
                  >
                    {{ purchaseButtonLabel }}
                  </BaseButton>
                  <BaseButton
                    block
                    variant="outline"
                    :disabled="isOpeningChat"
                    @click="openChat"
                  >
                    {{ isOpeningChat ? '채팅방 여는 중…' : '판매자에게 문의하기' }}
                  </BaseButton>
                </div>
              </div>

              <p
                v-if="errorMessage"
                role="alert"
                class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
              >
                {{ errorMessage }}
              </p>
            </section>
          </div>

          <!-- 추가 사진은 둘째 줄 왼쪽 칸입니다. 대표 사진 바로 아래에 붙습니다. -->
          <ul
            v-if="productImages.length > 1"
            class="grid grid-cols-5 gap-2 lg:col-start-1"
            aria-label="상품 추가 이미지"
          >
            <li
              v-for="image in productImages"
              :key="image.imageId"
            >
              <button
                type="button"
                class="aspect-square w-full overflow-hidden rounded-md border"
                :class="activeImageUrl === image.imageUrl ? 'border-primary' : 'border-border'"
                @click="activeImageUrl = image.imageUrl"
              >
                <img
                  :src="image.imageUrl"
                  :alt="`${product.name} 추가 이미지`"
                  class="h-full w-full object-cover"
                >
              </button>
            </li>
          </ul>
        </div>

        <!-- 중단: 설명은 테두리 없이 두되 한 줄이 너무 길지 않게 폭을 제한합니다. -->
        <div class="mt-8 max-w-3xl">
          <section>
            <!--
              판매자가 직접 쓴 글이라 이 화면에서 가장 오래 읽는 부분입니다.
              옆 사양표와 같은 크기로 두면 훑어보는 값과 읽는 글이 구분되지 않아,
              한 단계씩 키우고 줄 간격도 함께 넓혔습니다.
            -->
            <h2 class="text-lg font-bold text-text-main">
              상품 설명
            </h2>
            <!--
              본문은 text-main입니다. 회색(text-sub)으로 두면 흰 바탕에서 대비가
              4.6:1까지 떨어져 길게 읽기 어렵습니다. 판매자가 직접 쓴 글이고 이
              화면에서 가장 오래 읽는 부분이라 본문 색으로 둡니다.
            -->
            <p
              ref="descriptionEl"
              class="mt-3 whitespace-pre-wrap text-base leading-7 text-text-main"
              :class="isDescriptionExpanded ? '' : 'line-clamp-4'"
            >
              {{ product.description || '판매자가 등록한 상세 설명이 없습니다.' }}
            </p>
            <button
              v-if="isDescriptionClamped || isDescriptionExpanded"
              type="button"
              class="mt-2 text-sm font-semibold text-primary hover:underline"
              @click="isDescriptionExpanded = !isDescriptionExpanded"
            >
              {{ isDescriptionExpanded ? '접기' : '더 보기' }}
            </button>
          </section>
        </div>

        <!-- 하단: 검증 자료와 인식된 사양을 나란히 두어 세로 길이를 줄입니다. -->
        <div class="mt-10 grid items-start gap-8 lg:grid-cols-2">
          <section class="card-soft rounded-lg bg-surface p-5 sm:p-6">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p class="text-xs font-semibold text-primary">
                  검증 체크리스트
                </p>
                <h2 class="mt-1 text-lg font-bold text-text-main">
                  판매글에 등록된 검증 항목
                </h2>
                <p class="mt-1 text-xs text-text-sub">
                  판매자가 등록할 때 사용한 항목과 동일한 목록입니다.
                </p>
              </div>
              <!-- 목록 카드와 같은 모양으로 둡니다. 같은 값을 화면마다 다르게 읽지 않도록. -->
              <div class="shrink-0 text-right">
                <span class="rounded-pill bg-accent px-2.5 py-1 text-xs font-bold text-primary">
                  {{ buyerChecklistProgress.completed }}/{{ buyerChecklistProgress.required }}
                </span>
              </div>
            </div>
            <div class="mt-4 h-2 overflow-hidden rounded-pill bg-slate-100">
              <div
                class="h-full rounded-pill bg-primary-gradient"
                :style="{ width: `${checklistRate}%` }"
              />
            </div>
            <p
              v-if="checklist.recaptureRequested"
              class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700"
            >
              추가 확인이 필요한 항목이 {{ checklist.recaptureRequested }}개 있습니다.
            </p>

            <!--
              항목을 빠뜨리지 않고 전부 보여주되 화면이 끝없이 길어지지 않게, 5개 높이만 열어 두고
              나머지는 스크롤로 봅니다. 행 높이(5rem)와 간격(0.5rem)을 고정해야 5개에서 잘립니다.
            -->
            <!--
              항목마다 상자를 두면 테두리가 겹쳐 보여 지저분합니다. 하나의 카드 안에서 구분선으로만
              나눕니다.
            -->
            <ul
              v-if="buyerChecklist.length"
              class="checklist-card mt-4 max-h-[27.5rem] overflow-y-auto"
              aria-label="검증 체크리스트 항목"
            >
              <li
                v-for="item in buyerChecklist"
                :key="item.checklistItemId"
                class="checklist-row flex h-20 items-center gap-3"
              >
                <span
                  class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold"
                  :class="item.completed ? 'bg-primary text-white' : 'bg-slate-200 text-text-sub'"
                  :aria-label="item.completed ? '확인 완료' : '미확인'"
                >
                  {{ item.completed ? '✓' : '·' }}
                </span>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-semibold text-text-main">
                    {{ item.name }}<span
                      v-if="item.required"
                      class="ml-0.5 text-red-500"
                    >*</span>
                  </p>
                  <p class="mt-0.5 text-xs text-text-sub">
                    {{ evidenceTypeLabel(item.evidenceType) }}
                    · {{ item.completed ? '판매자 확인 완료' : '자료 준비 중' }}
                  </p>
                </div>
                <ul
                  v-if="item.evidence.length"
                  class="flex shrink-0 items-center gap-1.5"
                >
                  <li
                    v-for="(evidence, index) in item.evidence.slice(0, 3)"
                    :key="evidence.evidenceId"
                  >
                    <button
                      type="button"
                      class="relative block h-14 w-14 overflow-hidden rounded-md border border-border bg-white"
                      :aria-label="`${item.name} 검증 자료 크게 보기`"
                      @click="openMediaViewer(item, index)"
                    >
                      <img
                        v-if="evidence.evidenceType === 'PHOTO'"
                        :src="evidence.mediaUrl"
                        alt=""
                        class="h-full w-full object-cover"
                      >
                      <video
                        v-else-if="evidence.evidenceType === 'VIDEO'"
                        :src="evidence.mediaUrl"
                        preload="metadata"
                        muted
                        class="h-full w-full object-cover"
                      />
                      <span
                        v-else
                        class="flex h-full w-full items-center justify-center text-[10px] font-semibold text-primary"
                      >파일</span>
                      <span
                        v-if="evidence.evidenceType === 'VIDEO'"
                        class="absolute inset-0 flex items-center justify-center bg-black/35 text-sm text-white"
                      >▶</span>
                    </button>
                  </li>
                  <li v-if="item.evidence.length > 3">
                    <button
                      type="button"
                      class="rounded-md px-1.5 py-1 text-xs font-semibold text-primary hover:bg-accent"
                      :aria-label="`${item.name} 검증 자료 ${item.evidence.length - 3}개 더 보기`"
                      @click="openMediaViewer(item, 3)"
                    >
                      +{{ item.evidence.length - 3 }}
                    </button>
                  </li>
                </ul>
                <span
                  v-else
                  class="shrink-0 text-xs text-text-sub"
                >자료 없음</span>
              </li>
            </ul>
            <p
              v-else
              class="mt-4 rounded-md bg-bg px-4 py-8 text-center text-sm text-text-sub"
            >
              공개된 검증 항목이 없습니다.
            </p>

            <!--
              카드 안에 카드를 또 두지 않고 구분선으로만 나눕니다.
              내 상품에서는 통째로 감춥니다 — 자기 자신에게 재촬영을 요청할 일이 없어,
              안내 문구만 남으면 빈 상자처럼 보입니다.
            -->
            <div
              v-if="!isOwner"
              class="mt-5 flex flex-wrap items-center justify-between gap-2 border-t border-border pt-4"
            >
              <p class="min-w-0 flex-1 text-xs text-text-sub">
                자료가 부족하면 판매자에게 다시 찍어 달라고 요청할 수 있습니다.
              </p>
              <BaseButton
                class="shrink-0 px-3 py-1.5 text-sm"
                variant="outline"
                @click="openRecaptureModal"
              >
                재촬영 요청
              </BaseButton>
            </div>
          </section>

          <!-- ocr_result/dxdiag_result/battery_report_result 취합값. 항목별 자료 첨부 여부와는
               별개로, 실제로 인식된 사양 값 자체를 그룹 단위로 보여줍니다. -->
          <div
            v-if="diagnosisSummaryItems.length"
            class="card-soft rounded-lg bg-surface p-5 sm:p-6"
          >
            <!-- 옆 검증 카드와 같은 3단 머리글(작은 파란 라벨 → 굵은 제목 → 설명)로 맞춥니다. -->
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p class="text-xs font-semibold text-primary">
                  사양 체크리스트
                </p>
                <h2 class="mt-1 text-lg font-bold text-text-main">
                  자동 인식된 사양
                </h2>
                <p
                  v-if="diagnosisDisclaimer"
                  class="mt-1 text-xs text-text-sub"
                >
                  {{ diagnosisDisclaimer }}
                </p>
              </div>
              <!--
                옆 검증 카드와 같은 배지·막대를 씁니다. 다만 이 숫자는 판매자가 채운 양이 아니라
                파일에서 읽어낸 양입니다. 낮다고 상품이 나쁜 게 아니라서 라벨로 구분해 둡니다.
              -->
              <div class="shrink-0 text-right">
                <span class="rounded-pill bg-accent px-2.5 py-1 text-xs font-bold text-primary">
                  {{ diagnosisProgress.available }}/{{ diagnosisProgress.total }}
                </span>
              </div>
            </div>
            <div class="mt-4 h-2 overflow-hidden rounded-pill bg-slate-100">
              <div
                class="h-full rounded-pill bg-primary-gradient"
                :style="{ width: `${diagnosisRate}%` }"
              />
            </div>

            <!-- 기본 정보: 이게 무슨 기기인지부터 보여줍니다. -->
            <section
              v-if="basicInfoItems.length"
              class="mt-5"
            >
              <h3 class="text-xs font-semibold text-text-sub">
                기본 정보
              </h3>
              <DiagnosisSpecList :items="basicInfoItems" />
            </section>

            <!--
              배터리 성능: 원시 수치 나열 대신 "믿고 써도 되는가"에 답하는 카드입니다.
              건강도(용량 비율)를 못 구한 경우까지 감안해 3단계로 나눕니다.
            -->
            <section class="mt-5">
              <h3 class="text-xs font-semibold text-text-sub">
                배터리 성능
              </h3>

              <div
                v-if="hasBatteryHealth"
                class="mt-2 rounded-lg border border-slate-100 bg-slate-50 p-4"
              >
                <div class="flex flex-wrap items-baseline gap-2">
                  <span class="text-2xl font-bold text-text-main">{{ batteryRatioDisplay }}%</span>
                  <span
                    class="rounded-pill px-2.5 py-1 text-xs font-bold"
                    :class="batteryGradeBadgeClass"
                  >
                    {{ batteryGrade.label }}
                  </span>
                </div>
                <p class="mt-3 text-sm text-text-sub">
                  {{ batteryGrade.scenario }}
                </p>
                <!-- 처음에는 구매 판단에 필요한 등급·한 줄 안내만 두고, 근거 수치와 설명은
                     필요할 때만 열어 보게 합니다. 기본 상태는 닫혀 있습니다. -->
                <details class="group mt-3 border-t border-slate-200 pt-3">
                  <summary class="flex cursor-pointer list-none items-center justify-between gap-3 text-xs font-semibold text-primary">
                    <span>배터리 상세 정보</span>
                    <span>
                      <span class="group-open:hidden">펼치기</span>
                      <span class="hidden group-open:inline">접기</span>
                    </span>
                  </summary>
                  <div class="mt-3">
                    <!-- 등급 4단계는 넓은 구간을 가진 것도 있어, 구간 안 위치를 막대로 함께 보여줍니다. -->
                    <div class="h-2 overflow-hidden rounded-pill bg-slate-200">
                      <div
                        class="h-full rounded-pill"
                        :class="batteryGradeBarClass"
                        :style="{ width: `${batteryRatioDisplay}%` }"
                      />
                    </div>
                    <p
                      v-if="cycleCountItem?.status === 'AVAILABLE'"
                      class="mt-3 text-xs text-text-sub"
                    >
                      충전 사이클 {{ diagnosisFieldValue('CYCLE_COUNT', cycleCountItem.value) }} · {{ CYCLE_COUNT_DISCLAIMER }}
                    </p>
                    <p class="mt-3 text-xs text-text-sub">
                      {{ BATTERY_GRADE_DISCLAIMER }}
                    </p>
                    <p class="mt-1 text-xs text-text-sub">
                      {{ BATTERY_GRADE_BASIS }}
                    </p>
                    <div
                      v-if="batteryDetailItems.length"
                      class="mt-3"
                    >
                      <p class="text-xs font-semibold text-text-sub">
                        용량 상세
                      </p>
                      <DiagnosisSpecList :items="batteryDetailItems" />
                    </div>
                  </div>
                </details>
              </div>

              <div
                v-else-if="hasAnyBatteryInfo"
                class="mt-2 rounded-lg border border-slate-100 bg-slate-50 p-4"
              >
                <p class="text-sm font-semibold text-text-main">
                  {{ BATTERY_UNMEASURABLE_TITLE }}
                </p>
                <p class="mt-1 text-xs text-text-sub">
                  {{ BATTERY_UNMEASURABLE_DESCRIPTION }}
                </p>
                <DiagnosisSpecList :items="batteryFallbackItems" />
              </div>

              <div
                v-else
                class="mt-2 rounded-lg border border-slate-100 bg-slate-50 p-4"
              >
                <p class="text-sm font-semibold text-slate-400">
                  {{ BATTERY_UNAVAILABLE_TITLE }}
                </p>
                <p class="mt-1 text-xs text-text-sub">
                  {{ BATTERY_UNAVAILABLE_DESCRIPTION }}
                </p>
              </div>
            </section>

            <!-- 그래픽·장치 정보: 구매자가 우선순위 낮게 보는 값들이라 배터리 뒤로 둡니다. -->
            <section
              v-if="graphicsDeviceItems.length"
              class="mt-5"
            >
              <h3 class="text-xs font-semibold text-text-sub">
                그래픽·장치 정보
              </h3>
              <DiagnosisSpecList :items="graphicsDeviceItems" />
            </section>
          </div>
        </div>

        <!--
          상품 사진 원본 팝업.
          -------------------------------------------------------------------------
          위 4:3 틀은 사진을 잘라 채웁니다. 여기서는 object-contain으로 잘리지 않은
          전체를 보여 줍니다. 바깥을 누르거나 Esc로 닫습니다.
        -->
        <div
          v-if="expandedImage"
          role="dialog"
          aria-modal="true"
          aria-label="상품 이미지 원본"
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
            :alt="product.name"
            class="max-h-full max-w-full rounded-lg object-contain shadow-2xl"
          >
        </div>

        <!-- 증빙 원본 팝업. 목록 썸네일이 작아서 영상은 여기서 재생합니다.
             툭 나타나면 놀라기 때문에 짧게 밝아지며 올라오게 합니다. -->
        <Transition name="viewer">
          <div
            v-if="mediaViewer"
            class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4"
            @click.self="mediaViewer = null"
          >
            <div class="viewer__panel w-full max-w-2xl overflow-hidden rounded-lg bg-surface">
              <div class="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
                <p class="truncate text-sm font-bold text-text-main">
                  {{ mediaViewer.itemName }}
                  <span
                    v-if="mediaViewer.evidenceList.length > 1"
                    class="ml-1 font-normal text-text-sub"
                  >{{ mediaViewerIndex + 1 }} / {{ mediaViewer.evidenceList.length }}</span>
                </p>
                <button
                  type="button"
                  class="rounded-md px-2 py-1 text-sm text-text-sub hover:bg-bg"
                  aria-label="검증 자료 닫기"
                  @click="mediaViewer = null"
                >
                  닫기
                </button>
              </div>
              <div class="relative flex max-h-[70vh] items-center justify-center bg-black">
                <video
                  v-if="mediaViewerEvidence?.evidenceType === 'VIDEO'"
                  :key="`video-${mediaViewerEvidence.evidenceId}`"
                  :src="mediaViewerEvidence.mediaUrl"
                  controls
                  autoplay
                  class="viewer__media max-h-[70vh] w-full"
                />
                <img
                  v-else-if="mediaViewerEvidence?.evidenceType === 'PHOTO'"
                  :key="`photo-${mediaViewerEvidence.evidenceId}`"
                  :src="mediaViewerEvidence.mediaUrl"
                  :alt="`${mediaViewer.itemName} 검증 자료`"
                  class="viewer__media max-h-[70vh] w-full object-contain"
                >
                <a
                  v-else-if="mediaViewerEvidence"
                  :href="mediaViewerEvidence.mediaUrl"
                  class="block px-4 py-16 text-sm font-semibold text-white underline"
                >
                  검수 파일 내려받기
                </a>

                <!-- 자료가 두 개 이상일 때만 좌우 버튼을 둡니다. 한 장뿐인데 버튼이 있으면
                   누를 곳처럼 보여 혼란을 줍니다. -->
                <template v-if="mediaViewer.evidenceList.length > 1">
                  <button
                    type="button"
                    class="absolute left-2 flex h-10 w-10 items-center justify-center rounded-full bg-black/50 text-lg text-white hover:bg-black/70"
                    aria-label="이전 자료"
                    @click="moveMediaViewer(-1)"
                  >
                    ‹
                  </button>
                  <button
                    type="button"
                    class="absolute right-2 flex h-10 w-10 items-center justify-center rounded-full bg-black/50 text-lg text-white hover:bg-black/70"
                    aria-label="다음 자료"
                    @click="moveMediaViewer(1)"
                  >
                    ›
                  </button>
                </template>
              </div>
            </div>
          </div>
        </Transition>

        <div
          v-if="isRecaptureModalOpen"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
        >
          <BaseCard class="w-full max-w-md">
            <template v-if="!recaptureSubmitted">
              <h2 class="text-lg font-bold text-text-main">
                어떤 항목을 재촬영해 주셨으면 하나요?
              </h2>
              <p class="mt-1 text-sm text-text-sub">
                궁금한 항목을 선택하고, 어떤 부분이 더 잘 보였으면 하는지 자유롭게 적어 주세요.
              </p>

              <ul class="mt-5 max-h-48 space-y-2 overflow-y-auto">
                <li
                  v-for="item in buyerChecklistItems"
                  :key="item.checklistItemId"
                >
                  <label class="flex cursor-pointer items-center gap-3 rounded-md border border-border px-3 py-2.5 text-sm">
                    <input
                      type="checkbox"
                      class="h-4 w-4 rounded border-border"
                      :checked="checkedItemIds.includes(item.checklistItemId)"
                      @change="toggleRecaptureItem(item.checklistItemId)"
                    >
                    <span class="font-semibold text-text-main">{{ item.name }}</span>
                  </label>
                </li>
                <li
                  v-if="!buyerChecklistItems.length"
                  class="rounded-md bg-bg px-3 py-4 text-center text-sm text-text-sub"
                >
                  등록된 체크리스트 항목이 없습니다.
                </li>
              </ul>

              <label class="mt-4 block text-sm font-semibold text-text-main">
                요청 내용
                <textarea
                  v-model="recaptureReason"
                  rows="4"
                  maxlength="500"
                  placeholder="예) 카메라 - 렌즈 부분이 잘 안 보여서 좀 더 잘 보이게 가능할까요?"
                  class="mt-2 w-full rounded-md border border-border px-3 py-2.5 text-sm outline-none focus:border-primary"
                />
              </label>

              <p
                v-if="recaptureError"
                role="alert"
                class="mt-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700"
              >
                {{ recaptureError }}
              </p>

              <div class="mt-5 flex justify-end gap-3">
                <BaseButton
                  type="button"
                  variant="outline"
                  @click="isRecaptureModalOpen = false"
                >
                  취소
                </BaseButton>
                <BaseButton
                  type="button"
                  :disabled="isSubmittingRecapture"
                  @click="submitRecaptureRequest"
                >
                  {{ isSubmittingRecapture ? '요청 중…' : '요청 보내기' }}
                </BaseButton>
              </div>
            </template>
            <template v-else>
              <p class="text-base font-bold text-text-main">
                재촬영 요청을 보냈습니다.
              </p>
              <p class="mt-2 text-sm text-text-sub">
                판매자가 확인 후 새로운 자료를 등록하면 알려드릴게요.
              </p>
              <BaseButton
                class="mt-5"
                block
                @click="isRecaptureModalOpen = false"
              >
                확인
              </BaseButton>
            </template>
          </BaseCard>
        </div>
      </template>
    </main>
  </DefaultLayout>
</template>

<style scoped>
/*
  크게 보기 창이 부드럽게 나타납니다.
  ---------------------------------------------------------------------------
  화면 전체를 덮는 창이 툭 나타나면 놀랍니다. 어두운 배경은 짧게 밝아지고, 안쪽 판은
  아주 조금 작은 상태에서 제자리로 올라옵니다. 0.1초는 "부드럽다"고 느끼면서도
  기다린다는 느낌은 들지 않는 길이입니다.
*/
.viewer-enter-active,
.viewer-leave-active {
  transition: opacity 0.1s ease;
}

.viewer-enter-from,
.viewer-leave-to {
  opacity: 0;
}

.viewer-enter-active .viewer__panel {
  animation: viewer-rise 0.13s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes viewer-rise {
  from { transform: translateY(8px) scale(0.98); }
  to { transform: none; }
}

/* 사진을 넘길 때도 툭 바뀌지 않게 짧게 밝아집니다. */
.viewer__media {
  animation: viewer-fade 0.09s ease;
}

@keyframes viewer-fade {
  from { opacity: 0.3; }
  to { opacity: 1; }
}

/* 움직임을 줄여 달라고 설정한 사용자에게는 움직임 없이 바로 보여 줍니다. */
@media (prefers-reduced-motion: reduce) {
  .viewer-enter-active,
  .viewer-leave-active,
  .viewer-enter-active .viewer__panel,
  .viewer__media {
    transition: none;
    animation: none;
  }
}

/* 검증 체크리스트: 흰 카드 하나에 구분선으로만 항목을 나눕니다. */
.checklist-card {
  background: #fff;
  border: 1px solid #f1f5f9;
  border-radius: 20px;
  box-shadow: 0 4px 20px rgb(15 23 42 / 3%);
  padding: 4px 20px;
}

.checklist-row {
  border-bottom: 1px solid #f1f5f9;
}

.checklist-row:last-child {
  border-bottom: none;
}
</style>
