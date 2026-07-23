<script setup>
import { ref } from 'vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

const favorites = ref([
  {
    id: 1,
    brand: 'Apple',
    name: "iPhone 14 Pro 'Space Black'",
    price: '890,000',
    condition: 'A급',
  },
  {
    id: 2,
    brand: 'Samsung',
    name: "Galaxy Tab S9 'Graphite'",
    price: '620,000',
    condition: '미개봉',
  },
  {
    id: 3,
    brand: 'Sony',
    name: "WH-1000XM5 'Black'",
    price: '289,000',
    condition: 'A급',
  },
])

function removeFavorite(productId) {
  favorites.value = favorites.value.filter((product) => product.id !== productId)
}
</script>

<template>
  <MyPageLayout>
    <div class="mb-6">
      <p class="text-xs font-semibold text-primary">
        WIREFRAME MOCK
      </p>
      <h1 class="mt-2 text-2xl font-bold text-text-main">
        관심 상품
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        관심 상품 API가 연결되기 전까지 화면 흐름을 확인할 수 있는 목업 데이터입니다.
      </p>
    </div>

    <div
      v-if="favorites.length"
      class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
    >
      <BaseCard
        v-for="product in favorites"
        :key="product.id"
        :padded="false"
        class="overflow-hidden"
      >
        <div class="flex aspect-[4/3] items-center justify-center bg-bg text-xs text-text-sub">
          상품 이미지
        </div>
        <div class="p-5">
          <div class="flex items-center justify-between gap-3">
            <span class="text-xs font-semibold text-primary">{{ product.brand }}</span>
            <BaseBadge variant="gray">
              {{ product.condition }}
            </BaseBadge>
          </div>
          <h2 class="mt-3 min-h-10 text-sm font-bold leading-5 text-text-main">
            {{ product.name }}
          </h2>
          <p class="mt-2 text-base font-bold text-text-main">
            {{ product.price }}원
          </p>
          <div class="mt-5 flex gap-2">
            <BaseButton
              class="flex-1 px-3"
              :to="{ name: 'coming-soon', params: { feature: 'product-detail' }, query: { name: product.name } }"
            >
              상품 보기
            </BaseButton>
            <BaseButton
              variant="outline"
              class="px-3"
              :aria-label="`${product.name} 관심 상품 해제`"
              @click="removeFavorite(product.id)"
            >
              해제
            </BaseButton>
          </div>
        </div>
      </BaseCard>
    </div>

    <BaseCard
      v-else
      class="py-16 text-center"
    >
      <p class="font-semibold text-text-main">
        관심 상품이 없습니다.
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
  </MyPageLayout>
</template>
