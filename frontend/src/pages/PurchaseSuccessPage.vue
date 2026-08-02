<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { confirmPayment } from '../api/payment'

// 서버가 일시적 오류로 보고 재시도를 허용하는 코드만 "다시 승인 시도"를 보여준다(같은 결제 재confirm).
const RETRYABLE_ERROR_CODES = new Set(['PAY011'])
// Toss가 승인을 명확히 거절한 코드다. 카드가 실제로 청구되지 않았으므로 새 결제를 시작해도
// 이중 청구 위험이 낮다 — "다른 결제수단으로 다시 결제"로 새 payment 생성을 허용한다. 그 외
// (위·변조 의심, PG 대사 필요 등 승인 여부가 불명확한 확정 실패)는 새 결제 경로로 보내지 않고
// 주문 내역·상품 상세로만 안내한다.
const TERMINAL_REJECTED_ERROR_CODES = new Set(['PAY012'])

const route = useRoute()
const payment = ref(null)
const isConfirming = ref(true)
const confirmError = ref('')
const confirmErrorCode = ref('')
const confirmParams = ref(null)

const address = computed(() => route.query.address || '-')
const productName = computed(() => route.query.productName || '-')
const manufacturer = computed(() => route.query.manufacturer || '')
const isRetryableError = computed(() => RETRYABLE_ERROR_CODES.has(confirmErrorCode.value))
const isTerminalRejectedError = computed(() => TERMINAL_REJECTED_ERROR_CODES.has(confirmErrorCode.value))

async function attemptConfirm() {
  if (!confirmParams.value) {
    return
  }
  isConfirming.value = true
  confirmError.value = ''
  confirmErrorCode.value = ''
  try {
    payment.value = await confirmPayment(confirmParams.value.paymentId, {
      paymentKey: confirmParams.value.paymentKey,
      orderId: confirmParams.value.orderId,
      amount: confirmParams.value.amount,
    })
  } catch (error) {
    confirmError.value = error.message || '결제 승인에 실패했습니다.'
    confirmErrorCode.value = error.code || ''
  } finally {
    isConfirming.value = false
  }
}

onMounted(async () => {
  const { paymentId, paymentKey, orderId, amount } = route.query
  if (!paymentId || !paymentKey || !orderId || !amount) {
    confirmError.value = '결제 승인 정보가 올바르지 않습니다.'
    isConfirming.value = false
    return
  }

  confirmParams.value = { paymentId, paymentKey, orderId, amount: Number(amount) }
  await attemptConfirm()
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
            v-if="isRetryableError"
            block
            @click="attemptConfirm"
          >
            다시 승인 시도
          </BaseButton>
          <BaseButton
            v-else-if="isTerminalRejectedError"
            :to="`/purchase/${route.params.productId}`"
            block
          >
            다른 결제수단으로 다시 결제
          </BaseButton>
          <template v-else>
            <BaseButton
              to="/mypage/orders"
              block
            >
              주문 내역에서 확인하기
            </BaseButton>
            <BaseButton
              :to="{ name: 'product-detail', params: { productId: route.params.productId } }"
              variant="outline"
              block
            >
              상품 상세로 돌아가기
            </BaseButton>
          </template>
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
