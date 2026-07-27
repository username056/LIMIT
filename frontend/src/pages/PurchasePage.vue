<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import { buildProductById } from '../mock/products'

// TODO(주문/결제 API 연동): 주문 생성·결제 API가 준비되면 이 페이지의 mock 배송지·결제 흐름을
// 실제 요청으로 교체하세요. 지금은 결제 자체를 처리할 백엔드가 없어 결제 버튼은 coming-soon으로 연결됩니다.

const route = useRoute()
const router = useRouter()
const product = computed(() => buildProductById(route.params.productId))

const receiverName = ref('홍길동')
const receiverPhone = ref('010-1234-5678')
const addressLine1 = ref('서울시 강남구 테헤란로 123')
const addressLine2 = ref('마크타워 5층 501호')
const deliveryMemo = ref('문 앞에 놓아주세요.')

const PAYMENT_METHODS = [
  { value: 'card', label: '신용/체크카드' },
  { value: 'tosspay', label: '토스페이' },
  { value: 'transfer', label: '실시간 계좌이체' },
]
const selectedPaymentMethod = ref('card')
const selectedPaymentLabel = computed(
  () => PAYMENT_METHODS.find((method) => method.value === selectedPaymentMethod.value)?.label || '',
)

// mock 주문 생성: 실제 주문 API가 준비되면 이 함수 대신 생성된 주문 응답을 사용하세요.
function submitPayment() {
  const now = new Date()
  const dateCode = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  const orderNumber = `LMT-${dateCode}-${String(Math.floor(1000 + Math.random() * 9000))}`

  router.push({
    name: 'purchase-success',
    params: { productId: route.params.productId },
    query: {
      orderNumber,
      receiverName: receiverName.value,
      address: `${addressLine1.value} ${addressLine2.value}`,
    },
  })
}
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-[1200px] px-6 py-10 lg:px-10">
      <h1 class="mb-6 text-2xl font-bold text-text-main">
        결제하기
      </h1>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div class="space-y-6">
          <BaseCard>
            <h2 class="mb-4 text-base font-bold text-text-main">
              수령인 및 배송지 정보
            </h2>
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <BaseInput
                v-model="receiverName"
                label="수령인 이름"
              />
              <BaseInput
                v-model="receiverPhone"
                label="연락처"
              />
            </div>

            <div class="mt-4">
              <span class="mb-2 block text-sm font-medium text-text-main">배송 주소</span>
              <div class="flex gap-2">
                <input
                  v-model="addressLine1"
                  type="text"
                  class="w-full rounded-md border border-border bg-surface px-4 py-3 text-sm text-text-main outline-none focus:border-primary"
                >
                <BaseButton
                  variant="outline"
                  class="whitespace-nowrap"
                  :to="{ name: 'coming-soon', params: { feature: 'postal-code-search' } }"
                >
                  우편번호 찾기
                </BaseButton>
              </div>
              <input
                v-model="addressLine2"
                type="text"
                class="mt-2 w-full rounded-md border border-border bg-surface px-4 py-3 text-sm text-text-main outline-none focus:border-primary"
              >
            </div>

            <div class="mt-4">
              <BaseInput
                v-model="deliveryMemo"
                label="배송 메모"
              />
            </div>
          </BaseCard>

          <BaseCard>
            <h2 class="mb-4 text-base font-bold text-text-main">
              결제 수단 선택
            </h2>
            <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <button
                v-for="method in PAYMENT_METHODS"
                :key="method.value"
                type="button"
                class="rounded-md border px-4 py-3 text-left text-sm font-semibold transition-colors"
                :class="selectedPaymentMethod === method.value
                  ? 'border-primary bg-accent text-primary-dark'
                  : 'border-border text-text-main hover:border-primary'"
                @click="selectedPaymentMethod = method.value"
              >
                {{ method.label }}
              </button>
            </div>
          </BaseCard>
        </div>

        <div>
          <BaseCard class="sticky top-6">
            <h2 class="mb-4 text-base font-bold text-text-main">
              주문 요약
            </h2>
            <div class="flex items-center gap-3">
              <div class="h-16 w-16 shrink-0 rounded-md bg-primary-gradient" />
              <div>
                <p class="text-sm font-bold text-text-main">
                  {{ product.name }}
                </p>
                <p class="mt-1 text-xs text-text-sub">
                  {{ product.brand }}
                </p>
              </div>
            </div>

            <div class="mt-5 space-y-2 border-t border-border pt-4 text-sm">
              <div class="flex items-center justify-between text-text-sub">
                <span>상품 금액</span>
                <span>₩{{ product.price.toLocaleString('ko-KR') }}</span>
              </div>
              <div class="flex items-center justify-between text-base font-bold text-text-main">
                <span>최종 결제 금액</span>
                <span class="text-primary">₩{{ product.price.toLocaleString('ko-KR') }}</span>
              </div>
            </div>

            <BaseButton
              block
              class="mt-5"
              @click="submitPayment"
            >
              {{ selectedPaymentLabel }}으로 결제하기
            </BaseButton>

            <p class="mt-4 text-xs leading-5 text-text-sub">
              ⓘ 결제 완료 후 판매자가 발송하고 구매확정을 할 때까지 안전거래 보호장치(에스크로)가 철저히 적용됩니다.
            </p>
          </BaseCard>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
