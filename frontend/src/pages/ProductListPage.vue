<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseSelect from '../components/BaseSelect.vue'
import { VERIFICATION_TIERS, buildPage, verificationTierOf } from '../mock/products'

// WIREFRAME MOCK: 상품 목록/찜/검수 개수 API가 연결되기 전까지 화면 흐름을 확인하기 위한 mock 데이터입니다.

const router = useRouter()

const DEVICE_TYPES = [
  { value: 'laptop', label: '노트북', count: 128 },
  { value: 'smartphone', label: '스마트폰', count: 412 },
  { value: 'tablet', label: '태블릿', count: 98 },
  { value: 'camera', label: '카메라', count: 34 },
]

const MANUFACTURERS = ['Samsung', 'Apple', 'LG', 'Lenovo', 'Dell']

const PRICE_PRESETS = [
  { label: '50만원 이하', min: 0, max: 500000 },
  { label: '50~100만원', min: 500000, max: 1000000 },
  { label: '100~150만원', min: 1000000, max: 1500000 },
  { label: '150만원 이상', min: 1500000, max: null },
]

const SORT_OPTIONS = [
  { value: 'latest', label: '최신 순' },
  { value: 'price-desc', label: '높은 가격 순' },
  { value: 'verified-desc', label: '많은 검수 순' },
]

const MAX_PAGES = 6

const selectedDeviceTypes = ref(DEVICE_TYPES.map((type) => type.value))
const selectedManufacturers = ref([])
const minPrice = ref('')
const maxPrice = ref('')
const selectedTiers = ref([])
const sortBy = ref('latest')

const products = ref([])
const page = ref(0)
const hasMore = ref(true)
const isLoadingMore = ref(false)
const sentinel = ref(null)
let observer = null

function toggleInList(list, value) {
  const index = list.value.indexOf(value)
  if (index === -1) list.value.push(value)
  else list.value.splice(index, 1)
}

function applyPricePreset(preset) {
  minPrice.value = preset.min ? String(preset.min) : ''
  maxPrice.value = preset.max ? String(preset.max) : ''
}

function toggleLike(product) {
  product.liked = !product.liked
}

function goToDetail(productId) {
  router.push({ name: 'product-detail', params: { productId } })
}

const filteredProducts = computed(() => products.value.filter((product) => {
  if (!selectedDeviceTypes.value.includes(product.deviceType)) return false
  if (selectedManufacturers.value.length && !selectedManufacturers.value.includes(product.brand)) return false

  const min = minPrice.value ? Number(minPrice.value) : null
  const max = maxPrice.value ? Number(maxPrice.value) : null
  if (min !== null && product.price < min) return false
  if (max !== null && product.price > max) return false

  if (selectedTiers.value.length && !selectedTiers.value.includes(verificationTierOf(product).value)) return false

  return true
}))

const sortedProducts = computed(() => {
  const list = [...filteredProducts.value]
  if (sortBy.value === 'price-desc') return list.sort((a, b) => b.price - a.price)
  if (sortBy.value === 'verified-desc') return list.sort((a, b) => b.verified - a.verified)
  return list.sort((a, b) => b.createdAt - a.createdAt)
})

function loadMore() {
  if (isLoadingMore.value || !hasMore.value) return
  isLoadingMore.value = true
  setTimeout(() => {
    products.value = [...products.value, ...buildPage(page.value)]
    page.value += 1
    if (page.value >= MAX_PAGES) hasMore.value = false
    isLoadingMore.value = false
  }, 600)
}

onMounted(() => {
  loadMore()
  observer = new IntersectionObserver((entries) => {
    if (entries[0]?.isIntersecting) loadMore()
  })
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-[1200px] px-6 py-10 lg:px-10">
      <p class="mb-4 rounded-md bg-accent px-4 py-2 text-xs font-semibold text-primary-dark">
        WIREFRAME MOCK · 찜(좋아요)과 검수 개수는 아직 백엔드 API가 없어 mock 데이터로 표시됩니다.
      </p>

      <div class="mb-8 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 class="text-2xl font-bold text-text-main">
            상품 목록
          </h1>
          <p class="mt-1 text-sm text-text-sub">
            원하는 조건으로 전자기기를 빠르게 찾아보세요
          </p>
        </div>
        <BaseSelect
          v-model="sortBy"
          :options="SORT_OPTIONS"
          class="w-full sm:w-44"
        />
      </div>

      <div class="flex flex-col gap-8 lg:flex-row">
        <aside class="w-full shrink-0 lg:w-64">
          <BaseCard class="space-y-8">
            <div>
              <h2 class="mb-3 text-sm font-bold text-text-main">
                기기 유형
              </h2>
              <label
                v-for="type in DEVICE_TYPES"
                :key="type.value"
                class="mb-2 flex cursor-pointer items-center justify-between text-sm"
              >
                <span class="flex items-center gap-2">
                  <input
                    v-model="selectedDeviceTypes"
                    type="checkbox"
                    :value="type.value"
                    class="h-4 w-4 rounded border-border text-primary focus:ring-primary"
                  >
                  <span class="text-text-main">{{ type.label }}</span>
                </span>
                <span class="text-text-sub">{{ type.count }}</span>
              </label>
            </div>

            <div>
              <h2 class="mb-3 text-sm font-bold text-text-main">
                가격 범위
              </h2>
              <div class="mb-2 flex gap-2">
                <input
                  v-model="minPrice"
                  type="number"
                  placeholder="최소 가격"
                  class="w-1/2 rounded-md border border-border px-3 py-2 text-sm text-text-main outline-none focus:border-primary"
                >
                <input
                  v-model="maxPrice"
                  type="number"
                  placeholder="최대 가격"
                  class="w-1/2 rounded-md border border-border px-3 py-2 text-sm text-text-main outline-none focus:border-primary"
                >
              </div>
              <div class="grid grid-cols-2 gap-2">
                <button
                  v-for="preset in PRICE_PRESETS"
                  :key="preset.label"
                  type="button"
                  class="rounded-md border border-border px-2 py-1.5 text-xs text-text-sub hover:border-primary hover:text-primary"
                  @click="applyPricePreset(preset)"
                >
                  {{ preset.label }}
                </button>
              </div>
            </div>

            <div>
              <h2 class="mb-3 text-sm font-bold text-text-main">
                제조사
              </h2>
              <label
                v-for="brand in MANUFACTURERS"
                :key="brand"
                class="mb-2 flex cursor-pointer items-center gap-2 text-sm"
              >
                <input
                  v-model="selectedManufacturers"
                  type="checkbox"
                  :value="brand"
                  class="h-4 w-4 rounded border-border text-primary focus:ring-primary"
                >
                <span class="text-text-main">{{ brand }}</span>
              </label>
            </div>

            <div>
              <h2 class="mb-3 text-sm font-bold text-text-main">
                검수 상태
              </h2>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="tier in VERIFICATION_TIERS"
                  :key="tier.value"
                  type="button"
                  class="rounded-full px-3 py-1.5 text-xs font-semibold transition-colors"
                  :class="selectedTiers.includes(tier.value)
                    ? 'bg-primary-gradient text-white'
                    : 'border border-border text-text-sub hover:border-primary hover:text-primary'"
                  @click="toggleInList(selectedTiers, tier.value)"
                >
                  {{ tier.label }}
                </button>
              </div>
            </div>
          </BaseCard>
        </aside>

        <div class="flex-1">
          <div
            v-if="sortedProducts.length"
            class="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-3"
          >
            <BaseCard
              v-for="product in sortedProducts"
              :key="product.id"
              :padded="false"
              class="cursor-pointer overflow-hidden transition-shadow hover:shadow-elevated"
              role="link"
              tabindex="0"
              @click="goToDetail(product.id)"
              @keydown.enter="goToDetail(product.id)"
            >
              <div class="relative aspect-[4/3] bg-primary-gradient">
                <button
                  type="button"
                  class="absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-full bg-surface/90 shadow-elevated"
                  :aria-label="product.liked ? `${product.name} 좋아요 취소` : `${product.name} 좋아요`"
                  @click.stop="toggleLike(product)"
                >
                  <svg
                    class="h-4 w-4"
                    :class="product.liked ? 'text-red-500' : 'text-white'"
                    viewBox="0 0 24 24"
                    fill="currentColor"
                    aria-hidden="true"
                  >
                    <path d="M11.645 20.91l-.007-.003-.022-.012a15.247 15.247 0 01-.383-.218 25.18 25.18 0 01-4.244-3.17C4.688 15.36 2.25 12.174 2.25 8.25 2.25 5.322 4.714 3 7.688 3A5.5 5.5 0 0112 5.052 5.5 5.5 0 0116.313 3c2.973 0 5.437 2.322 5.437 5.25 0 3.925-2.438 7.111-4.739 9.256a25.175 25.175 0 01-4.244 3.17 15.247 15.247 0 01-.383.219l-.022.012-.007.004-.003.001a.752.752 0 01-.704 0l-.003-.001z" />
                  </svg>
                </button>
              </div>
              <div class="p-4">
                <p class="text-xs font-semibold text-primary">
                  {{ product.brand }}
                </p>
                <h3 class="mt-1 min-h-10 text-sm font-bold leading-5 text-text-main">
                  {{ product.name }}
                </h3>
                <div class="mt-3 flex items-center justify-between">
                  <p class="text-base font-bold text-text-main">
                    ₩{{ product.price.toLocaleString('ko-KR') }}
                  </p>
                  <span
                    class="rounded-full px-2 py-1 text-xs font-semibold"
                    :class="verificationTierOf(product).badge"
                  >
                    {{ product.verified }}/{{ product.required }}
                  </span>
                </div>
              </div>
            </BaseCard>
          </div>

          <BaseCard
            v-else-if="!isLoadingMore"
            class="py-16 text-center"
          >
            <p class="font-semibold text-text-main">
              조건에 맞는 상품이 없습니다.
            </p>
            <p class="mt-2 text-sm text-text-sub">
              필터를 조정해서 다시 찾아보세요.
            </p>
          </BaseCard>

          <div
            ref="sentinel"
            class="mt-10 flex flex-col items-center gap-3 py-6"
          >
            <div
              v-if="isLoadingMore"
              class="h-8 w-8 animate-spin rounded-full bg-primary-gradient [mask:radial-gradient(farthest-side,transparent_calc(100%-3px),#000_calc(100%-3px))] [-webkit-mask:radial-gradient(farthest-side,transparent_calc(100%-3px),#000_calc(100%-3px))]"
            />
            <p
              v-if="isLoadingMore"
              class="text-xs text-text-sub"
            >
              고객님 마음에 꼭 드는 상품을 불러오는 중...
            </p>
            <p
              v-else-if="!hasMore && sortedProducts.length"
              class="text-xs text-text-sub"
            >
              모든 상품을 확인했습니다!
            </p>
          </div>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
