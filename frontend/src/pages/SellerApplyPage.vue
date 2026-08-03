<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { registerSeller } from '../api/seller'
import { legalVersion, sellerTermsSections } from '../legal/documents'
import { restoreAuthSession } from '../auth/session'
import BaseButton from '../components/BaseButton.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseSelect from '../components/BaseSelect.vue'
import MyPageLayout from '../layouts/MyPageLayout.vue'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const isSubmitting = ref(false)
const errorMessage = ref('')
const isTermsModalOpen = ref(false)
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

function agreeFromModal() {
  form.sellerTermsAccepted = true
  isTermsModalOpen.value = false
  errorMessage.value = ''
}

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
      <PageHeader
        eyebrow="SELLER REGISTER"
        title="판매자 등록"
        description="별도 심사 없이 등록이 완료되는 즉시 상품을 판매할 수 있습니다."
      />

      <form
        class="space-y-6 card-soft rounded-lg bg-surface p-6"
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

        <div class="rounded-md bg-bg p-4">
          <label class="flex items-start gap-3 text-sm text-text-main">
            <input
              v-model="form.sellerTermsAccepted"
              type="checkbox"
              class="mt-0.5 h-4 w-4 accent-primary"
            >
            <span>
              <span class="font-semibold">(필수)</span>
              판매 상품과 정산 정보에 대한 책임 및 판매자 이용 조건에 동의합니다.
            </span>
          </label>
          <div class="mt-2 pl-7">
            <button
              type="button"
              class="text-xs font-semibold text-primary underline"
              @click="isTermsModalOpen = true"
            >
              판매자 이용 조건 전문 보기
            </button>
          </div>
        </div>

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

    <div
      v-if="isTermsModalOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      role="dialog"
      aria-modal="true"
      aria-label="판매자 이용 조건"
      @click.self="isTermsModalOpen = false"
    >
      <div class="flex max-h-[80vh] w-full max-w-lg flex-col rounded-lg bg-surface shadow-elevated">
        <div class="flex items-start justify-between gap-4 border-b border-border px-6 py-4">
          <div>
            <h2 class="text-base font-bold text-text-main">
              판매자 이용 조건
            </h2>
            <p class="mt-1 text-xs text-text-sub">
              시행일 {{ legalVersion }}
            </p>
          </div>
          <button
            type="button"
            class="text-xl leading-none text-text-sub hover:text-text-main"
            aria-label="판매자 이용 조건 닫기"
            @click="isTermsModalOpen = false"
          >
            ×
          </button>
        </div>

        <div class="flex-1 space-y-5 overflow-y-auto px-6 py-5">
          <section
            v-for="section in sellerTermsSections"
            :key="section.id"
          >
            <h3 class="text-sm font-bold text-text-main">
              {{ section.title }}
            </h3>
            <ul class="mt-2 space-y-1.5">
              <li
                v-for="item in section.items"
                :key="item"
                class="text-xs leading-6 text-text-sub"
              >
                · {{ item }}
              </li>
            </ul>
          </section>
          <p class="text-xs text-text-sub">
            전체 이용약관은
            <RouterLink
              :to="{ name: 'terms-service' }"
              class="font-semibold text-primary underline"
            >
              이용약관 페이지
            </RouterLink>
            에서 확인할 수 있습니다.
          </p>
        </div>

        <div class="border-t border-border px-6 py-4">
          <BaseButton
            block
            type="button"
            @click="agreeFromModal"
          >
            동의하고 닫기
          </BaseButton>
        </div>
      </div>
    </div>
  </MyPageLayout>
</template>
