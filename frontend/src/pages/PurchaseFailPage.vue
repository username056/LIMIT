<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { cancelPayment } from '../api/payment'

const route = useRoute()
const cancelFailed = ref(false)
// cancel 요청이 끝나기 전까지는 성공/실패를 모른다. 이 사이에 "다시 시도하기"를 누르면
// 기본값(새 결제 경로)으로 나가버려, cancel이 실제로는 실패해 예약이 아직 RESERVED인 경우
// LISTING_NOT_ON_SALE로 막힐 수 있다 — 그래서 응답을 받을 때까지 버튼을 막아둔다.
const isCancelling = ref(false)

// Toss 결제창 취소·이탈로 여기 도착하면 매물 예약을 즉시 풀어준다. 이 호출이 실패하거나
// 브라우저가 아예 닫혀 도달하지 못해도, 서버의 예약 만료 스케줄러가 최종 안전망으로 남는다.
//
// 취소 자체가 실패하면(네트워크 오류 등) 예약은 여전히 이 구매자 앞으로 살아있을 가능성이 높다.
// 이 상태에서 "다시 시도하기"가 새 결제를 만들면 매물이 이미 RESERVED라 LISTING_NOT_ON_SALE로
// 거부된다 — 그래서 이때는 새 결제 대신 같은 예약을 재사용하는 재시도 경로로 보낸다.
onMounted(async () => {
  const { paymentId } = route.query
  if (!paymentId) {
    return
  }
  isCancelling.value = true
  try {
    await cancelPayment(paymentId)
  } catch {
    cancelFailed.value = true
  } finally {
    isCancelling.value = false
  }
})

const retryTo = computed(() => {
  const productId = route.params.productId
  if (cancelFailed.value && route.query.paymentId) {
    return { path: `/purchase/${productId}`, query: { retryPaymentId: route.query.paymentId } }
  }
  return `/purchase/${productId}`
})
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto flex min-h-[70vh] max-w-2xl items-center px-6 py-16">
      <BaseCard class="w-full p-8 sm:p-10">
        <h1 class="text-center text-xl font-bold text-text-main">
          결제가 완료되지 않았습니다
        </h1>
        <p class="mx-auto mt-3 max-w-md text-center text-sm leading-6 text-text-sub">
          {{ route.query.message || '결제가 취소되었거나 처리 중 오류가 발생했습니다.' }}
        </p>

        <div class="mt-6 flex flex-col gap-3">
          <BaseButton
            :to="retryTo"
            :disabled="isCancelling"
            block
          >
            {{ isCancelling ? '예약 정리 중…' : '다시 시도하기' }}
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
