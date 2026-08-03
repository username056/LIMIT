<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { confirmPayment } from '../api/payment'
import { getProduct } from '../api/products'

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
const product = ref(null)
const thumbnailUrl = computed(() => product.value?.thumbnailUrl || '')

const address = computed(() => route.query.address || '-')

/*
  상품 표시는 조회한 값을 먼저 쓰고, 없으면 주소에 실려 온 값으로 버팁니다.
  ---------------------------------------------------------------------------
  이름과 제조사는 successUrl 쿼리로 넘어오지만 모델명은 없습니다. 상세·목록과
  같은 "제조사 · 모델 / 판매글 이름" 순서로 보여 주려면 모델명이 필요해서
  상품을 한 번 조회합니다. 조회가 실패해도 쿼리 값으로 이름과 제조사는 남습니다.
*/
const productName = computed(() => product.value?.name || route.query.productName || '-')
const manufacturer = computed(
  () => product.value?.device?.manufacturer || route.query.manufacturer || '',
)
const deviceLine = computed(() => [manufacturer.value, product.value?.device?.model]
  .filter(Boolean)
  .join(' · '))
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

  /*
    사진과 모델명을 위해 상품을 한 번 조회합니다.
    -------------------------------------------------------------------------
    사진 URL까지 successUrl에 담으면 주소가 길어지고 CDN 주소가 그대로
    노출됩니다. 승인 뒤에 한 번 더 물어보는 편이 낫습니다.

    실패해도 쿼리로 받은 이름·제조사가 남고 사진 자리는 그라데이션 네모로
    돌아갈 뿐이라, 결제 결과와는 무관합니다.
  */
  try {
    product.value = await getProduct(route.params.productId)
  } catch {
    product.value = null
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
          <img
            v-if="thumbnailUrl"
            :src="thumbnailUrl"
            :alt="productName"
            class="h-14 w-14 shrink-0 rounded-md object-cover"
          >
          <div
            v-else
            class="h-14 w-14 shrink-0 rounded-md bg-primary-gradient"
          />
          <!-- 순서는 상품 상세·결제하기와 같습니다. 제조사·모델 위, 판매글 이름 아래. -->
          <div class="min-w-0">
            <p
              v-if="deviceLine"
              class="truncate text-xs font-semibold text-primary"
            >
              {{ deviceLine }}
            </p>
            <p class="mt-1 truncate text-sm font-bold text-text-main group-hover:text-primary group-hover:underline">
              {{ productName }}
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
