<script setup>
import { ref } from 'vue'
import SidebarLayout from '../layouts/SidebarLayout.vue'
import StatCard from '../components/StatCard.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseTable from '../components/BaseTable.vue'
import BasePagination from '../components/BasePagination.vue'
import BarChart from '../components/charts/BarChart.vue'
import DonutChart from '../components/charts/DonutChart.vue'
import BaseButton from '../components/BaseButton.vue'

const sidebarItems = [
  { label: '대시보드', href: '/seller/dashboard', active: false },
  { label: '상품 관리', href: '/seller/products', active: false },
  { label: '주문 관리', href: '/coming-soon/seller-orders', active: false },
  { label: '정산 및 통계', href: '/seller/dashboard', active: true },
  { label: '설정', href: '/coming-soon/seller-settings', active: false },
]

// 예시 데이터입니다. 실제 연동 시 API 응답으로 교체하세요.
const settlements = [
  { name: "Nike Dunk Low 'Panda'", date: '2024.05.28', amount: '8,250,000', status: '정산완료' },
  { name: 'Jordan 1 Retro High OG', date: '2024.05.20', amount: '15,400,000', status: '정산완료' },
  { name: "New Balance 990v6 'Grey'", date: '2024.06.15', amount: '12,400,000', status: '보류' },
  { name: 'Adidas Yeezy Boost 350 V2', date: '2024.05.12', amount: '19,200,000', status: '정산완료' },
]

const currentPage = ref(1)
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    <p class="mb-1 text-xs font-bold uppercase tracking-[0.16em] text-primary">
      SELLER CENTER
    </p>
    <h1 class="mb-1 text-lg font-bold text-text-main">
      정산 및 통계
    </h1>
    <p class="mb-6 text-sm text-text-sub">
      실시간 판매 데이터와 정산 현황을 확인하세요.
    </p>

    <div class="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
      <StatCard
        label="정산 완료"
        value="₩42,850,000"
        trend="전월 대비 +8%"
      />
      <StatCard
        label="정산 예정"
        value="₩12,400,000"
        trend="이번 주 정산 2건"
      />
      <StatCard
        label="총 드롭 참여"
        value="14,208명"
        trend="전월 대비 +12%"
      />
    </div>

    <div class="mb-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="rounded-lg border border-border bg-surface p-5 lg:col-span-2">
        <p class="mb-4 text-sm font-bold text-text-main">
          드롭 참여 통계
        </p>
        <BarChart
          :labels="['05.24', '05.25', '05.26', '05.27', '05.28', '05.29']"
          :values="[12, 18, 25, 20, 30, 22]"
        />
      </div>
      <div class="flex flex-col items-center justify-center rounded-lg bg-primary-gradient p-5 text-white">
        <p class="mb-3 text-sm font-semibold">
          구매 전환율
        </p>
        <DonutChart :percent="72" />
        <p class="mt-3 text-center text-xs opacity-90">
          모의 경쟁 대비 전환 우수 (124:1 → 4.2%)
        </p>
      </div>
    </div>

    <div>
      <p class="mb-3 text-sm font-bold text-text-main">
        정산 내역 상세
      </p>
      <BaseTable :columns="['드롭 명', '정산 예정일', '정산 금액', '상태', '']">
        <tr
          v-for="item in settlements"
          :key="item.name"
        >
          <td class="px-4 py-3 text-sm text-text-main">
            {{ item.name }}
          </td>
          <td class="px-4 py-3 text-sm text-text-sub">
            {{ item.date }}
          </td>
          <td class="px-4 py-3 text-sm font-semibold text-text-main">
            ₩{{ item.amount }}
          </td>
          <td class="px-4 py-3">
            <BaseBadge :variant="item.status === '정산완료' ? 'primary' : 'gray'">
              {{ item.status }}
            </BaseBadge>
          </td>
          <td class="px-4 py-3 text-right text-text-sub">
            <BaseButton
              variant="ghost"
              class="px-2 py-1"
              :to="{ name: 'coming-soon', params: { feature: 'settlement' }, query: { name: item.name } }"
              :aria-label="`${item.name} 정산 상세 보기`"
            >
              ›
            </BaseButton>
          </td>
        </tr>
      </BaseTable>

      <div class="mt-4">
        <BasePagination
          v-model:current-page="currentPage"
          :total-pages="3"
        />
      </div>
    </div>
  </SidebarLayout>
</template>
