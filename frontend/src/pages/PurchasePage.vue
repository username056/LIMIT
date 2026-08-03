<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import PageHeader from '../components/PageHeader.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseAddressInput from '../components/BaseAddressInput.vue'
import { formatAddress } from '../utils/daumPostcode'
import { getAccessToken } from '../auth/session'
import { getMyProfile } from '../api/member'
import { getProduct } from '../api/products'
import { createPayment, getPayment, retryPayment } from '../api/payment'
import { getDefaultAddress } from '../stores/addressBook'

const route = useRoute()
const router = useRouter()
const product = ref(null)
const isLoadingProduct = ref(true)
// URL을 직접 바꿔 /purchase/다른상품?retryPaymentId=내결제ID로 들어오면, 실제 결제는
// retryPaymentId가 가리키는 매물로 진행되는데 화면에는 route의 productId 상품이 보인다.
// 백엔드가 소유자 확인은 해도 "화면 상품"과 "실제 결제 대상"이 어긋나는 것 자체는 막지 못하므로,
// 재시도 대상 결제의 listingId를 조회해 route productId와 다르면 결제를 아예 진행하지 못하게 막는다.
const retryListingMismatch = ref(false)

onMounted(async () => {
  try {
    product.value = await getProduct(route.params.productId)
    const retryPaymentId = route.query.retryPaymentId
    if (retryPaymentId) {
      const payment = await getPayment(retryPaymentId)
      if (Number(payment.listingId) !== Number(route.params.productId)) {
        retryListingMismatch.value = true
      }
    }
  } catch (error) {
    checkoutError.value = error.message || '상품 정보를 불러오지 못했습니다.'
  } finally {
    isLoadingProduct.value = false
  }
})

const isLoggedIn = Boolean(getAccessToken())
const showLoginRequiredModal = ref(!isLoggedIn)
const useDefaultAddress = ref(false)
const isLoadingDefaultAddress = ref(false)
const defaultAddressError = ref('')
const checkoutError = ref('')
const isSubmitting = ref(false)

const receiverName = ref('김싸피')
const receiverPhone = ref('010-1234-5678')
const address = ref({ zonecode: '', address: '광주광역시 광산구 하남산단 6번로 107', addressDetail: '광주 2반' })
const deliveryMemo = ref('문 앞에 놓아주세요.')

function goToLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

async function toggleUseDefaultAddress() {
  useDefaultAddress.value = !useDefaultAddress.value
  if (!useDefaultAddress.value) return

  defaultAddressError.value = ''
  isLoadingDefaultAddress.value = true
  try {
    const profile = await getMyProfile()
    receiverName.value = profile.nickname || receiverName.value
    receiverPhone.value = profile.phone || receiverPhone.value

    const defaultAddress = getDefaultAddress()
    if (defaultAddress) {
      address.value = {
        zonecode: defaultAddress.zonecode || '',
        address: defaultAddress.addressLine || defaultAddress.address,
        addressDetail: defaultAddress.addressDetail || '',
      }
    }
  } catch (error) {
    defaultAddressError.value = error.message || '회원 정보를 불러오지 못했습니다.'
    useDefaultAddress.value = false
  } finally {
    isLoadingDefaultAddress.value = false
  }
}

const PAYMENT_METHODS = [
  { value: 'card', label: '신용/체크카드', apiMethod: 'CARD', tossMethod: '카드' },
  { value: 'tosspay', label: '토스페이', apiMethod: 'TOSSPAY', tossMethod: '토스페이' },
  { value: 'transfer', label: '실시간 계좌이체', apiMethod: 'ACCOUNT_TRANSFER', tossMethod: '계좌이체' },
]
const selectedPaymentMethod = ref('card')
const selectedPayment = computed(
  () => PAYMENT_METHODS.find((method) => method.value === selectedPaymentMethod.value),
)
const selectedPaymentLabel = computed(() => selectedPayment.value?.label || '')

function validateCheckout() {
  if (!receiverName.value.trim() || !receiverPhone.value.trim()) {
    checkoutError.value = '수령인 이름과 연락처를 입력해 주세요.'
    return false
  }
  if (!address.value.address?.trim()) {
    checkoutError.value = '배송 주소를 입력해 주세요.'
    return false
  }
  return true
}

async function submitPayment() {
  checkoutError.value = ''
  if (retryListingMismatch.value) return
  if (!validateCheckout()) return
  if (!product.value) return

  const clientKey = import.meta.env.VITE_TOSS_PAYMENTS_CLIENT_KEY
  if (!clientKey) {
    checkoutError.value = '결제 설정이 완료되지 않았습니다. 잠시 후 다시 시도해 주세요.'
    return
  }
  if (!window.TossPayments) {
    checkoutError.value = '결제 모듈을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.'
    return
  }

  isSubmitting.value = true
  try {
    // 결제창 이탈 후 예약이 살아있는 상태에서 다시 왔다면(PurchaseFailPage에서 취소 실패로
    // retryPaymentId를 넘겨준 경우), 새 결제를 만들지 않고 같은 예약을 재사용하는 retry를 쓴다 —
    // 그래야 이미 RESERVED인 매물에 다시 예약을 걸다 LISTING_NOT_ON_SALE로 거부되지 않는다.
    const retryPaymentId = route.query.retryPaymentId
    const payment = retryPaymentId
      ? await retryPayment(retryPaymentId, selectedPayment.value.apiMethod)
      : await createPayment({
          listingId: Number(route.params.productId),
          method: selectedPayment.value.apiMethod,
          idempotencyKey: crypto.randomUUID(),
        })

    const origin = window.location.origin
    const productId = route.params.productId
    const tossPayments = window.TossPayments(clientKey)
    await tossPayments.requestPayment(selectedPayment.value.tossMethod, {
      amount: Number(payment.requestedAmount),
      orderId: payment.providerOrderId,
      orderName: product.value.name,
      customerName: receiverName.value,
      successUrl: `${origin}/purchase/${productId}/success?paymentId=${payment.paymentId}&address=${encodeURIComponent(formatAddress(address.value))}&productName=${encodeURIComponent(product.value.name)}&manufacturer=${encodeURIComponent(product.value.device?.manufacturer || '')}`,
      failUrl: `${origin}/purchase/${productId}/fail?paymentId=${payment.paymentId}`,
    })
  } catch (error) {
    checkoutError.value = error.message || '결제 요청을 처리하지 못했습니다.'
    isSubmitting.value = false
  }
}
</script>

<template>
  <DefaultLayout>
    <div class="page-shell">
      <PageHeader
        eyebrow="PURCHASE"
        title="결제하기"
        description="결제 수단을 고르고 주문 내용을 확인한 뒤 진행해 주세요."
      />

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div class="space-y-6">
          <p
            v-if="checkoutError"
            role="alert"
            class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
          >
            {{ checkoutError }}
          </p>
          <BaseCard>
            <div class="flex flex-wrap items-center justify-between gap-3">
              <h2 class="text-base font-bold text-text-main">
                수령인 및 배송지 정보
              </h2>
              <label
                v-if="isLoggedIn"
                class="flex items-center gap-2 text-xs font-semibold text-text-sub"
              >
                <input
                  type="checkbox"
                  :checked="useDefaultAddress"
                  class="h-4 w-4 rounded border-border"
                  @change="toggleUseDefaultAddress"
                >
                마이페이지 기본 배송지와 동일
              </label>
            </div>
            <p
              v-if="isLoadingDefaultAddress"
              class="mt-3 text-xs text-text-sub"
            >
              마이페이지 정보를 불러오는 중...
            </p>
            <p
              v-if="defaultAddressError"
              role="alert"
              class="mt-3 text-xs text-red-600"
            >
              {{ defaultAddressError }}
            </p>
            <div class="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
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
              <BaseAddressInput
                v-model="address"
                label="배송 주소"
                required
              />
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
                class="rounded-md border px-4 py-3 text-center text-sm font-semibold transition-colors"
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
          <BaseCard
            v-if="isLoadingProduct"
            class="sticky top-6"
          >
            <p class="text-sm text-text-sub">
              상품 정보를 불러오는 중...
            </p>
          </BaseCard>
          <BaseCard
            v-else-if="retryListingMismatch"
            class="sticky top-6"
          >
            <p
              role="alert"
              class="text-sm leading-6 text-red-700"
            >
              재시도할 결제와 현재 상품 정보가 일치하지 않습니다. 주문 내역에서 다시 시도해 주세요.
            </p>
            <BaseButton
              to="/mypage/orders"
              block
              class="mt-5"
            >
              주문 내역으로 이동
            </BaseButton>
          </BaseCard>
          <BaseCard
            v-else-if="product"
            class="sticky top-6"
          >
            <h2 class="mb-4 text-base font-bold text-text-main">
              주문 요약
            </h2>
            <RouterLink
              :to="{ name: 'product-detail', params: { productId: route.params.productId } }"
              class="group flex items-center gap-3 rounded-md transition hover:bg-bg"
            >
              <div class="h-16 w-16 shrink-0 rounded-md bg-primary-gradient" />
              <div>
                <p class="text-sm font-bold text-text-main group-hover:text-primary group-hover:underline">
                  {{ product.name }}
                </p>
                <p class="mt-1 text-xs text-text-sub">
                  {{ product.device?.manufacturer }}
                </p>
              </div>
            </RouterLink>

            <div class="mt-5 space-y-2 border-t border-border pt-4 text-sm">
              <div class="flex items-center justify-between text-text-sub">
                <span>상품 금액</span>
                <span>₩{{ Number(product.price).toLocaleString('ko-KR') }}</span>
              </div>
              <div class="flex items-center justify-between text-base font-bold text-text-main">
                <span>최종 결제 금액</span>
                <span class="text-primary">₩{{ Number(product.price).toLocaleString('ko-KR') }}</span>
              </div>
            </div>

            <BaseButton
              block
              class="mt-5"
              :disabled="isSubmitting"
              :aria-busy="isSubmitting || undefined"
              @click="submitPayment"
            >
              {{ selectedPaymentLabel }}로 결제하기
            </BaseButton>

            <p class="mt-4 text-xs leading-5 text-text-sub">
              ⓘ 결제 완료 후 판매자가 발송하고 구매확정을 할 때까지 안전거래 보호장치(에스크로)가 철저히 적용됩니다.
            </p>
          </BaseCard>
        </div>
      </div>
    </div>

    <div
      v-if="showLoginRequiredModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
    >
      <BaseCard class="w-full max-w-sm text-center">
        <h2 class="text-lg font-bold text-text-main">
          로그인 후에 이용 가능합니다
        </h2>
        <p class="mt-2 text-sm text-text-sub">
          구매를 진행하려면 먼저 로그인해 주세요.
        </p>
        <BaseButton
          block
          class="mt-5"
          @click="goToLogin"
        >
          로그인하러 가기
        </BaseButton>
      </BaseCard>
    </div>
  </DefaultLayout>
</template>
