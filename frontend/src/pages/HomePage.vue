<script setup>
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'

// 예시 데이터입니다. 실제 연동 시 API 응답으로 교체하세요.
const products = [
  {
    category: '휴대폰',
    brand: 'Apple',
    name: "iPhone 14 Pro 'Space Black'",
    price: '890,000',
  },
  {
    category: '태블릿',
    brand: 'Samsung',
    name: "Galaxy Tab S9 'Graphite'",
    price: '620,000',
  },
  {
    category: '노트북',
    brand: 'Apple',
    name: "MacBook Air M2 'Starlight'",
    price: '1,290,000',
  },
]

const rankings = [
  { rank: '01', name: "iPhone 14 Pro 'Space Black'", price: '890,000', trend: 'up' },
  { rank: '02', name: "MacBook Air M2 'Starlight'", price: '1,290,000', trend: 'up' },
  { rank: '03', name: "Sony A7 IV 'Black'", price: '1,650,000', trend: 'flat' },
]
</script>

<template>
  <DefaultLayout>
    <!-- Hero -->
    <section class="mx-auto max-w-6xl px-6 pt-8">
      <div class="relative overflow-hidden rounded-lg border border-border bg-bg px-10 py-16">
        <p class="mb-2 text-xs font-semibold text-primary">
          단독 한정 상품
        </p>
        <h1 class="mb-3 text-3xl font-bold text-text-main">
          믿을 수 있는 중고 전자기기 마켓
        </h1>
        <p class="mb-6 max-w-md text-sm text-text-sub">
          꼼꼼한 검수를 거친 인기 중고 전자기기를 지금 만나보세요.
        </p>
        <BaseButton to="/#products">
          지금 둘러보기
        </BaseButton>
      </div>
    </section>

    <!-- Products + Ranking -->
    <section
      id="products"
      class="mx-auto max-w-6xl scroll-mt-20 px-6 py-10"
    >
      <div class="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <!-- Product cards -->
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-3 lg:col-span-9">
          <RouterLink
            v-for="product in products"
            :key="product.name"
            :to="{ name: 'coming-soon', params: { feature: 'product-detail' }, query: { name: product.name } }"
            class="group overflow-hidden rounded-lg border border-border bg-surface transition-shadow hover:shadow-elevated"
          >
            <div class="flex aspect-square items-center justify-center bg-bg text-xs text-text-sub">
              상품 이미지
            </div>
            <div class="p-4">
              <p class="text-xs text-text-sub">
                {{ product.brand }} · {{ product.category }}
              </p>
              <p class="mt-1 text-sm font-semibold text-text-main">
                {{ product.name }}
              </p>
              <p class="mt-1 text-sm font-bold text-text-main">
                {{ product.price }} KRW
              </p>
            </div>
          </RouterLink>
        </div>

        <!-- Ranking sidebar -->
        <aside class="rounded-lg border border-border bg-surface p-5 lg:col-span-3">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="text-sm font-bold text-text-main">
              인기 랭킹
            </h2>
            <span class="flex items-center gap-1 text-xs text-text-sub">
              <span class="h-1.5 w-1.5 rounded-full bg-primary" />
              실시간
            </span>
          </div>

          <ul class="space-y-4">
            <li
              v-for="item in rankings"
              :key="item.rank"
              class="flex items-center gap-3"
            >
              <span class="w-5 text-sm font-bold text-text-sub">{{ item.rank }}</span>
              <div class="h-10 w-10 shrink-0 rounded-md bg-bg" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-xs font-medium text-text-main">
                  {{ item.name }}
                </p>
                <p class="text-xs text-text-sub">
                  {{ item.price }} KRW
                </p>
              </div>
              <span
                v-if="item.trend === 'up'"
                class="text-xs text-primary"
              >▲</span>
              <span
                v-else
                class="text-xs text-text-sub"
              >–</span>
            </li>
          </ul>

          <BaseButton
            block
            to="/coming-soon/ranking"
            variant="outline"
            class="mt-5 py-2 text-xs"
          >
            전체 랭킹 보기
          </BaseButton>
        </aside>
      </div>
    </section>
  </DefaultLayout>
</template>
