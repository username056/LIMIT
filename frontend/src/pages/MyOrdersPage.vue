<script setup>
import { computed, ref } from 'vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import BaseTabs from '../components/BaseTabs.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'

const activeTab = ref('전체')
const selectedOrder = ref(null)

// 예시 데이터입니다. 실제 연동 시 API 응답으로 교체하세요.
const orders = [
  {
    name: "Air Jordan 1 Retro High OG 'White Cement'",
    date: '2024.05.28',
    id: '#OR20240528-001',
    price: '249,000',
    status: '배송완료',
    delivery: '2024.05.30 배송 완료',
  },
  {
    name: "Yeezy Boost 350 V2 'Slate'",
    date: '2024.05.20',
    id: '#OR20240520-004',
    price: '229,000',
    status: '배송중',
    delivery: '2024.05.23 집화 완료',
  },
  {
    name: "New Balance 990v6 Made in USA 'Grey'",
    date: '2024.05.12',
    id: '#OR20240512-002',
    price: '299,000',
    status: '결제완료',
    delivery: '판매자 발송 대기',
  },
  {
    name: "Sony WH-1000XM5 'Black'",
    date: '2024.05.02',
    id: '#OR20240502-003',
    price: '289,000',
    status: '취소/환불',
    delivery: '2024.05.03 결제 취소',
  },
]

const visibleOrders = computed(() => {
  if (activeTab.value === '전체') return orders
  return orders.filter((order) => order.status === activeTab.value)
})
</script>

<template>
  <MyPageLayout>
    <div class="mb-5">
      <p class="text-xs font-semibold text-primary">
        MY PAGE
      </p>
      <h1 class="mt-2 text-2xl font-bold text-text-main">
        주문 내역
      </h1>
      <p class="mt-2 text-sm text-text-sub">
        최근 주문한 상품의 결제와 배송 상태를 확인하세요.
      </p>
    </div>

    <BaseTabs
      v-model="activeTab"
      :tabs="['전체', '결제완료', '배송중', '취소/환불']"
      class="mb-5"
    />

    <ul class="space-y-3">
      <li
        v-for="order in visibleOrders"
        :key="order.id"
        class="flex items-center gap-4 rounded-lg border border-border bg-surface p-4"
      >
        <div class="h-14 w-14 shrink-0 rounded-md bg-bg" />
        <div class="min-w-0 flex-1">
          <BaseBadge
            :variant="order.status === '배송중' || order.status === '결제완료' ? 'primary' : 'gray'"
            class="mb-1"
          >
            {{ order.status }}
          </BaseBadge>
          <p class="truncate text-sm font-semibold text-text-main">
            {{ order.name }}
          </p>
          <p class="text-xs text-text-sub">
            {{ order.date }} · {{ order.id }}
          </p>
        </div>
        <div class="text-right">
          <p class="mb-2 text-sm font-bold text-text-main">
            {{ order.price }}원
          </p>
          <BaseButton
            variant="outline"
            @click="selectedOrder = order"
          >
            주문 조회
          </BaseButton>
        </div>
      </li>
    </ul>

    <BaseCard
      v-if="!visibleOrders.length"
      class="py-14 text-center"
    >
      <p class="font-semibold text-text-main">
        해당 상태의 주문이 없습니다.
      </p>
    </BaseCard>

    <BaseCard
      v-if="selectedOrder"
      class="mt-6"
    >
      <div class="flex items-start justify-between gap-4">
        <div>
          <p class="text-xs font-semibold text-primary">
            주문 상세
          </p>
          <h2 class="mt-2 text-lg font-bold text-text-main">
            {{ selectedOrder.name }}
          </h2>
          <p class="mt-1 text-sm text-text-sub">
            {{ selectedOrder.id }} · {{ selectedOrder.date }}
          </p>
        </div>
        <button
          type="button"
          class="text-sm font-semibold text-text-sub hover:text-text-main"
          aria-label="주문 상세 닫기"
          @click="selectedOrder = null"
        >
          닫기
        </button>
      </div>
      <div class="mt-5 grid gap-4 rounded-lg bg-bg p-5 text-sm sm:grid-cols-3">
        <div>
          <p class="text-xs text-text-sub">
            주문 상태
          </p>
          <p class="mt-1 font-bold text-text-main">
            {{ selectedOrder.status }}
          </p>
        </div>
        <div>
          <p class="text-xs text-text-sub">
            결제 금액
          </p>
          <p class="mt-1 font-bold text-text-main">
            {{ selectedOrder.price }}원
          </p>
        </div>
        <div>
          <p class="text-xs text-text-sub">
            배송 정보
          </p>
          <p class="mt-1 font-bold text-text-main">
            {{ selectedOrder.delivery }}
          </p>
        </div>
      </div>
    </BaseCard>
  </MyPageLayout>
</template>
