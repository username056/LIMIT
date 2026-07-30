<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import { getDeviceCategories, getProducts } from '../api/products'
import { formatPriceDigits, toPriceDigits } from '../utils/priceInput'
import { useSellerGate } from '../auth/sellerGate'
import ProductCard from '../components/ProductCard.vue'
import SellerNoticeModal from '../components/SellerNoticeModal.vue'

const products = ref([])
const route = useRoute()
const router = useRouter()
const {
  isSellerNoticeOpen,
  goToSell,
  goToSellerApply,
  closeSellerNotice,
} = useSellerGate(router)
const categories = ref([])
const isLoading = ref(false)
const errorMessage = ref('')
const isFilterOpen = ref(false)
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false, totalElements: 0 })
const filters = reactive({
  keyword: '',
  categoryId: '',
  minPrice: '',
  maxPrice: '',
  verificationCountRanges: [], // 빈 배열 = 전체
  sort: 'createdAt,desc',
})
let latestSearchRequestId = 0

const VERIFICATION_COUNT_BUCKETS = [
  { value: '0-5', label: '0-5개' },
  { value: '5-7', label: '5-7개' },
  { value: '7-9', label: '7-9개' },
  { value: '10+', label: '10개 이상' },
]

function selectAllVerificationBuckets() {
  filters.verificationCountRanges = []
  search(0)
}

function toggleVerificationBucket(value) {
  const index = filters.verificationCountRanges.indexOf(value)
  if (index === -1) filters.verificationCountRanges.push(value)
  else filters.verificationCountRanges.splice(index, 1)

  if (filters.verificationCountRanges.length === VERIFICATION_COUNT_BUCKETS.length) {
    filters.verificationCountRanges = []
  }
  search(0)
}

// 상품 목록 API가 아직 상품별 검증 개수를 내려주지 않아, 구간 선택을 기존 검증 상태값에 매핑해
// 동작시킵니다(10개 이상만 선택 → 검증 완료, 나머지 구간만 선택 → 검증 중, 둘 다 섞이거나 전체
// 선택 → 필터 없음). 백엔드에 개수 필드가 추가되면 실제 구간 필터로 교체하세요.
function verificationStatusForBuckets(buckets) {
  if (!buckets.length) return ''
  const hasHighBucket = buckets.includes('10+')
  const hasLowBucket = buckets.some((bucket) => bucket !== '10+')
  if (hasHighBucket && hasLowBucket) return ''
  return hasHighBucket ? 'COMPLETED' : 'IN_PROGRESS'
}

const resultCount = computed(() => pageMeta.value.totalElements ?? products.value.length)
const activeFilterCount = computed(() => [
  filters.keyword,
  filters.categoryId,
  filters.minPrice,
  filters.maxPrice,
  ...filters.verificationCountRanges,
].filter(Boolean).length)

function flattenCategories(items, depth = 0) {
  return (items || []).flatMap((item) => [
    { ...item, depth },
    ...flattenCategories(item.children, depth + 1),
  ])
}

// 값에는 숫자만 담고 입력창에는 쉼표를 붙여 보여줍니다.
// 값이 그대로일 때 Vue가 DOM을 다시 그리지 않으므로 직접 되돌려 놓습니다.
function onPriceFilterInput(event, key) {
  filters[key] = toPriceDigits(event.target.value)
  event.target.value = formatPriceDigits(filters[key])
}

async function search(page = 0) {
  const requestId = ++latestSearchRequestId
  isLoading.value = true
  errorMessage.value = ''
  try {
    const { verificationCountRanges, ...restFilters } = filters
    const response = await getProducts({
      ...restFilters,
      verificationStatus: verificationStatusForBuckets(verificationCountRanges),
      page,
      size: 18,
    })
    if (requestId !== latestSearchRequestId) return
    products.value = response?.data || []
    pageMeta.value = {
      page,
      totalPages: 0,
      hasNext: false,
      totalElements: products.value.length,
      ...(response?.meta || {}),
    }
  } catch (error) {
    if (requestId !== latestSearchRequestId) return
    products.value = []
    errorMessage.value = error.message || '상품을 불러오지 못했습니다.'
  } finally {
    if (requestId === latestSearchRequestId) isLoading.value = false
  }
}

async function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    categoryId: '',
    minPrice: '',
    maxPrice: '',
    verificationCountRanges: [],
    sort: 'createdAt,desc',
  })
  const nextQuery = { ...route.query }
  delete nextQuery.q
  delete nextQuery.categoryId
  await router.replace({ name: 'products', query: nextQuery })
  await search(0)
}

onMounted(async () => {
  filters.keyword = String(route.query.q || '')
  // 홈의 카테고리 카드에서 ?categoryId=로 넘어오면 해당 카테고리가 선택된 상태로 시작합니다.
  filters.categoryId = String(route.query.categoryId || '')
  try {
    categories.value = flattenCategories(await getDeviceCategories({ activeOnly: true }))
  } catch {
    categories.value = []
  }
  await search(0)
})

watch(() => [route.query.q, route.query.categoryId], async ([keyword, categoryId]) => {
  const nextKeyword = String(keyword || '')
  const nextCategoryId = String(categoryId || '')
  if (nextKeyword === filters.keyword && nextCategoryId === filters.categoryId) return
  filters.keyword = nextKeyword
  filters.categoryId = nextCategoryId
  await search(0)
})
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[1200px] px-4 py-8 sm:px-6 lg:px-10 lg:py-12">
      <div class="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p class="text-xs font-bold uppercase tracking-[0.18em] text-primary">
            VERIFIED DEVICES
          </p>
          <h1 class="mt-2 text-2xl font-bold text-text-main">
            전체 상품
          </h1>
          <p class="mt-2 text-sm text-text-sub">
            검증 자료가 연결된 중고 전자기기를 찾아보세요.
          </p>
        </div>
        <BaseButton @click="goToSell">
          내 상품 등록하기
        </BaseButton>
      </div>

      <div class="mb-5 lg:hidden">
        <button
          type="button"
          class="flex w-full items-center justify-between rounded-md border border-border bg-surface px-4 py-3 text-sm font-semibold text-text-main"
          :aria-expanded="isFilterOpen"
          @click="isFilterOpen = !isFilterOpen"
        >
          <span>필터{{ activeFilterCount ? ` ${activeFilterCount}` : '' }}</span>
          <span class="text-primary">{{ isFilterOpen ? '접기' : '열기' }}</span>
        </button>
      </div>

      <div class="grid gap-8 lg:grid-cols-[220px_minmax(0,1fr)]">
        <aside :class="isFilterOpen ? 'block' : 'hidden lg:block'">
          <form
            class="sticky top-6 rounded-lg border border-border bg-surface p-5 shadow-card"
            aria-label="상품 검색 필터"
            @submit.prevent="search(0)"
          >
            <div class="flex items-center justify-between">
              <h2 class="font-bold text-text-main">
                필터
              </h2>
              <button
                type="button"
                class="text-xs font-semibold text-primary"
                @click="resetFilters"
              >
                초기화
              </button>
            </div>

            <label class="mt-5 block text-xs font-semibold text-text-main">
              상품명
              <input
                v-model.trim="filters.keyword"
                type="search"
                placeholder="모델명, 상품명"
                class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:bg-white"
              >
            </label>

            <label class="mt-5 block text-xs font-semibold text-text-main">
              카테고리
              <select
                v-model="filters.categoryId"
                aria-label="카테고리 선택"
                class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-2.5 text-sm outline-none focus:border-primary"
              >
                <option value="">전체 카테고리</option>
                <option
                  v-for="category in categories"
                  :key="category.categoryId"
                  :value="category.categoryId"
                >{{ `${'　'.repeat(category.depth)}${category.name}` }}</option>
              </select>
            </label>

            <fieldset class="mt-5">
              <legend class="text-xs font-semibold text-text-main">
                가격
              </legend>
              <div class="mt-2 grid grid-cols-[1fr_auto_1fr] items-center gap-2">
                <input
                  :value="formatPriceDigits(filters.minPrice)"
                  aria-label="최소 가격"
                  type="text"
                  inputmode="numeric"
                  autocomplete="off"
                  placeholder="최소"
                  class="min-w-0 rounded-md border border-border bg-bg px-2 py-2.5 text-sm outline-none focus:border-primary"
                  @input="onPriceFilterInput($event, 'minPrice')"
                >
                <span class="text-text-sub">–</span>
                <input
                  :value="formatPriceDigits(filters.maxPrice)"
                  aria-label="최대 가격"
                  type="text"
                  inputmode="numeric"
                  autocomplete="off"
                  placeholder="최대"
                  class="min-w-0 rounded-md border border-border bg-bg px-2 py-2.5 text-sm outline-none focus:border-primary"
                  @input="onPriceFilterInput($event, 'maxPrice')"
                >
              </div>
            </fieldset>

            <fieldset class="mt-5">
              <legend class="text-xs font-semibold text-text-main">
                검증 개수
              </legend>
              <div class="mt-2 flex flex-wrap gap-2">
                <button
                  type="button"
                  class="rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors"
                  :class="filters.verificationCountRanges.length === 0
                    ? 'border-primary bg-accent text-primary-dark'
                    : 'border-border text-text-sub hover:border-primary hover:text-text-main'"
                  @click="selectAllVerificationBuckets"
                >
                  전체
                </button>
                <button
                  v-for="bucket in VERIFICATION_COUNT_BUCKETS"
                  :key="bucket.value"
                  type="button"
                  class="rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors"
                  :class="filters.verificationCountRanges.includes(bucket.value)
                    ? 'border-primary bg-accent text-primary-dark'
                    : 'border-border text-text-sub hover:border-primary hover:text-text-main'"
                  @click="toggleVerificationBucket(bucket.value)"
                >
                  {{ bucket.label }}
                </button>
              </div>
            </fieldset>

            <BaseButton
              class="mt-6"
              type="submit"
              block
            >
              필터 적용
            </BaseButton>
          </form>
        </aside>

        <section class="min-w-0">
          <div class="mb-5 flex items-center justify-between border-b border-border pb-4">
            <p class="text-sm text-text-sub">
              총 <strong class="text-text-main">{{ resultCount.toLocaleString('ko-KR') }}</strong>개의 상품
            </p>
            <label class="flex items-center gap-2 text-xs text-text-sub">
              <span>정렬</span>
              <select
                v-model="filters.sort"
                aria-label="상품 정렬"
                class="rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-main"
                @change="search(0)"
              >
                <option value="createdAt,desc">최신 등록순</option>
                <option value="price,asc">낮은 가격순</option>
                <option value="price,desc">높은 가격순</option>
              </select>
            </label>
          </div>

          <p
            v-if="errorMessage"
            role="alert"
            class="mb-5 flex items-center justify-between rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
          >
            <span>{{ errorMessage }}</span>
            <button
              type="button"
              class="font-semibold underline"
              @click="search(pageMeta.page)"
            >
              다시 시도
            </button>
          </p>

          <div
            v-if="isLoading"
            class="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3"
            aria-label="상품 로딩 중"
          >
            <div
              v-for="index in 6"
              :key="index"
              class="overflow-hidden rounded-lg border border-border bg-surface"
            >
              <div class="aspect-[4/3] animate-pulse bg-slate-100" />
              <div class="space-y-3 p-4">
                <div class="h-3 w-2/5 animate-pulse rounded bg-slate-100" />
                <div class="h-5 w-4/5 animate-pulse rounded bg-slate-100" />
                <div class="h-4 w-1/2 animate-pulse rounded bg-slate-100" />
              </div>
            </div>
          </div>

          <div
            v-else-if="products.length"
            class="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3"
          >
            <ProductCard
              v-for="product in products"
              :key="product.productId"
              :product="product"
              :to="{ name: 'product-detail', params: { productId: product.productId } }"
            />
          </div>

          <div
            v-else-if="!errorMessage"
            class="rounded-lg border border-dashed border-border bg-surface px-6 py-20 text-center"
          >
            <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-accent text-xl text-primary">
              ⌕
            </div>
            <h2 class="mt-4 font-bold text-text-main">
              조건에 맞는 상품이 없습니다.
            </h2>
            <p class="mt-2 text-sm text-text-sub">
              필터 범위를 넓히거나 검색어를 바꿔 보세요.
            </p>
            <BaseButton
              class="mt-5"
              variant="outline"
              @click="resetFilters"
            >
              필터 초기화
            </BaseButton>
          </div>

          <nav
            v-if="!isLoading && pageMeta.totalPages > 1"
            class="mt-8 flex items-center justify-center gap-4"
            aria-label="전체 상품 페이지"
          >
            <BaseButton
              variant="outline"
              :disabled="pageMeta.page === 0"
              @click="search(pageMeta.page - 1)"
            >
              이전
            </BaseButton>
            <span class="text-sm text-text-sub">
              <strong class="text-text-main">{{ pageMeta.page + 1 }}</strong> / {{ pageMeta.totalPages }}
            </span>
            <BaseButton
              variant="outline"
              :disabled="!pageMeta.hasNext"
              @click="search(pageMeta.page + 1)"
            >
              다음
            </BaseButton>
          </nav>
        </section>
      </div>
    </main>

    <SellerNoticeModal
      :open="isSellerNoticeOpen"
      @close="closeSellerNotice"
      @apply="goToSellerApply"
    />
  </DefaultLayout>
</template>
