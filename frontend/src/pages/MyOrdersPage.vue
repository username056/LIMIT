<script setup>
import { ref } from 'vue'
import SidebarLayout from '../layouts/SidebarLayout.vue'
import BaseTabs from '../components/BaseTabs.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'

const sidebarItems = [
  { label: '주문 내역', active: true },
  { label: '관심 상품', active: false },
  { label: '프로필 설정', active: false },
]

const activeTab = ref('전체')

// 예시 데이터입니다. 실제 연동 시 API 응답으로 교체하세요.
const orders = [
  {
    name: "Air Jordan 1 Retro High OG 'White Cement'",
    date: '2024.05.28',
    id: '#OR20240528-001',
    price: '249,000',
    status: '배송완료',
  },
  {
    name: "Yeezy Boost 350 V2 'Slate'",
    date: '2024.05.20',
    id: '#OR20240520-004',
    price: '229,000',
    status: '배송중',
  },
  {
    name: "New Balance 990v6 Made in USA 'Grey'",
    date: '2024.05.12',
    id: '#OR20240512-002',
    price: '299,000',
    status: '배송완료',
  },
]
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    <h1 class="mb-4 text-lg font-bold text-text-main">
      주문 내역 조회
    </h1>

    <BaseTabs
      v-model="activeTab"
      :tabs="['전체', '결제완료', '배송중', '취소/환불']"
      class="mb-5"
    />

    <ul class="space-y-3">
      <li
        v-for="order in orders"
        :key="order.id"
        class="flex items-center gap-4 rounded-lg border border-border bg-surface p-4"
      >
        <div class="h-14 w-14 shrink-0 rounded-md bg-bg" />
        <div class="min-w-0 flex-1">
          <BaseBadge
            :variant="order.status === '배송완료' ? 'gray' : 'primary'"
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
          <BaseButton variant="outline">
            주문 조회
          </BaseButton>
        </div>
      </li>
    </ul>
  </SidebarLayout>
</template>
