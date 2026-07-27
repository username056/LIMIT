<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import { getDeviceCategories, getProducts } from '../api/products'

const products = ref([])
const route = useRoute()
const router = useRouter()
const categories = ref([])
const isLoading = ref(false)
const errorMessage = ref('')
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false, totalElements: 0 })
const filters = reactive({
  keyword: '',
  categoryId: '',
  minPrice: '',
  maxPrice: '',
  tradeRegion: '',
  verificationStatus: '',
  sort: 'createdAt,desc',
})
let latestSearchRequestId = 0

const resultCount = computed(() => pageMeta.value.totalElements ?? products.value.length)

function flattenCategories(items, depth = 0) {
  return (items || []).flatMap((item) => [
    { ...item, depth },
    ...flattenCategories(item.children, depth + 1),
  ])
}

function formatPrice(price) {
  return Number(price || 0).toLocaleString('ko-KR')
}

function verificationLabel(status) {
  return {
    COMPLETED: '검증 완료',
    IN_PROGRESS: '검증 중',
  }[status] || '상태 확인 중'
}

async function search(page = 0) {
  const requestId = ++latestSearchRequestId
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getProducts({ ...filters, page, size: 18 })
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
    tradeRegion: '',
    verificationStatus: '',
    sort: 'createdAt,desc',
  })
  const nextQuery = { ...route.query }
  delete nextQuery.q
  await router.replace({ name: 'products', query: nextQuery })
  await search(0)
}

onMounted(async () => {
  filters.keyword = String(route.query.q || '')
  try {
    categories.value = flattenCategories(await getDeviceCategories({ activeOnly: true }))
  } catch {
    categories.value = []
  }
  await search(0)
})

watch(() => route.query.q, async (keyword) => {
  const nextKeyword = String(keyword || '')
  if (nextKeyword === filters.keyword) return
  filters.keyword = nextKeyword
  await search(0)
})
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[1200px] px-4 py-8 sm:px-6 lg:px-10 lg:py-12">
      <div class="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p class="text-xs font-bold uppercase tracking-[0.18em] text-primary">
            Verified devices
          </p>
          <h1 class="mt-2 text-3xl font-bold tracking-tight text-text-main">
            상품 목록
          </h1>
          <p class="mt-2 text-sm text-text-sub">
            검증 자료가 연결된 중고 전자기기를 찾아보세요.
          </p>
        </div>
        <BaseButton to="/seller/products">
          내 상품 등록하기
        </BaseButton>
      </div>

      <div class="grid gap-8 lg:grid-cols-[220px_minmax(0,1fr)]">
        <aside>
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
                  v-model="filters.minPrice"
                  aria-label="최소 가격"
                  type="number"
                  min="0"
                  placeholder="최소"
                  class="min-w-0 rounded-md border border-border bg-bg px-2 py-2.5 text-sm outline-none focus:border-primary"
                >
                <span class="text-text-sub">–</span>
                <input
                  v-model="filters.maxPrice"
                  aria-label="최대 가격"
                  type="number"
                  min="0"
                  placeholder="최대"
                  class="min-w-0 rounded-md border border-border bg-bg px-2 py-2.5 text-sm outline-none focus:border-primary"
                >
              </div>
            </fieldset>

            <label class="mt-5 block text-xs font-semibold text-text-main">
              검증 상태
              <select
                v-model="filters.verificationStatus"
                class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-2.5 text-sm outline-none focus:border-primary"
              >
                <option value="">전체 상태</option>
                <option value="COMPLETED">검증 완료</option>
                <option value="IN_PROGRESS">검증 중</option>
              </select>
            </label>

            <label class="mt-5 block text-xs font-semibold text-text-main">
              거래 지역
              <input
                v-model.trim="filters.tradeRegion"
                placeholder="예: 서울 강남구"
                class="mt-2 w-full rounded-md border border-border bg-bg px-3 py-2.5 text-sm outline-none focus:border-primary"
              >
            </label>

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
            <RouterLink
              v-for="product in products"
              :key="product.productId"
              :to="{ name: 'product-detail', params: { productId: product.productId } }"
              class="group overflow-hidden rounded-lg border border-border bg-surface shadow-card transition hover:-translate-y-0.5 hover:border-primary/50 hover:shadow-elevated"
            >
              <div class="relative flex aspect-[4/3] items-center justify-center overflow-hidden bg-slate-50">
                <img
                  v-if="product.thumbnailUrl"
                  :src="product.thumbnailUrl"
                  :alt="product.name"
                  class="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]"
                >
                <div
                  v-else
                  class="flex flex-col items-center text-slate-300"
                >
                  <span class="text-4xl">▣</span>
                  <span class="mt-2 text-xs">등록된 이미지 없음</span>
                </div>
                <span
                  class="absolute right-3 top-3 rounded-pill border border-white/70 bg-white/90 px-2.5 py-1 text-[11px] font-bold shadow-card"
                  :class="product.verificationStatus === 'COMPLETED' ? 'text-success' : 'text-text-sub'"
                >{{ verificationLabel(product.verificationStatus) }}</span>
              </div>
              <div class="p-4">
                <p class="truncate text-xs font-semibold text-primary">
                  {{ product.manufacturerName || '제조사 미등록' }} · {{ product.modelName || '모델 미등록' }}
                </p>
                <h2 class="mt-2 truncate font-bold text-text-main">
                  {{ product.name }}
                </h2>
                <p class="mt-3 text-lg font-bold text-text-main">
                  {{ formatPrice(product.price) }}원
                </p>
                <div class="mt-3 flex items-center justify-between border-t border-border pt-3 text-xs text-text-sub">
                  <span class="truncate">{{ product.tradeRegion || '거래 지역 협의' }}</span>
                  <span class="ml-2 shrink-0 font-semibold text-primary">상세 보기 →</span>
                </div>
              </div>
            </RouterLink>
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
            aria-label="상품 목록 페이지"
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
  </DefaultLayout>
</template>
