<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { cancelPayment } from '../api/payment'

const route = useRoute()

// Toss 결제창 취소·이탈로 여기 도착하면 매물 예약을 즉시 풀어준다. 이 호출이 실패하거나
// 브라우저가 아예 닫혀 도달하지 못해도, 서버의 예약 만료 스케줄러가 최종 안전망으로 남는다 —
// 그래서 여기서는 실패해도 사용자에게 에러를 보여주지 않고 조용히 넘어간다.
onMounted(() => {
  const { paymentId } = route.query
  if (paymentId) {
    cancelPayment(paymentId).catch(() => {})
  }
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
            :to="`/purchase/${route.params.productId}`"
            block
          >
            다시 시도하기
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
