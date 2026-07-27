<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import { buildProductById } from '../mock/products'
import { getProduct } from '../api/product'
import { useAuthSession } from '../auth/session'

// TODO(상품 API 연동): 상품 상세/기기 진단/자가 검수 체크리스트 API가 실제로 준비되면
// 아래 mock 표시 데이터(product, diagnostics, checklistItems)를 API 응답으로 교체하세요.
// 판매자 여부 판단(isOwner)은 이미 실제 API 응답 형태(sellerId) 기준으로 짜여 있어 그대로 두면 됩니다.
// 체크리스트는 판매자가 검수 사진·영상을 촬영하면 자동으로 채워질 예정이며, 이 부분은 백엔드에서 준비 중입니다.

const DEVICE_TYPE_LABELS = {
  laptop: '노트북',
  smartphone: '스마트폰',
  tablet: '태블릿',
  camera: '카메라',
}

const route = useRoute()
const product = computed(() => buildProductById(route.params.productId))
const categoryLabel = computed(() => DEVICE_TYPE_LABELS[product.value.deviceType] || '기기')

// 판매자(글 작성자) 여부는 실제 로그인 세션 + 실제 상품 상세 API로 판단합니다.
// 지금은 백엔드가 스텁이라 sellerId가 항상 고정값(55)으로 와서, 로그인 계정의 회원 ID가
// 55가 아닌 이상 항상 "구매자"로 판정됩니다. 백엔드가 실제 판매자 ID를 반환하면 자동으로 맞게 동작합니다.
const session = useAuthSession()
const sellerId = ref(null)
const isCheckingOwner = ref(true)

onMounted(async () => {
  try {
    const detail = await getProduct(route.params.productId)
    sellerId.value = detail?.sellerId ?? null
  } catch {
    sellerId.value = null
  } finally {
    isCheckingOwner.value = false
  }
})

const isOwner = computed(() => {
  const memberId = session.value?.member?.memberId
  return memberId != null && sellerId.value != null && memberId === sellerId.value
})

// 수동 토글: 자동 판별 결과를 화면 확인용으로 덮어쓸 수 있게 남겨둡니다.
const manualViewAs = ref(null)
const viewAs = computed({
  get: () => manualViewAs.value ?? (isOwner.value ? 'seller' : 'buyer'),
  set: (value) => { manualViewAs.value = value },
})

const liked = ref(false)
const isHeartPopping = ref(false)
function toggleLike() {
  liked.value = !liked.value
  isHeartPopping.value = false
  requestAnimationFrame(() => {
    isHeartPopping.value = true
  })
}

const images = [0, 1, 2, 3]
const selectedImage = ref(0)

const diagnostics = [
  { label: '배터리 사이클', value: '127회' },
  { label: '최대 성능 수치', value: '94%', highlight: true },
  { label: 'RAM 용량', value: '18GB' },
  { label: 'SSD 저장공간', value: '512GB' },
]

// 체크박스(recaptureRequested)는 구매자가 특정 항목이 미덥지 않을 때 표시해두는 용도입니다.
// 체크된 항목들을 모아 "상품 재촬영 요청"에 같이 전달합니다.
const checklistItems = ref([
  { id: 1, label: '외관 흠집 및 모서리 파손', status: 'confirmed', recaptureRequested: false },
  { id: 2, label: '화면 디스플레이 오작동 및 번인', status: 'confirmed', recaptureRequested: false },
  { id: 3, label: '키보드 및 트랙패드 터치 감도', status: 'confirmed', recaptureRequested: false },
  { id: 4, label: '배터리 성능 및 최대 용량', status: 'confirmed', recaptureRequested: false },
  { id: 5, label: '스피커 데시벨 및 마이크 녹음', status: 'flagged', shortLabel: '스피커', recaptureRequested: false },
])

const confirmedCount = computed(() => checklistItems.value.filter((item) => item.status === 'confirmed').length)
const flaggedItems = computed(() => checklistItems.value.filter((item) => item.status === 'flagged'))
const selectedForRecapture = computed(() => checklistItems.value.filter((item) => item.recaptureRequested))
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-[1200px] px-6 py-8 lg:px-10">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-md bg-accent px-4 py-2 text-xs font-semibold text-primary-dark">
        <span>
          WIREFRAME MOCK · 기기 진단과 자가 검수 체크리스트는 백엔드 준비 중이라 mock 데이터입니다.
          <template v-if="isCheckingOwner">· 판매자 여부 확인 중...</template>
          <template v-else>· 실제 로그인 세션과 상품 API로 자동 판별: {{ isOwner ? '판매자' : '구매자' }} (아래 버튼으로 확인용 전환 가능)</template>
        </span>
        <span class="inline-flex items-center gap-1 rounded-full bg-surface p-1">
          <button
            type="button"
            class="rounded-full px-3 py-1 font-semibold transition-colors"
            :class="viewAs === 'buyer' ? 'bg-primary-gradient text-white' : 'text-text-sub'"
            @click="viewAs = 'buyer'"
          >
            구매자 화면
          </button>
          <button
            type="button"
            class="rounded-full px-3 py-1 font-semibold transition-colors"
            :class="viewAs === 'seller' ? 'bg-primary-gradient text-white' : 'text-text-sub'"
            @click="viewAs = 'seller'"
          >
            판매자 화면
          </button>
        </span>
      </div>

      <nav
        class="mb-6 flex items-center gap-2 text-sm text-text-sub"
        aria-label="breadcrumb"
      >
        <RouterLink
          to="/"
          aria-label="홈"
          class="hover:text-primary"
        >
          <svg
            class="h-4 w-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M3 12l9-9 9 9M5 10v10a1 1 0 001 1h4v-6h4v6h4a1 1 0 001-1V10"
            />
          </svg>
        </RouterLink>
        <span>›</span>
        <RouterLink
          to="/products"
          class="hover:text-primary"
        >
          상품 둘러보기
        </RouterLink>
        <span>›</span>
        <span>{{ categoryLabel }}</span>
        <span>›</span>
        <span class="font-semibold text-text-main">{{ product.name }}</span>
      </nav>

      <div class="grid grid-cols-1 gap-10 lg:grid-cols-2">
        <div>
          <div class="aspect-[4/3] w-full rounded-lg bg-primary-gradient" />
          <div class="mt-3 grid grid-cols-4 gap-3">
            <button
              v-for="image in images"
              :key="image"
              type="button"
              class="aspect-[4/3] rounded-md bg-primary-gradient opacity-70"
              :class="selectedImage === image ? 'opacity-100 ring-2 ring-primary' : 'hover:opacity-100'"
              :aria-label="`상품 이미지 ${image + 1}`"
              @click="selectedImage = image"
            />
          </div>
        </div>

        <div>
          <BaseBadge variant="success">
            판매중
          </BaseBadge>
          <h1 class="mt-3 text-2xl font-bold text-text-main">
            {{ product.name }}
          </h1>
          <p class="mt-1 text-sm text-text-sub">
            {{ product.brand }} · 검수 {{ product.verified }}/{{ product.required }}
          </p>

          <div class="mt-4 flex items-center justify-between">
            <p class="text-2xl font-bold text-text-main">
              ₩{{ product.price.toLocaleString('ko-KR') }}
            </p>
            <button
              type="button"
              class="flex h-10 w-10 items-center justify-center rounded-full bg-primary-gradient shadow-elevated"
              :aria-label="liked ? '좋아요 취소' : '좋아요'"
              @click="toggleLike"
            >
              <svg
                class="h-5 w-5"
                :class="[liked ? 'text-pink-400' : 'text-white', isHeartPopping ? 'animate-heart-pop' : '']"
                viewBox="0 0 24 24"
                fill="currentColor"
                aria-hidden="true"
                @animationend="isHeartPopping = false"
              >
                <path d="M11.645 20.91l-.007-.003-.022-.012a15.247 15.247 0 01-.383-.218 25.18 25.18 0 01-4.244-3.17C4.688 15.36 2.25 12.174 2.25 8.25 2.25 5.322 4.714 3 7.688 3A5.5 5.5 0 0112 5.052 5.5 5.5 0 0116.313 3c2.973 0 5.437 2.322 5.437 5.25 0 3.925-2.438 7.111-4.739 9.256a25.175 25.175 0 01-4.244 3.17 15.247 15.247 0 01-.383.219l-.022.012-.007.004-.003.001a.752.752 0 01-.704 0l-.003-.001z" />
              </svg>
            </button>
          </div>

          <BaseCard class="mt-5 flex items-center gap-3">
            <span class="flex h-9 w-9 items-center justify-center rounded-full bg-bg text-text-sub">
              <svg
                class="h-5 w-5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.5 20.25a7.5 7.5 0 0115 0"
                />
              </svg>
            </span>
            <span class="text-sm font-semibold text-text-main">정직한전자기기</span>
          </BaseCard>

          <div class="mt-6">
            <h2 class="mb-3 text-sm font-bold text-text-main">
              기기 진단 주요 요약
            </h2>
            <div class="grid grid-cols-2 gap-3">
              <div
                v-for="item in diagnostics"
                :key="item.label"
                class="rounded-md bg-bg p-3"
              >
                <p class="text-xs text-text-sub">
                  {{ item.label }}
                </p>
                <p
                  class="mt-1 text-sm font-bold"
                  :class="item.highlight ? 'text-green-600' : 'text-text-main'"
                >
                  {{ item.value }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <BaseCard class="mt-10">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-base font-bold text-text-main">
              상세 자가 검수 체크리스트
            </h2>
            <p class="mt-1 text-sm text-text-sub">
              판매자가 직접 테스트하고 사진과 비디오로 첨부한 체크리스트입니다.
            </p>
          </div>
          <p class="whitespace-nowrap text-sm font-bold text-text-main">
            {{ confirmedCount }}/{{ checklistItems.length }}
          </p>
        </div>

        <ul class="mt-4 divide-y divide-border">
          <li
            v-for="item in checklistItems"
            :key="item.id"
            class="flex items-center justify-between gap-3 py-3"
          >
            <span class="text-sm text-text-main">{{ item.label }}</span>
            <span class="flex items-center gap-3">
              <!-- TODO(상품 API 연동): 판매자가 촬영한 실제 검수 사진으로 교체 (EvidenceResponse.url 등) -->
              <span
                class="h-10 w-10 shrink-0 rounded-md bg-primary-gradient opacity-70"
                aria-hidden="true"
              />
              <input
                v-model="item.recaptureRequested"
                type="checkbox"
                class="h-4 w-4 rounded border-border text-primary focus:ring-primary"
                :aria-label="`${item.label} 재촬영 요청`"
              >
              <span
                class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full"
                :class="item.status === 'confirmed' ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'"
              >
                <svg
                  v-if="item.status === 'confirmed'"
                  class="h-4 w-4"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  aria-hidden="true"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M5 13l4 4L19 7"
                  />
                </svg>
                <svg
                  v-else
                  class="h-4 w-4"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  aria-hidden="true"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M12 9v4m0 4h.01M10.29 3.86l-8.18 14.18A1 1 0 003 19.5h18a1 1 0 00.89-1.46L13.71 3.86a1 1 0 00-1.72 0z"
                  />
                </svg>
              </span>
            </span>
          </li>
        </ul>

        <p
          v-if="flaggedItems.length"
          class="mt-2 rounded-md bg-amber-50 px-4 py-3 text-sm text-amber-700"
        >
          <span class="font-semibold">{{ flaggedItems.length }}개 항목 미확인됨 ({{ flaggedItems.map((item) => item.shortLabel).join(', ') }})</span><br>
          추가 확인이 필요하다면 판매자에게 해당 부위 재촬영 요청을 추천드립니다. 실시간 채팅 혹은 1대1 화상 통화도 추천드려요]!
        </p>

        <div class="mt-5 flex flex-col gap-3 sm:flex-row">
          <template v-if="viewAs === 'buyer'">
            <BaseButton
              variant="outline"
              :to="{
                name: 'coming-soon',
                params: { feature: 'recapture-request' },
                query: { name: product.name, items: selectedForRecapture.map((item) => item.label).join(',') },
              }"
            >
              상품 재촬영 요청
            </BaseButton>
            <BaseButton
              variant="outline"
              class="border-primary text-primary"
              :to="{ name: 'coming-soon', params: { feature: 'chat' }, query: { name: product.name } }"
            >
              판매자와 대화하기 (채팅)
            </BaseButton>
            <BaseButton
              class="flex-1"
              :to="{ name: 'purchase', params: { productId: product.id } }"
            >
              바로 구매 신청하기
            </BaseButton>
          </template>
          <template v-else>
            <BaseButton
              variant="outline"
              :to="{ name: 'coming-soon', params: { feature: 'product-edit' }, query: { name: product.name } }"
            >
              글 수정하기
            </BaseButton>
            <BaseButton
              variant="outline"
              class="border-primary text-primary"
              :to="{ name: 'coming-soon', params: { feature: 'listing-stats' }, query: { name: product.name } }"
            >
              작성글 통계 및 문의 채팅 목록
            </BaseButton>
            <BaseButton
              class="flex-1"
              :to="{ name: 'coming-soon', params: { feature: 'recapture-request' }, query: { name: product.name } }"
            >
              상품 재촬영
            </BaseButton>
          </template>
        </div>
      </BaseCard>
    </div>
  </DefaultLayout>
</template>

<style scoped>
@keyframes heart-pop {
  0% { transform: scale(1); }
  35% { transform: scale(1.35); }
  60% { transform: scale(0.9); }
  100% { transform: scale(1); }
}

.animate-heart-pop {
  animation: heart-pop 0.3s ease;
}
</style>
