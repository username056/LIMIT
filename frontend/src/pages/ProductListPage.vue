<script setup>
import { onMounted, reactive, ref } from 'vue'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import { getDeviceCategories, getProducts } from '../api/products'

const products = ref([])
const categories = ref([])
const isLoading = ref(false)
const errorMessage = ref('')
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false })
const filters = reactive({ keyword: '', categoryId: '', minPrice: '', maxPrice: '', tradeRegion: '' })

async function search(page = 0) {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getProducts({ ...filters, page, size: 20, sort: 'createdAt,desc' })
    products.value = response?.data || []
    pageMeta.value = response?.meta || { page, totalPages: 0, hasNext: false }
  } catch (error) {
    errorMessage.value = error.message || '상품을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

function submitSearch() {
  search(0)
}

onMounted(async () => {
  try {
    categories.value = await getDeviceCategories({ activeOnly: true })
  } catch {
    categories.value = []
  }
  await search(0)
})
</script>

<template>
  <DefaultLayout>
    <main class="mx-auto max-w-[1200px] px-6 py-10 lg:px-10">
      <h1 class="text-2xl font-bold text-text-main">
        상품 둘러보기
      </h1>
      <form
        class="mt-6 grid gap-3 rounded-lg border border-border bg-surface p-5 md:grid-cols-5"
        @submit.prevent="submitSearch"
      >
        <input
          v-model.trim="filters.keyword"
          aria-label="상품명 검색"
          placeholder="상품명"
          class="rounded-md border border-border px-3 py-2 text-sm"
        >
        <select
          v-model="filters.categoryId"
          aria-label="카테고리"
          class="rounded-md border border-border px-3 py-2 text-sm"
        >
          <option value="">
            전체 카테고리
          </option>
          <option
            v-for="category in categories"
            :key="category.categoryId"
            :value="category.categoryId"
          >
            {{ category.name }}
          </option>
        </select>
        <input
          v-model="filters.minPrice"
          aria-label="최소 가격"
          type="number"
          min="0"
          placeholder="최소 가격"
          class="rounded-md border border-border px-3 py-2 text-sm"
        >
        <input
          v-model="filters.maxPrice"
          aria-label="최대 가격"
          type="number"
          min="0"
          placeholder="최대 가격"
          class="rounded-md border border-border px-3 py-2 text-sm"
        >
        <BaseButton type="submit">
          검색
        </BaseButton>
      </form>

      <p
        v-if="errorMessage"
        role="alert"
        class="mt-5 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ errorMessage }}
      </p>
      <p
        v-if="isLoading"
        class="py-16 text-center text-sm text-text-sub"
      >
        상품을 불러오는 중입니다.
      </p>
      <div
        v-else
        class="mt-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4"
      >
        <RouterLink
          v-for="product in products"
          :key="product.productId"
          :to="`/products/${product.productId}`"
          class="overflow-hidden rounded-lg border border-border bg-surface transition-shadow hover:shadow-elevated"
        >
          <div class="flex aspect-square items-center justify-center bg-bg text-sm text-text-sub">
            <img
              v-if="product.thumbnailUrl"
              :src="product.thumbnailUrl"
              :alt="product.name"
              class="h-full w-full object-cover"
            >
            <span v-else>상품 이미지</span>
          </div>
          <div class="p-4">
            <p class="text-xs text-text-sub">
              {{ product.manufacturerName }} · {{ product.modelName }}
            </p>
            <h2 class="mt-1 font-semibold text-text-main">
              {{ product.name }}
            </h2>
            <p class="mt-2 font-bold text-text-main">
              ₩{{ Number(product.price).toLocaleString() }}
            </p>
            <p class="mt-1 text-xs text-text-sub">
              {{ product.tradeRegion || '거래 지역 협의' }}
            </p>
          </div>
        </RouterLink>
      </div>
      <p
        v-if="!isLoading && !products.length"
        class="py-16 text-center text-sm text-text-sub"
      >
        조건에 맞는 판매 상품이 없습니다.
      </p>
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
          {{ pageMeta.page + 1 }} / {{ pageMeta.totalPages }}
        </span>
        <BaseButton
          variant="outline"
          :disabled="!pageMeta.hasNext"
          @click="search(pageMeta.page + 1)"
        >
          다음
        </BaseButton>
      </nav>
    </main>
  </DefaultLayout>
</template>
