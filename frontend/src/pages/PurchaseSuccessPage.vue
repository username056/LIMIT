<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { buildProductById } from '../mock/products'

// TODO(주문/결제 API 연동): 실제 주문 생성 응답(주문 번호, 결제 상태 등)이 준비되면
// 아래 mock 주문 정보 대신 API 응답을 사용하세요. 지금은 PurchasePage에서 만든 mock 주문 번호를
// 쿼리로 그대로 전달받아 보여줍니다.

const route = useRoute()
const product = computed(() => buildProductById(route.params.productId))
const orderNumber = computed(() => route.query.orderNumber || '-')
const receiverName = computed(() => route.query.receiverName || '-')
const address = computed(() => route.query.address || '-')
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto flex min-h-[70vh] max-w-2xl items-center px-6 py-16">
      <BaseCard class="w-full p-8 sm:p-10">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary-gradient">
          <svg
            class="h-8 w-8 text-white"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.5"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M5 13l4 4L19 7"
            />
          </svg>
        </div>

        <h1 class="mt-5 text-center text-2xl font-bold text-text-main">
          구매에 성공하셨습니다!
        </h1>
        <p class="mx-auto mt-3 max-w-md text-center text-sm leading-6 text-text-sub">
          결제가 완료되었습니다. 판매자가 상품을 발송하면 알림으로 알려드리며,
          수령을 확인할 때까지 결제 금액은 안전하게 보관됩니다.
        </p>

        <RouterLink
          :to="{ name: 'product-detail', params: { productId: route.params.productId } }"
          class="group mt-6 flex items-center gap-3 rounded-md bg-accent p-4 transition hover:brightness-95"
        >
          <div class="h-14 w-14 shrink-0 rounded-md bg-primary-gradient" />
          <div>
            <p class="text-sm font-bold text-text-main group-hover:text-primary group-hover:underline">
              {{ product.name }}
            </p>
            <p class="mt-1 text-xs text-text-sub">
              {{ product.brand }}
            </p>
          </div>
        </RouterLink>

        <div class="mt-6 space-y-2 border-t border-border pt-4 text-sm">
          <div class="flex items-center justify-between">
            <span class="text-text-sub">주문 번호</span>
            <span class="font-semibold text-text-main">{{ orderNumber }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-text-sub">최종 결제 금액</span>
            <span class="font-semibold text-text-main">₩{{ product.price.toLocaleString('ko-KR') }} (일시불)</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-text-sub">수령인</span>
            <span class="font-semibold text-text-main">{{ receiverName }}</span>
          </div>
          <div class="flex items-center justify-between gap-4">
            <span class="shrink-0 text-text-sub">배송 주소</span>
            <span class="text-right font-semibold text-text-main">{{ address }}</span>
          </div>
        </div>

        <div class="mt-6 flex flex-col gap-3">
          <BaseButton
            to="/mypage/orders"
            block
          >
            주문 내역 보기
          </BaseButton>
          <BaseButton
            to="/products"
            variant="outline"
            block
          >
            쇼핑 계속하기
          </BaseButton>
        </div>
      </BaseCard>
    </div>
  </DefaultLayout>
</template>
