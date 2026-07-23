<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

const route = useRoute()
const router = useRouter()

const features = {
  chat: {
    eyebrow: '채팅',
    title: '채팅 기능을 준비하고 있습니다',
    description: '거래 상대와 안전하게 대화할 수 있는 채팅 화면이 연결될 예정입니다.',
  },
  'product-search': {
    eyebrow: '상품 검색',
    title: '통합 상품 검색을 준비하고 있습니다',
    description: '상품명, 브랜드, 카테고리 필터를 한곳에서 사용할 수 있도록 준비 중입니다.',
  },
  'product-detail': {
    eyebrow: '상품 상세',
    title: '상품 상세 화면을 준비하고 있습니다',
    description: '상품 상태, 판매자 정보와 거래 조건을 확인할 수 있는 화면이 연결될 예정입니다.',
  },
  ranking: {
    eyebrow: '인기 랭킹',
    title: '전체 랭킹을 준비하고 있습니다',
    description: '기간과 카테고리별 인기 상품 순위를 확인할 수 있도록 준비 중입니다.',
  },
  'seller-orders': {
    eyebrow: '판매자 센터',
    title: '주문 관리 화면을 준비하고 있습니다',
    description: '판매 주문의 결제, 배송과 취소 상태를 관리할 수 있도록 준비 중입니다.',
  },
  'seller-settings': {
    eyebrow: '판매자 센터',
    title: '판매자 설정을 준비하고 있습니다',
    description: '스토어와 알림 설정을 안전하게 관리할 수 있도록 준비 중입니다.',
  },
  'product-history': {
    eyebrow: '상품 관리',
    title: '상품 변경 이력을 준비하고 있습니다',
    description: '가격과 노출 상태 변경 내역을 확인할 수 있도록 준비 중입니다.',
  },
  'product-edit': {
    eyebrow: '상품 관리',
    title: '상품 수정 화면을 준비하고 있습니다',
    description: '등록 상품의 정보와 판매 상태를 수정할 수 있도록 준비 중입니다.',
  },
  settlement: {
    eyebrow: '정산',
    title: '정산 상세 화면을 준비하고 있습니다',
    description: '정산 예정 금액과 지급 내역을 확인할 수 있도록 준비 중입니다.',
  },
}

const content = computed(() => features[route.params.feature] || {
  eyebrow: 'L1MIT',
  title: '요청한 화면을 준비하고 있습니다',
  description: '연결할 API와 화면이 준비되는 대로 이 경로에서 바로 이용할 수 있습니다.',
})

const context = computed(() => route.query.name || route.query.q || '')
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto flex min-h-[560px] max-w-2xl items-center px-6 py-16">
      <BaseCard class="w-full p-8 text-center sm:p-12">
        <span class="inline-flex rounded-full bg-accent px-3 py-1 text-xs font-bold text-primary">
          {{ content.eyebrow }} · 준비 중
        </span>
        <h1 class="mt-5 text-2xl font-bold tracking-[-0.03em] text-text-main sm:text-3xl">
          {{ content.title }}
        </h1>
        <p class="mx-auto mt-4 max-w-lg text-sm leading-6 text-text-sub">
          {{ content.description }}
        </p>
        <p
          v-if="context"
          class="mx-auto mt-5 max-w-lg rounded-md bg-bg px-4 py-3 text-sm font-semibold text-text-main"
        >
          요청 항목: {{ context }}
        </p>
        <div class="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
          <BaseButton
            variant="outline"
            @click="router.back()"
          >
            이전 화면
          </BaseButton>
          <BaseButton to="/">
            홈으로 이동
          </BaseButton>
        </div>
      </BaseCard>
    </section>
  </DefaultLayout>
</template>
