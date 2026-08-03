<script setup>
// 구매자가 판매자를 눌러서 들어오는 화면입니다. 그 사람이 지금 팔고 있는 상품만 보여 줍니다.
// 카드는 상품 목록과 같은 ProductCard를 써서 어느 화면에서 봐도 같게 보이게 합니다.
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import ProductCard from '../components/ProductCard.vue'
import { getSellerProfile } from '../api/seller'
import { getProducts } from '../api/products'

const PAGE_SIZE = 12

const route = useRoute()
const sellerId = computed(() => Number(route.params.sellerId))
const profile = ref(null)
const products = ref([])
const pageMeta = ref({ page: 0, totalPages: 0, hasNext: false })
const isLoading = ref(true)
const errorMessage = ref('')

// 판매자 등록 행이 없는 회원이면 sellerType이 비어 옵니다. 그때는 구분 배지를 감춥니다.
const sellerTypeLabel = computed(() => {
  if (!profile.value?.sellerType) return ''
  return profile.value.sellerType === 'BUSINESS' ? '사업자 판매자' : '개인 판매자'
})
const joinedLabel = computed(() => {
  if (!profile.value?.joinedAt) return ''
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' })
    .format(new Date(profile.value.joinedAt))
})
const initial = computed(() => (profile.value?.nickname || '판').trim().charAt(0))

async function loadProducts(page = 0) {
  const response = await getProducts({
    sellerId: sellerId.value,
    page,
    size: PAGE_SIZE,
    sort: 'createdAt,desc',
  })
  products.value = response?.data || []
  pageMeta.value = response?.meta || { page, totalPages: 0, hasNext: false }
}

onMounted(async () => {
  try {
    profile.value = await getSellerProfile(sellerId.value)
    await loadProducts(0)
  } catch (error) {
    errorMessage.value = error.message || '판매자 정보를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <DefaultLayout>
    <main class="page-shell">
      <p
        v-if="isLoading"
        class="card-soft rounded-lg bg-surface px-6 py-16 text-center text-sm text-text-sub"
      >
        판매자 정보를 불러오는 중입니다.
      </p>

      <div
        v-else-if="errorMessage"
        class="card-soft rounded-lg bg-surface px-6 py-16 text-center"
      >
        <p
          role="alert"
          class="text-sm text-red-700"
        >
          {{ errorMessage }}
        </p>
        <BaseButton
          class="mt-6"
          variant="outline"
          to="/products"
        >
          전체 상품으로
        </BaseButton>
      </div>

      <template v-else-if="profile">
        <section class="flex flex-wrap items-center gap-4 card-soft rounded-lg bg-surface p-6">
          <span
            class="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-primary-gradient text-2xl font-bold text-white"
            aria-hidden="true"
          >{{ initial }}</span>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <h1 class="truncate text-xl font-bold text-text-main">
                {{ profile.nickname }}
              </h1>
              <BaseBadge
                v-if="sellerTypeLabel"
                variant="gray"
              >
                {{ sellerTypeLabel }}
              </BaseBadge>
            </div>
            <p class="mt-1 text-sm text-text-sub">
              판매 중 {{ profile.onSaleCount }}개<span v-if="joinedLabel"> · {{ joinedLabel }} 등록</span>
            </p>
          </div>
        </section>

        <section class="mt-8">
          <h2 class="mb-4 text-lg font-bold text-text-main">
            판매 중인 상품
          </h2>

          <div
            v-if="products.length"
            class="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3"
          >
            <ProductCard
              v-for="product in products"
              :key="product.productId"
              :product="product"
              :to="{ name: 'product-detail', params: { productId: product.productId } }"
            />
          </div>
          <p
            v-else
            class="rounded-lg border border-dashed border-border bg-surface px-6 py-16 text-center text-sm text-text-sub"
          >
            지금 판매 중인 상품이 없습니다.
          </p>

          <nav
            v-if="pageMeta.totalPages > 1"
            class="mt-6 flex items-center justify-center gap-4"
            aria-label="판매자 상품 페이지"
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
        </section>
      </template>
    </main>
  </DefaultLayout>
</template>
