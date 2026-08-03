<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMySellerProfile } from '../api/seller'
import { getMyProducts } from '../api/products'
import BaseBadge from '../components/BaseBadge.vue'
import StatCard from '../components/StatCard.vue'
import BarChart from '../components/charts/BarChart.vue'
import DonutChart from '../components/charts/DonutChart.vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import PageHeader from '../components/PageHeader.vue'

// 판매자 전용 통계 API가 아직 없어, 내 상품 목록에서 계산할 수 있는 지표만 보여줍니다.
// 조회수·매출액처럼 목록 응답에 없는 지표는 주문/집계 API가 준비된 뒤 추가해야 합니다.
const AGGREGATE_SIZE = 100

const profile = ref(null)
const products = ref([])
const totalCount = ref(0)
const isLoading = ref(true)
const isLoadingStats = ref(true)
const errorMessage = ref('')
const statsErrorMessage = ref('')

const sellerTypeLabel = computed(() => (
  profile.value?.sellerType === 'BUSINESS' ? '사업자 판매자' : '개인 판매자'
))
const createdAtLabel = computed(() => {
  if (!profile.value?.createdAt) return '-'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' })
    .format(new Date(profile.value.createdAt))
})

function countByStatus(status) {
  return products.value.filter((product) => product.status === status).length
}

const statusBreakdown = computed(() => [
  { label: '판매 중', count: countByStatus('ON_SALE') },
  { label: '임시 저장 중', count: countByStatus('DRAFT') },
  { label: '판매 완료', count: countByStatus('SOLD') },
  { label: '숨김', count: countByStatus('HIDDEN') },
])

// 필수 체크리스트 항목 전체 대비 완료 비율입니다.
const verificationRate = computed(() => {
  const required = products.value.reduce((sum, product) => sum + Number(product.requiredItemCount || 0), 0)
  if (!required) return 0
  const completed = products.value.reduce((sum, product) => sum + Number(product.completedItemCount || 0), 0)
  return Math.min(100, Math.round((completed / required) * 100))
})

const verifiedProductCount = computed(() => products.value.filter((product) => (
  Number(product.requiredItemCount || 0) > 0
  && Number(product.completedItemCount || 0) >= Number(product.requiredItemCount)
)).length)

const isPartialAggregate = computed(() => totalCount.value > products.value.length)

onMounted(async () => {
  try {
    profile.value = await getMySellerProfile()
  } catch (error) {
    errorMessage.value = error.message || '판매자 정보를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }

  try {
    const response = await getMyProducts({ page: 0, size: AGGREGATE_SIZE, sort: 'updatedAt,desc' })
    products.value = response?.data || []
    totalCount.value = response?.meta?.totalElements ?? products.value.length
  } catch (error) {
    statsErrorMessage.value = error.message || '상품 통계를 불러오지 못했습니다.'
  } finally {
    isLoadingStats.value = false
  }
})
</script>

<template>
  <MyPageLayout>
    <section>
      <PageHeader
        eyebrow="MY DASHBOARD"
        title="판매자 대시보드"
        description="내가 등록한 상품의 현황과 검증 진행 상태를 한눈에 확인하세요."
      />

      <p
        v-if="isLoadingStats"
        class="mb-8 card-soft rounded-lg bg-surface p-6 text-sm text-text-sub"
      >
        상품 통계를 불러오는 중입니다.
      </p>
      <p
        v-else-if="statsErrorMessage"
        role="alert"
        class="mb-8 rounded-lg bg-red-50 p-6 text-sm text-red-600"
      >
        {{ statsErrorMessage }}
      </p>
      <template v-else>
        <div class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="등록한 상품"
            :value="`${totalCount}개`"
          />
          <StatCard
            label="판매 중"
            :value="`${statusBreakdown[0].count}개`"
          />
          <StatCard
            label="임시 저장 중"
            :value="`${statusBreakdown[1].count}개`"
            :trend="statusBreakdown[1].count ? '등록을 마치면 판매가 시작됩니다.' : ''"
          />
          <StatCard
            label="판매 완료"
            :value="`${statusBreakdown[2].count}개`"
          />
        </div>

        <div class="mb-8 grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
          <div class="flex flex-col items-center justify-center gap-4 rounded-lg bg-primary-gradient p-6 shadow-card">
            <p class="text-sm font-semibold text-white">
              필수 검증 완료율
            </p>
            <DonutChart :percent="verificationRate" />
            <p class="text-center text-xs leading-5 text-white/80">
              검증을 모두 마친 상품 {{ verifiedProductCount }}개
            </p>
          </div>

          <div class="card-soft rounded-lg bg-surface p-6">
            <h2 class="mb-1 text-base font-bold text-text-main">
              상태별 상품 수
            </h2>
            <p class="mb-4 text-xs text-text-sub">
              최근 수정 순으로 최대 {{ AGGREGATE_SIZE }}개를 집계합니다.
            </p>
            <BarChart
              :labels="statusBreakdown.map((item) => item.label)"
              :values="statusBreakdown.map((item) => item.count)"
            />
          </div>
        </div>

        <p
          v-if="isPartialAggregate"
          class="mb-8 rounded-md bg-accent px-4 py-3 text-xs text-primary-dark"
        >
          상품이 {{ totalCount }}개라서 위 그래프는 최근 {{ products.length }}개만 집계한 값입니다.
        </p>
      </template>

      <h2 class="mb-3 text-lg font-bold text-text-main">
        판매자 등록 정보
      </h2>
      <p
        v-if="isLoading"
        class="card-soft rounded-lg bg-surface p-6 text-sm text-text-sub"
      >
        판매자 정보를 불러오는 중입니다.
      </p>
      <p
        v-else-if="errorMessage"
        role="alert"
        class="rounded-lg bg-red-50 p-6 text-sm text-red-600"
      >
        {{ errorMessage }}
      </p>

      <div
        v-else
        class="card-soft rounded-lg bg-surface p-6"
      >
        <div class="mb-6 flex items-center justify-between border-b border-border pb-4">
          <div>
            <p class="text-sm text-text-sub">
              판매자 상태
            </p>
            <p class="mt-1 font-bold text-text-main">
              {{ sellerTypeLabel }}
            </p>
          </div>
          <BaseBadge variant="primary">
            {{ profile.status === 'ACTIVE' ? '활성' : profile.status }}
          </BaseBadge>
        </div>

        <dl class="grid grid-cols-1 gap-x-8 gap-y-5 text-sm sm:grid-cols-2">
          <div>
            <dt class="text-text-sub">
              활동 국가
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ profile.countryCode }}
            </dd>
          </div>
          <div>
            <dt class="text-text-sub">
              등록일
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ createdAtLabel }}
            </dd>
          </div>
          <div v-if="profile.businessName">
            <dt class="text-text-sub">
              상호명
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ profile.businessName }}
            </dd>
          </div>
          <div>
            <dt class="text-text-sub">
              정산 계좌
            </dt>
            <dd class="mt-1 font-semibold text-text-main">
              {{ profile.settlementBankName }} · {{ profile.settlementAccountHolder }}
              (끝 {{ profile.settlementAccountLast4 }})
            </dd>
          </div>
        </dl>
      </div>
    </section>
  </MyPageLayout>
</template>
