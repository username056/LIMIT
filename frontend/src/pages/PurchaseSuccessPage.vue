<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { confirmPayment } from '../api/payment'

const route = useRoute()
const payment = ref(null)
const isConfirming = ref(true)
const confirmError = ref('')

const address = computed(() => route.query.address || '-')
const productName = computed(() => route.query.productName || '-')
const manufacturer = computed(() => route.query.manufacturer || '')

onMounted(async () => {
  try {
    const { paymentId, paymentKey, orderId, amount } = route.query
    if (!paymentId || !paymentKey || !orderId || !amount) {
      confirmError.value = '결제 승인 정보가 올바르지 않습니다.'
      return
    }

    payment.value = await confirmPayment(paymentId, {
      paymentKey,
      orderId,
      amount: Number(amount),
    })
  } catch (error) {
    confirmError.value = error.message || '결제 승인에 실패했습니다.'
  } finally {
    isConfirming.value = false
  }
})
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto flex min-h-[70vh] max-w-2xl items-center px-6 py-16">
      <BaseCard
        v-if="isConfirming"
        class="w-full p-8 text-center sm:p-10"
      >
        <p class="text-sm text-text-sub">
          결제를 승인하는 중입니다...
        </p>
      </BaseCard>

      <BaseCard
        v-else-if="confirmError"
        class="w-full p-8 sm:p-10"
      >
        <h1 class="text-center text-xl font-bold text-text-main">
          결제 승인에 실패했습니다
        </h1>
        <p class="mx-auto mt-3 max-w-md text-center text-sm leading-6 text-red-600">
          {{ confirmError }}
        </p>
        <div class="mt-6 flex flex-col gap-3">
          <BaseButton
            :to="`/purchase/${route.params.productId}`"
            block
          >
            다시 시도하기
          </BaseButton>
        </div>
      </BaseCard>

      <BaseCard
        v-else
        class="w-full p-8 sm:p-10"
      >
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
              {{ productName }}
            </p>
            <p class="mt-1 text-xs text-text-sub">
              {{ manufacturer }}
            </p>
          </div>
        </RouterLink>

        <div
          v-if="payment"
          class="mt-6 space-y-2 border-t border-border pt-4 text-sm"
        >
          <div class="flex items-center justify-between">
            <span class="text-text-sub">결제 번호</span>
            <span class="font-semibold text-text-main">{{ payment.paymentId }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-text-sub">최종 결제 금액</span>
            <span class="font-semibold text-text-main">₩{{ Number(payment.approvedAmount).toLocaleString('ko-KR') }}</span>
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
