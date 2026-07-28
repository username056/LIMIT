<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { registerSeller } from '../api/seller'
import { restoreAuthSession } from '../auth/session'
import BaseButton from '../components/BaseButton.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseSelect from '../components/BaseSelect.vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'

const router = useRouter()
const isSubmitting = ref(false)
const errorMessage = ref('')
const form = reactive({
  sellerType: 'INDIVIDUAL',
  countryCode: 'KR',
  businessName: '',
  settlementBankName: '',
  settlementAccountHolder: '',
  settlementAccountLast4: '',
  sellerTermsAccepted: false,
})

const sellerTypeOptions = [
  { label: '개인 판매자', value: 'INDIVIDUAL' },
  { label: '사업자 판매자', value: 'BUSINESS' },
]
const countryOptions = [
  { label: '대한민국', value: 'KR' },
  { label: '미국', value: 'US' },
  { label: '일본', value: 'JP' },
]

async function submitRegistration() {
  errorMessage.value = ''

  if (form.sellerType === 'BUSINESS' && !form.businessName.trim()) {
    errorMessage.value = '사업자 판매자는 상호명을 입력해 주세요.'
    return
  }
  if (!form.settlementBankName.trim() || !form.settlementAccountHolder.trim()) {
    errorMessage.value = '정산 은행과 예금주를 입력해 주세요.'
    return
  }
  if (!/^\d{4}$/.test(form.settlementAccountLast4)) {
    errorMessage.value = '정산 계좌의 마지막 숫자 4자리를 입력해 주세요.'
    return
  }
  if (!form.sellerTermsAccepted) {
    errorMessage.value = '판매자 이용 조건에 동의해 주세요.'
    return
  }

  isSubmitting.value = true
  try {
    await registerSeller({
      sellerType: form.sellerType,
      countryCode: form.countryCode,
      businessName: form.businessName.trim() || null,
      settlementBankName: form.settlementBankName.trim(),
      settlementAccountHolder: form.settlementAccountHolder.trim(),
      settlementAccountLast4: form.settlementAccountLast4,
      sellerTermsAccepted: form.sellerTermsAccepted,
    })

    const restored = await restoreAuthSession()
    if (!restored) {
      errorMessage.value = '판매자 등록은 완료됐지만 세션 갱신에 실패했습니다. 다시 로그인해 주세요.'
      return
    }
    await router.replace({ name: 'seller-products' })
  } catch (error) {
    errorMessage.value = error.message || '판매자 등록을 완료하지 못했습니다.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <MyPageLayout>
    <section class="mx-auto max-w-2xl">
      <h1 class="mb-2 text-2xl font-bold text-text-main">
        판매자 등록
      </h1>
      <p class="mb-6 text-sm text-text-sub">
        별도 심사 없이 등록이 완료되는 즉시 상품을 판매할 수 있습니다.
      </p>

      <form
        class="space-y-6 rounded-lg border border-border bg-surface p-6 shadow-card"
        @submit.prevent="submitRegistration"
      >
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <BaseSelect
            v-model="form.sellerType"
            label="판매자 유형"
            :options="sellerTypeOptions"
          />
          <BaseSelect
            v-model="form.countryCode"
            label="활동 국가"
            :options="countryOptions"
          />
        </div>

        <BaseInput
          v-if="form.sellerType === 'BUSINESS'"
          v-model="form.businessName"
          label="상호명"
          placeholder="사업자 상호명을 입력하세요"
          required
        />

        <div>
          <h2 class="mb-3 text-sm font-bold text-text-main">
            정산 계좌 정보
          </h2>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <BaseInput
              v-model="form.settlementBankName"
              label="은행"
              placeholder="예: 국민은행"
              required
            />
            <BaseInput
              v-model="form.settlementAccountHolder"
              label="예금주"
              placeholder="예금주 이름"
              required
            />
          </div>
          <BaseInput
            v-model="form.settlementAccountLast4"
            class="mt-4"
            label="계좌번호 마지막 4자리"
            placeholder="1234"
            autocomplete="off"
            required
          />
          <p class="mt-2 text-xs text-text-sub">
            계좌번호 전체를 저장하지 않고 확인에 필요한 마지막 4자리만 저장합니다.
          </p>
        </div>

        <label class="flex items-start gap-3 rounded-md bg-bg p-4 text-sm text-text-main">
          <input
            v-model="form.sellerTermsAccepted"
            type="checkbox"
            class="mt-0.5 h-4 w-4 accent-primary"
          >
          <span>판매 상품과 정산 정보에 대한 책임 및 판매자 이용 조건에 동의합니다.</span>
        </label>

        <p
          v-if="errorMessage"
          role="alert"
          class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
        >
          {{ errorMessage }}
        </p>

        <BaseButton
          block
          type="submit"
          :disabled="isSubmitting"
        >
          {{ isSubmitting ? '등록 중...' : '판매자로 바로 등록하기' }}
        </BaseButton>
      </form>
    </section>
  </MyPageLayout>
</template>
