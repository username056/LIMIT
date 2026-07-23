<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import SidebarLayout from '../layouts/SidebarLayout.vue'
import BaseSelect from '../components/BaseSelect.vue'
import BaseDateRange from '../components/BaseDateRange.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseBadge from '../components/BaseBadge.vue'
import BaseToggle from '../components/BaseToggle.vue'
import BaseTable from '../components/BaseTable.vue'
import BasePagination from '../components/BasePagination.vue'

const sidebarItems = [
  { label: '대시보드', href: '/seller/dashboard', active: false },
  { label: '상품 등록', href: '/seller/products', active: true },
  { label: '판매 내역', href: '/coming-soon/seller-orders', active: false },
  { label: '정산', href: '/seller/dashboard', active: false },
  { label: '설정', href: '/coming-soon/seller-settings', active: false },
]

const router = useRouter()
const productStatus = ref('')
const progressStatus = ref('')
const startDate = ref('')
const endDate = ref('')
const currentPage = ref(1)
const appliedProductStatus = ref('')
const filterMessage = ref('')

// 예시 데이터입니다. 실제 연동 시 API 응답으로 교체하세요.
const products = ref([
  { name: 'Jordan 1 Retro High OG', code: 'ES5088-010', variant: "Black/White", price: '329,000', status: '판매중', visible: true },
  { name: "New Balance 990v4 Made in USA", code: 'M990GL6', variant: '', price: '289,000', status: '판매중지', visible: false },
  { name: "Yeezy Boost 350 V2 Dryv", code: 'HQ4540', variant: '', price: '315,000', status: '판매중', visible: true },
])

const logs = [
  { title: '신규 주문 발생 - Jordan 1 Retro High', meta: '판매자 A · 2025-06-08 09:14', amount: '+329,000' },
  { title: '가격 인상 - New Balance 990v6', meta: '판매자 B · 2025-06-08 08:52', amount: '+289,000' },
  { title: '배송 시작 - Yeezy Boost 350 V2', meta: '', amount: '' },
]

const filteredProducts = computed(() => {
  if (!appliedProductStatus.value || appliedProductStatus.value === '전체') return products.value
  return products.value.filter((product) => product.status === appliedProductStatus.value)
})

function applyFilters() {
  appliedProductStatus.value = productStatus.value
  currentPage.value = 1
  filterMessage.value = `${filteredProducts.value.length}개의 목업 상품을 조회했습니다.`
}

function openFeature(feature, product) {
  router.push({
    name: 'coming-soon',
    params: { feature },
    query: product ? { name: product.name } : {},
  })
}
</script>

<template>
  <SidebarLayout :sidebar-items="sidebarItems">
    <div class="mb-4 flex items-center gap-2 text-sm text-text-sub">
      <span class="flex h-6 w-6 items-center justify-center rounded-full bg-accent" />
      브랜드 신청 / 관리자
    </div>

    <!-- Filters -->
    <div class="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-4">
      <BaseSelect
        v-model="productStatus"
        label="상품 상태"
        :options="['전체', '판매중', '판매중지']"
      />
      <BaseSelect
        v-model="progressStatus"
        label="진행 상태"
        :options="['전체', '진행중', '종료']"
      />
      <BaseDateRange
        v-model:start-date="startDate"
        v-model:end-date="endDate"
        label="기간 조회"
        class="sm:col-span-1"
      />
      <div class="flex items-end">
        <BaseButton
          block
          @click="applyFilters"
        >
          상품 조회 적용하기
        </BaseButton>
      </div>
    </div>
    <p
      v-if="filterMessage"
      class="mb-4 rounded-md bg-accent px-4 py-3 text-sm text-primary"
      role="status"
    >
      {{ filterMessage }}
    </p>

    <!-- Table -->
    <BaseTable :columns="['상품 정보', '판매 변형', '가격', '게시 상태', '노출 여부', '관리']">
      <tr
        v-for="p in filteredProducts"
        :key="p.code"
      >
        <td class="flex items-center gap-3 px-4 py-3">
          <div class="h-10 w-10 shrink-0 rounded-md bg-bg" />
          <div>
            <p class="text-sm font-semibold text-text-main">
              {{ p.name }}
            </p>
            <p class="text-xs text-text-sub">
              {{ p.code }}
            </p>
          </div>
        </td>
        <td class="px-4 py-3 text-sm text-text-sub">
          {{ p.variant || '-' }}
        </td>
        <td class="px-4 py-3 text-sm font-semibold text-text-main">
          ₩{{ p.price }}
        </td>
        <td class="px-4 py-3">
          <BaseBadge :variant="p.status === '판매중' ? 'primary' : 'gray'">
            {{ p.status }}
          </BaseBadge>
        </td>
        <td class="px-4 py-3">
          <BaseToggle v-model="p.visible" />
        </td>
        <td class="px-4 py-3 text-text-sub">
          <button
            type="button"
            class="mr-2"
            aria-label="이력 보기"
            @click="openFeature('product-history', p)"
          >
            ↺
          </button>
          <button
            type="button"
            aria-label="수정"
            @click="openFeature('product-edit', p)"
          >
            ✎
          </button>
        </td>
      </tr>
      <tr v-if="!filteredProducts.length">
        <td
          colspan="6"
          class="px-4 py-12 text-center text-sm text-text-sub"
        >
          조건에 맞는 목업 상품이 없습니다.
        </td>
      </tr>
    </BaseTable>

    <div class="mt-4">
      <BasePagination
        v-model:current-page="currentPage"
        :total-pages="7"
      />
    </div>

    <!-- Log + settlement -->
    <div class="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div class="rounded-lg border border-border bg-surface p-5 lg:col-span-2">
        <p class="mb-3 text-sm font-bold text-text-main">
          실시간 거래 로그
        </p>
        <ul class="space-y-3">
          <li
            v-for="log in logs"
            :key="log.title"
            class="flex items-center justify-between text-sm"
          >
            <div>
              <p class="font-medium text-text-main">
                {{ log.title }}
              </p>
              <p
                v-if="log.meta"
                class="text-xs text-text-sub"
              >
                {{ log.meta }}
              </p>
            </div>
            <span
              v-if="log.amount"
              class="text-xs font-semibold text-primary"
            >{{ log.amount }}</span>
          </li>
        </ul>
      </div>

      <div class="flex flex-col justify-between rounded-lg bg-primary-gradient p-5 text-white">
        <div>
          <p class="text-xs opacity-90">
            이번 달 정산 예정 금액
          </p>
          <p class="mt-1 text-2xl font-bold">
            ₩4,210,000
          </p>
        </div>
        <button
          type="button"
          class="mt-4 rounded-md bg-white/20 py-2 text-sm font-semibold hover:bg-white/30"
          @click="openFeature('settlement')"
        >
          정산 정보 확인
        </button>
      </div>
    </div>
  </SidebarLayout>
</template>
