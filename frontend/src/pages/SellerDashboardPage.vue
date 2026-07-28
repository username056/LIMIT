<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMySellerProfile } from '../api/seller'
import BaseBadge from '../components/BaseBadge.vue'
import BaseButton from '../components/BaseButton.vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'

const profile = ref(null)
const isLoading = ref(true)
const errorMessage = ref('')

const sellerTypeLabel = computed(() => (
  profile.value?.sellerType === 'BUSINESS' ? '사업자 판매자' : '개인 판매자'
))
const createdAtLabel = computed(() => {
  if (!profile.value?.createdAt) return '-'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' })
    .format(new Date(profile.value.createdAt))
})

onMounted(async () => {
  try {
    profile.value = await getMySellerProfile()
  } catch (error) {
    errorMessage.value = error.message || '판매자 정보를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <MyPageLayout>
    <section>
      <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 class="text-2xl font-bold text-text-main">
            판매자 대시보드
          </h1>
          <p class="mt-1 text-sm text-text-sub">
            판매자 등록 정보와 상품 관리 메뉴를 확인할 수 있습니다.
          </p>
        </div>
        <BaseButton to="/seller/products">
          상품 관리
        </BaseButton>
      </div>

      <p
        v-if="isLoading"
        class="rounded-lg border border-border bg-surface p-6 text-sm text-text-sub"
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
        class="rounded-lg border border-border bg-surface p-6 shadow-card"
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
