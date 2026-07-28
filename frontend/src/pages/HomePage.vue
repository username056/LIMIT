<script setup>
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

// 예시 데이터입니다. 실제 연동 시 API 응답으로 교체하세요.
const categories = [
  { label: '노트북 / 랩탑' },
  { label: '스마트폰 / 모바일' },
  { label: '태블릿 / 패드' },
  { label: '카메라' },
]

const steps = [
  {
    number: '01',
    title: '자가 검수 체크 리스트',
    description: '기기별 검증 체크 리스트가 있어 기기 성능 및 외관 검수를 확인할 수 있습니다.',
  },
  {
    number: '02',
    title: '실시간 채팅 및 WebRTC 화상 연결',
    description: '판매자 내 사진이랑 영상으로 부족하다면 구매자와 판매자를 연결하는 1대1 채팅과 화상 연결을 이용해보세요!',
  },
]

const products = [
  {
    brand: 'Samsung',
    category: '노트북 / 랩탑',
    name: 'Galaxy Book4 Pro',
    price: '1,890,000',
  },
  {
    brand: 'Samsung',
    category: '스마트폰 / 모바일',
    name: 'Galaxy S24 Ultra',
    price: '1,050,000',
  },
  {
    brand: 'Samsung',
    category: '태블릿 / 패드',
    name: 'Galaxy Tab S9 Ultra',
    price: '920,000',
  },
  {
    brand: 'Samsung',
    category: '이어폰 / 오디오',
    name: 'Galaxy Buds3 Pro',
    price: '290,000',
  },
]
</script>

<template>
  <DefaultLayout>
    <!-- Hero -->
    <section class="mx-auto max-w-[1200px] px-6 pt-8 lg:px-10">
      <div class="grid items-center gap-10 overflow-hidden rounded-lg bg-accent px-8 py-12 lg:grid-cols-2 lg:px-14 lg:py-16">
        <div>
          <BaseBadge class="mb-4">
            실시간 화상 확인
          </BaseBadge>
          <h1 class="mb-4 text-3xl font-bold leading-snug text-text-main lg:text-4xl">
            검증된 중고 전자기기,<br>
            실시간 화상으로도 확인하세요!
          </h1>
          <p class="mb-8 max-w-md text-sm leading-relaxed text-text-sub">
            판매자가 올린 체크리스트 자료를 눈으로 직접 검증하고
            WebRTC 화상 채팅을 통해 제품 작동 상태를 1:1로 확인하는 중고 거래 플랫폼.
          </p>
          <BaseButton to="/#products">
            상품 둘러보기
          </BaseButton>
        </div>

        <div class="flex aspect-[4/3] items-center justify-center rounded-lg bg-surface text-text-sub shadow-elevated lg:aspect-[16/11]">
          실시간 화상 검수 이미지
        </div>
      </div>
    </section>

    <!-- Categories -->
    <section class="mx-auto max-w-[1200px] px-6 py-12 lg:px-10">
      <h2 class="mb-5 text-lg font-bold text-text-main">
        인기 전자기기 카테고리
      </h2>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <RouterLink
          v-for="category in categories"
          :key="category.label"
          :to="{ name: 'coming-soon', params: { feature: 'category' }, query: { name: category.label } }"
          class="group overflow-hidden rounded-lg border border-border bg-surface transition-shadow hover:shadow-elevated"
        >
          <div class="aspect-square bg-text-main" />
          <div class="flex items-center justify-between px-4 py-3">
            <span class="text-sm font-semibold text-text-main">{{ category.label }}</span>
          </div>
        </RouterLink>
      </div>
    </section>

    <!-- Verification process -->
    <section class="mx-auto max-w-[1200px] px-6 pb-12 lg:px-10">
      <BaseCard class="p-8 lg:p-10">
        <h2 class="mb-1 text-lg font-bold text-text-main">
          안전한 LIMIT 검증 프로세스
        </h2>
        <p class="mb-8 text-sm text-text-sub">
          투명하고 안전한 고가 전자기기 구매를 위해 리미트는 해당 서비스를 제공합니다
        </p>

        <div class="grid gap-8 sm:grid-cols-2">
          <div
            v-for="step in steps"
            :key="step.number"
          >
            <p class="mb-2 bg-primary-gradient bg-clip-text text-3xl font-extrabold text-transparent">
              {{ step.number }}
            </p>
            <h3 class="mb-2 text-base font-bold text-text-main">
              {{ step.title }}
            </h3>
            <p class="text-sm leading-relaxed text-text-sub">
              {{ step.description }}
            </p>
          </div>
        </div>
      </BaseCard>
    </section>

    <!-- Products -->
    <section
      id="products"
      class="mx-auto max-w-[1200px] scroll-mt-20 px-6 pb-16 lg:px-10"
    >
      <div class="mb-5 flex items-center justify-between">
        <h2 class="text-lg font-bold text-text-main">
          최근 올라온 실시간 확인 가능 상품
        </h2>
        <RouterLink
          :to="{ name: 'products' }"
          class="text-sm font-semibold text-primary hover:underline"
        >
          전체 상품 보기
        </RouterLink>
      </div>

      <div class="grid grid-cols-2 gap-6 lg:grid-cols-4">
        <RouterLink
          v-for="product in products"
          :key="product.name"
          :to="{ name: 'products' }"
          class="group overflow-hidden rounded-lg border border-border bg-surface transition-shadow hover:shadow-elevated"
        >
          <div class="flex aspect-square items-center justify-center bg-bg text-xs text-text-sub">
            상품 이미지
          </div>
          <div class="p-4">
            <p class="text-xs text-text-sub">
              {{ product.brand }}
            </p>
            <p class="mt-1 text-sm font-semibold text-text-main">
              {{ product.name }}
            </p>
            <p class="mt-1 text-sm font-bold text-text-main">
              ₩{{ product.price }}
            </p>
            <span class="mt-2 inline-block text-xs font-semibold text-primary">
              보러가기 →
            </span>
          </div>
        </RouterLink>
      </div>
    </section>
  </DefaultLayout>
</template>
