<script setup>
import { onMounted, ref } from 'vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import ProductCard from '../components/ProductCard.vue'
import { getMyFavorites, removeFavorite as removeFavoriteRequest } from '../api/favorites'
import { productStatusLabel } from '../utils/productStatus'

const favorites = ref([])
const isLoading = ref(true)
const errorMessage = ref('')
const removingProductIds = ref(new Set())
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false })

async function loadFavorites(page = 0) {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const response = await getMyFavorites({ page, size: 20 })
    favorites.value = response?.data || []
    pageMeta.value = response?.meta || { page, totalPages: 0, hasNext: false }
  } catch (error) {
    errorMessage.value = error.message || '좋아요한 상품을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

async function removeFavorite(product) {
  const next = new Set(removingProductIds.value)
  next.add(product.productId)
  removingProductIds.value = next
  errorMessage.value = ''

  try {
    await removeFavoriteRequest(product.productId)
    const targetPage = favorites.value.length === 1 && pageMeta.value.page > 0
      ? pageMeta.value.page - 1
      : pageMeta.value.page
    await loadFavorites(targetPage)
  } catch (error) {
    errorMessage.value = error.message || '좋아요한 상품을 해제하지 못했습니다.'
  } finally {
    const remaining = new Set(removingProductIds.value)
    remaining.delete(product.productId)
    removingProductIds.value = remaining
  }
}

onMounted(() => loadFavorites(0))
</script>

<template>
  <MyPageLayout>
    <div class="mb-6">
      <p class="text-xs font-semibold text-primary">
        MY FAVORITES
      </p>
      <h1 class="mt-2 text-2xl font-bold text-text-main">
        좋아요한 상품
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        저장한 상품의 판매 상태와 가격을 한곳에서 확인하세요.
      </p>
    </div>

    <BaseCard
      v-if="isLoading"
      class="py-16 text-center"
      aria-live="polite"
    >
      <p class="font-semibold text-text-main">
        좋아요한 상품을 불러오는 중입니다.
      </p>
    </BaseCard>

    <BaseCard
      v-else-if="errorMessage && !favorites.length"
      class="py-16 text-center"
      role="alert"
    >
      <p class="font-semibold text-text-main">
        {{ errorMessage }}
      </p>
      <BaseButton
        class="mt-6"
        @click="loadFavorites(pageMeta.page)"
      >
        다시 시도
      </BaseButton>
    </BaseCard>

    <div
      v-else-if="favorites.length"
      class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
    >
      <!-- 상품 목록과 같은 카드를 씁니다. 대표 이미지가 어느 화면에서든 같게 보여야 합니다. -->
      <ProductCard
        v-for="product in favorites"
        :key="product.favoriteId"
        :product="product"
      >
        <template #image-overlay>
          <BaseBadge
            variant="gray"
            class="absolute left-3 top-3"
          >
            {{ productStatusLabel(product.status) }}
          </BaseBadge>
        </template>
        <template #footer>
          <div class="flex gap-2">
            <BaseButton
              class="flex-1 px-3"
              :to="{ name: 'product-detail', params: { productId: product.productId } }"
            >
              상품 보기
            </BaseButton>
            <BaseButton
              variant="outline"
              class="px-3"
              :aria-label="`${product.name} 좋아요 해제`"
              :disabled="removingProductIds.has(product.productId)"
              @click="removeFavorite(product)"
            >
              {{ removingProductIds.has(product.productId) ? '해제 중' : '해제' }}
            </BaseButton>
          </div>
        </template>
      </ProductCard>
    </div>

    <nav
      v-if="!isLoading && pageMeta.totalPages > 1"
      class="mt-6 flex items-center justify-center gap-4"
      aria-label="좋아요한 상품 페이지"
    >
      <BaseButton
        variant="outline"
        :disabled="pageMeta.page === 0"
        @click="loadFavorites(pageMeta.page - 1)"
      >
        이전
      </BaseButton>
      <span class="text-sm text-text-sub">
        {{ pageMeta.page + 1 }} / {{ pageMeta.totalPages }}
      </span>
      <BaseButton
        variant="outline"
        :disabled="!pageMeta.hasNext"
        @click="loadFavorites(pageMeta.page + 1)"
      >
        다음
      </BaseButton>
    </nav>

    <BaseCard
      v-if="!isLoading && !errorMessage && !favorites.length"
      class="py-16 text-center"
    >
      <p class="font-semibold text-text-main">
        좋아요한 상품이 없습니다.
      </p>
      <p class="mt-2 text-sm text-text-sub">
        둘러보기에서 마음에 드는 상품을 저장해 보세요.
      </p>
      <BaseButton
        to="/"
        class="mt-6"
      >
        상품 둘러보기
      </BaseButton>
    </BaseCard>

    <p
      v-if="errorMessage && favorites.length"
      class="mt-4 text-sm font-semibold text-red-600"
      role="alert"
    >
      {{ errorMessage }}
    </p>
  </MyPageLayout>
</template>
