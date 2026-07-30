<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
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
import { getAccessToken, getSessionMember } from '../auth/session'
import { canSellerMarkSold, isSoldOut } from '../utils/productStatus'

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
// 소유자 전용 조회로 불러온 경우(비공개 상품)와, 판매 중인 내 상품을 공개 조회로 본 경우를 함께 다룹니다.
const loadedViaOwnerApi = ref(false)
const isOwner = computed(() => {
  if (loadedViaOwnerApi.value) return true
  const memberId = getSessionMember()?.memberId
  return Boolean(memberId && product.value?.sellerId
    && String(product.value.sellerId) === String(memberId))
})

const checklist = computed(() => product.value?.checklistSummary || {})
const checklistRate = computed(() => {
  const required = Number(checklist.value.required || 0)
  const completed = Number(checklist.value.completed || 0)
  return required ? Math.min(100, Math.round((completed / required) * 100)) : 0
})
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
const buyerChecklist = computed(() => buyerChecklistItems.value.map((item) => ({
  ...item,
  required: item.required ?? item.isRequired ?? false,
  completed: item.status === 'COMPLETED',
  evidence: evidenceByChecklistItem.value[item.checklistItemId] || [],
})))

const EVIDENCE_TYPE_LABELS = {
  PHOTO: '사진',
  VIDEO: '영상',
  DIAGNOSTIC_FILE: '진단파일',
  SELLER_CONFIRMATION: '판매자 확인',
}

function evidenceTypeLabel(type) {
  return EVIDENCE_TYPE_LABELS[type] || type || '자료'
}

// 증빙 원본을 크게 보는 팝업입니다. 목록 안 썸네일은 56px이라 영상 재생에는 너무 작습니다.
const mediaViewer = ref(null)

function openMediaViewer(item, evidence) {
  mediaViewer.value = { itemName: item.name, evidence }
}

// 설명은 기본 4줄로 접어 두고, 길면 펼쳐 봅니다. 체크리스트가 먼저 눈에 들어오게 하려는 의도입니다.
const isDescriptionExpanded = ref(false)
const isDescriptionLong = computed(() => (product.value?.description || '').length > 180)
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
    '판매 완료로 바꿀까요?\n\n구매자에게 더 이상 노출되지 않고, 되돌리거나 수정할 수 없습니다.',
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

onMounted(async () => {
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
  } catch (error) {
    errorMessage.value = error.message || '상품을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[1120px] px-4 py-8 sm:px-6 lg:px-10 lg:py-12">
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
        class="rounded-lg border border-border bg-surface px-6 py-20 text-center"
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
        <div class="grid gap-8 lg:grid-cols-[1.08fr_0.92fr] lg:gap-12">
          <section aria-label="상품 이미지">
            <div class="relative flex aspect-[4/3] items-center justify-center overflow-hidden rounded-md bg-slate-50">
              <img
                v-if="activeImageUrl"
                :src="activeImageUrl"
                :alt="product.name"
                class="h-full w-full object-cover"
              >
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
            <ul
              v-if="productImages.length > 1"
              class="mt-3 grid grid-cols-5 gap-2"
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
          </section>

          <section>
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <p class="text-sm font-semibold text-primary">
                  {{ product.device?.manufacturer || '제조사 미등록' }}
                  <span class="text-text-sub">· {{ product.device?.model || '모델 미등록' }}</span>
                </p>
                <h1 class="mt-3 text-3xl font-bold leading-tight tracking-tight text-text-main">
                  {{ product.name }}
                </h1>
              </div>
              <button
                type="button"
                class="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-md border text-xl transition"
                :class="isFavorite ? 'border-primary bg-accent text-primary' : 'border-border bg-surface text-text-sub hover:border-primary'"
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

            <dl class="mt-8 grid grid-cols-2 gap-x-6 gap-y-5 border-y border-border py-5 text-sm">
              <div>
                <dt class="text-xs text-text-sub">
                  색상
                </dt>
                <dd class="mt-1 font-semibold text-text-main">
                  {{ product.device?.color || '미입력' }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  저장 용량
                </dt>
                <dd class="mt-1 font-semibold text-text-main">
                  {{ product.device?.storageGb ? `${product.device.storageGb}GB` : '미입력' }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-text-sub">
                  상품 번호
                </dt>
                <dd class="mt-1 font-semibold text-text-main">
                  #{{ product.productId }}
                </dd>
              </div>
            </dl>

            <!-- 내 상품에서는 구매·문의처럼 자기 자신을 향하는 행동 대신 수정 동선만 보여줍니다. -->
            <div class="mt-5 space-y-3">
              <template v-if="isOwner">
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
              </template>
              <!-- 구매하기와 문의하기 두 개만 둡니다. 영상 확인은 채팅방 안에서 요청합니다. -->
              <template v-else>
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
              </template>
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

        <!--
          검증 체크리스트를 넓은 쪽에 두고 상품 설명을 좁게 접어 둡니다. 이 서비스에서 구매 판단의
          근거는 판매자가 쓴 설명글보다 항목별 검증 자료라서, 그 쪽이 먼저 읽히게 배치했습니다.
        -->
        <div class="mt-16 grid gap-8 lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-10">
          <section class="rounded-lg border border-border bg-surface p-5 sm:p-6">
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
              <div class="text-right">
                <strong class="text-3xl text-primary">{{ checklistRate }}%</strong>
                <p class="mt-1 text-xs text-text-sub">
                  필수 {{ checklist.completed || 0 }} / {{ checklist.required || 0 }}개 확인
                </p>
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
            <ul
              v-if="buyerChecklist.length"
              class="mt-4 max-h-[27.5rem] space-y-2 overflow-y-auto pr-1"
              aria-label="검증 체크리스트 항목"
            >
              <li
                v-for="item in buyerChecklist"
                :key="item.checklistItemId"
                class="flex h-20 items-center gap-3 rounded-md border border-border bg-bg px-3"
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
                    v-for="evidence in item.evidence.slice(0, 3)"
                    :key="evidence.evidenceId"
                  >
                    <button
                      type="button"
                      class="relative block h-14 w-14 overflow-hidden rounded-md border border-border bg-white"
                      :aria-label="`${item.name} 검증 자료 크게 보기`"
                      @click="openMediaViewer(item, evidence)"
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
                  <li
                    v-if="item.evidence.length > 3"
                    class="text-xs font-semibold text-text-sub"
                  >
                    +{{ item.evidence.length - 3 }}
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

            <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
              <p class="text-xs text-text-sub">
                자료가 부족하면 판매자에게 다시 찍어 달라고 요청할 수 있습니다.
              </p>
              <BaseButton
                v-if="!isOwner"
                class="px-3 py-2 text-sm"
                variant="outline"
                @click="openRecaptureModal"
              >
                재촬영 요청
              </BaseButton>
            </div>
          </section>

          <section class="lg:border-l lg:border-border lg:pl-8">
            <h2 class="font-bold text-text-main">
              상품 설명
            </h2>
            <p
              class="mt-3 whitespace-pre-wrap text-sm leading-6 text-text-sub"
              :class="isDescriptionExpanded ? '' : 'line-clamp-4'"
            >
              {{ product.description || '판매자가 등록한 상세 설명이 없습니다.' }}
            </p>
            <button
              v-if="isDescriptionLong"
              type="button"
              class="mt-2 text-xs font-semibold text-primary hover:underline"
              @click="isDescriptionExpanded = !isDescriptionExpanded"
            >
              {{ isDescriptionExpanded ? '접기' : '더 보기' }}
            </button>
          </section>
        </div>

        <!-- 증빙 원본 팝업. 목록 썸네일이 작아서 영상은 여기서 재생합니다. -->
        <div
          v-if="mediaViewer"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4"
          @click.self="mediaViewer = null"
        >
          <div class="w-full max-w-2xl overflow-hidden rounded-lg bg-surface">
            <div class="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
              <p class="truncate text-sm font-bold text-text-main">
                {{ mediaViewer.itemName }}
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
            <div class="flex max-h-[70vh] items-center justify-center bg-black">
              <video
                v-if="mediaViewer.evidence.evidenceType === 'VIDEO'"
                :src="mediaViewer.evidence.mediaUrl"
                controls
                autoplay
                class="max-h-[70vh] w-full"
              />
              <img
                v-else-if="mediaViewer.evidence.evidenceType === 'PHOTO'"
                :src="mediaViewer.evidence.mediaUrl"
                :alt="`${mediaViewer.itemName} 검증 자료`"
                class="max-h-[70vh] w-full object-contain"
              >
              <a
                v-else
                :href="mediaViewer.evidence.mediaUrl"
                class="block px-4 py-16 text-sm font-semibold text-white underline"
              >
                검수 파일 내려받기
              </a>
            </div>
          </div>
        </div>

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
