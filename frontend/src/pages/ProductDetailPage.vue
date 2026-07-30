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
} from '../api/products'
import { createChatRoom, requestRtcCall } from '../api/rtc'
import { createOrGetChatRoom } from '../api/chat'
import { getAccessToken, getSessionMember } from '../auth/session'
import { isSoldOut, productStatusLabel } from '../utils/productStatus'

const route = useRoute()
const router = useRouter()
const product = ref(null)
const isLoading = ref(true)
const isFavorite = ref(false)
const isUpdatingFavorite = ref(false)
const isRequestingCall = ref(false)
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
const publicEvidence = computed(() => checklistItems.value.flatMap(
  (item) => evidenceByChecklistItem.value[item.checklistItemId] || [],
))
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

async function requestCall() {
  if (!await requireLogin()) return
  isRequestingCall.value = true
  errorMessage.value = ''
  try {
    const room = await createChatRoom(product.value.productId)
    await requestRtcCall(room.roomId, { memo: `${product.value.name} 상태 실시간 확인 요청` })
    await router.push({ name: 'calls' })
  } catch (error) {
    errorMessage.value = error.message || '영상 확인 요청을 보내지 못했습니다.'
  } finally {
    isRequestingCall.value = false
  }
}

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
              <span class="absolute left-4 top-4 border-l-2 border-primary bg-surface/95 px-2.5 py-1 text-xs font-bold text-primary">
                {{ productStatusLabel(product.status) }}
              </span>
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
              <BaseButton
                v-if="isOwner"
                block
                :to="{ name: 'seller-product-edit', params: { productId: product.productId } }"
              >
                수정하기
              </BaseButton>
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
                  class="px-3 py-2 text-sm"
                  variant="outline"
                  :disabled="isOpeningChat"
                  @click="openChat"
                >
                  {{ isOpeningChat ? '채팅방 여는 중…' : '판매자에게 문의하기' }}
                </BaseButton>
              </template>
            </div>
            <BaseButton
              v-if="!isOwner"
              class="mt-2 px-3 py-2 text-sm"
              variant="ghost"
              :disabled="isRequestingCall"
              @click="requestCall"
            >
              {{ isRequestingCall ? '요청 중…' : '1:1 영상으로 상태 추가 확인' }}
            </BaseButton>

            <p
              v-if="errorMessage"
              role="alert"
              class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
            >
              {{ errorMessage }}
            </p>
          </section>
        </div>

        <div class="mt-16 grid gap-10 lg:grid-cols-[1fr_360px]">
          <section class="border-t border-border pt-6 sm:pt-8">
            <h2 class="text-lg font-bold text-text-main">
              상품 설명
            </h2>
            <p class="mt-4 whitespace-pre-wrap text-sm leading-7 text-text-sub">
              {{ product.description || '판매자가 등록한 상세 설명이 없습니다.' }}
            </p>
          </section>

          <section class="border-l-2 border-primary bg-bg px-5 py-4">
            <div class="flex items-start justify-between">
              <div>
                <p class="text-xs font-semibold text-primary">
                  검증 체크리스트
                </p>
                <h2 class="mt-1 font-bold text-text-main">
                  상태 자료 확인
                </h2>
              </div>
              <strong class="text-2xl text-primary">{{ checklistRate }}%</strong>
            </div>
            <div class="mt-5 h-2 overflow-hidden rounded-pill bg-slate-100">
              <div
                class="h-full rounded-pill bg-primary-gradient"
                :style="{ width: `${checklistRate}%` }"
              />
            </div>
            <p class="mt-3 text-sm text-text-sub">
              필수 항목 <strong class="text-text-main">{{ checklist.completed || 0 }}</strong> /
              {{ checklist.required || 0 }}개 확인
            </p>
            <p
              v-if="checklist.recaptureRequested"
              class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700"
            >
              추가 확인이 필요한 항목이 {{ checklist.recaptureRequested }}개 있습니다.
            </p>
            <p class="mt-4 text-xs leading-5 text-text-sub">
              원본 상태 자료는 상품과 연결된 체크리스트 기준으로 관리됩니다.
            </p>
            <ul
              v-if="publicEvidence.length"
              class="mt-4 grid grid-cols-3 gap-2"
              aria-label="구매자 공개 검수 증빙"
            >
              <li
                v-for="evidence in publicEvidence"
                :key="evidence.evidenceId"
                class="overflow-hidden rounded-md border border-border bg-white"
              >
                <video
                  v-if="evidence.evidenceType === 'VIDEO'"
                  :src="evidence.mediaUrl"
                  controls
                  preload="metadata"
                  class="aspect-square w-full object-cover"
                />
                <img
                  v-else-if="evidence.evidenceType === 'PHOTO'"
                  :src="evidence.mediaUrl"
                  alt="판매자가 공개한 검수 증빙"
                  class="aspect-square w-full object-cover"
                >
                <a
                  v-else
                  :href="evidence.mediaUrl"
                  class="block px-2 py-5 text-center text-xs font-medium text-primary"
                >
                  검수 파일 보기
                </a>
              </li>
            </ul>
            <BaseButton
              class="mt-4 px-3 py-2 text-sm"
              variant="outline"
              @click="openRecaptureModal"
            >
              재촬영 요청
            </BaseButton>
          </section>
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
