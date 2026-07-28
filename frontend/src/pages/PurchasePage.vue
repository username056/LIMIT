<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseAddressInput from '../components/BaseAddressInput.vue'
import { buildProductById } from '../mock/products'
import { formatAddress } from '../utils/daumPostcode'
import { getAccessToken } from '../auth/session'
import { getMyProfile } from '../api/member'
import { getDefaultAddress } from '../stores/addressBook'

// TODO(주문/결제 API 연동): 주문 생성·결제 API가 준비되면 이 페이지의 mock 배송지·결제 흐름을
// 실제 요청으로 교체하세요. 지금은 결제 자체를 처리할 백엔드가 없어 결제 버튼은 coming-soon으로 연결됩니다.

const route = useRoute()
const router = useRouter()
const product = computed(() => buildProductById(route.params.productId))

const isLoggedIn = Boolean(getAccessToken())
const showLoginRequiredModal = ref(!isLoggedIn)
const useDefaultAddress = ref(false)
const isLoadingDefaultAddress = ref(false)
const defaultAddressError = ref('')
const checkoutError = ref('')
const isSubmitting = ref(false)

const receiverName = ref('홍길동')
const receiverPhone = ref('010-1234-5678')
const address = ref({ zonecode: '', address: '서울시 강남구 테헤란로 123', addressDetail: '마크타워 5층 501호' })
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
  { value: 'card', label: '신용/체크카드' },
  { value: 'tosspay', label: '토스페이' },
  { value: 'transfer', label: '실시간 계좌이체' },
]
const selectedPaymentMethod = ref('card')
const selectedPaymentLabel = computed(
  () => PAYMENT_METHODS.find((method) => method.value === selectedPaymentMethod.value)?.label || '',
)

// mock 주문 생성: 실제 주문 API가 준비되면 이 함수 대신 생성된 주문 응답을 사용하세요.
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
  if (!validateCheckout()) return
  isSubmitting.value = true
  const now = new Date()
  const dateCode = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  const orderNumber = `LMT-${dateCode}-${String(Math.floor(1000 + Math.random() * 9000))}`

  await router.push({
    name: 'purchase-success',
    params: { productId: route.params.productId },
    query: {
      orderNumber,
      receiverName: receiverName.value,
      address: formatAddress(address.value),
    },
  })
  isSubmitting.value = false
}
</script>

<template>
  <DefaultLayout>
    <div class="mx-auto max-w-[1200px] px-6 py-10 lg:px-10">
      <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">
        PURCHASE
      </p>
      <h1 class="mb-6 mt-2 text-2xl font-bold text-text-main">
        결제하기
      </h1>

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
