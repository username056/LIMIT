<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import { getDeviceCategories } from '../api/products'

const route = useRoute()
const router = useRouter()
const showForbiddenNotice = ref(false)

onMounted(() => {
  if (route.query.notice !== 'forbidden') return
  showForbiddenNotice.value = true
  const { notice, ...rest } = route.query
  router.replace({ query: rest })
})

// 실제 판매 카테고리를 그대로 보여줍니다. 하위 기종은 빼고 최상위 카테고리만 노출합니다.
const categories = ref([])

onMounted(async () => {
  try {
    const items = await getDeviceCategories({ activeOnly: true })
    categories.value = (items || []).slice(0, 4)
  } catch {
    categories.value = []
  }
})

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
      <div
        v-if="categories.length"
        class="grid grid-cols-2 gap-4 sm:grid-cols-4"
      >
        <RouterLink
          v-for="category in categories"
          :key="category.categoryId"
          :to="{ name: 'products', query: { categoryId: category.categoryId } }"
          class="group overflow-hidden rounded-lg border border-border bg-surface transition-shadow hover:shadow-elevated"
        >
          <div class="aspect-square bg-text-main" />
          <div class="flex items-center justify-between px-4 py-3">
            <span class="text-sm font-semibold text-text-main">{{ category.name }}</span>
          </div>
        </RouterLink>
      </div>
      <p
        v-else
        class="rounded-lg border border-border bg-surface px-4 py-10 text-center text-sm text-text-sub"
      >
        카테고리를 불러오지 못했습니다.
      </p>
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

    <div
      v-if="showForbiddenNotice"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
    >
      <BaseCard class="w-full max-w-sm text-center">
        <h2 class="text-lg font-bold text-text-main">
          해당 페이지에 권한이 없습니다
        </h2>
        <p class="mt-2 text-sm text-text-sub">
          메인 페이지로 돌아갑니다.
        </p>
        <BaseButton
          block
          class="mt-5"
          @click="showForbiddenNotice = false"
        >
          확인
        </BaseButton>
      </BaseCard>
    </div>
  </DefaultLayout>
</template>
