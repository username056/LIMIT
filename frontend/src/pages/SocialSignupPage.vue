<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import { completeSocialSignup } from '../api/auth'
import {
  clearPendingSocialSignup,
  setAuthSession,
  usePendingSocialSignup,
} from '../auth/session'

const router = useRouter()
const pending = usePendingSocialSignup()
const form = ref({
  nickname: pending.value?.suggestedNickname || '', phone: '',
  serviceTermsAccepted: false, privacyTermsAccepted: false,
  ageRequirementAccepted: false, marketingAccepted: false,
})
const isLoading = ref(false)
const errorMessage = ref('')
const allRequired = computed(() => form.value.serviceTermsAccepted
  && form.value.privacyTermsAccepted && form.value.ageRequirementAccepted)
const termsUrl = import.meta.env.VITE_TERMS_OF_SERVICE_URL || '/terms/service'
const privacyUrl = import.meta.env.VITE_PRIVACY_POLICY_URL || '/terms/privacy'

function toggleRequired(event) {
  const checked = event.target.checked
  form.value.serviceTermsAccepted = checked
  form.value.privacyTermsAccepted = checked
  form.value.ageRequirementAccepted = checked
}

async function submit() {
  if (isLoading.value || !pending.value) return
  if (!allRequired.value) {
    errorMessage.value = '필수 약관에 모두 동의해 주세요.'
    return
  }
  isLoading.value = true
  errorMessage.value = ''
  try {
    const result = await completeSocialSignup({ ...form.value, phone: form.value.phone || null })
    setAuthSession(result)
    clearPendingSocialSignup()
    await router.replace('/')
  } catch (error) {
    errorMessage.value = error.message || '소셜 회원가입을 완료하지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto max-w-xl px-6 py-12">
      <div class="mb-8 text-center">
        <p class="mb-2 text-sm font-semibold text-primary">
          거의 다 왔어요
        </p>
        <h1 class="text-2xl font-bold text-text-main">
          소셜 회원가입
        </h1>
        <p
          v-if="pending"
          class="mt-2 text-sm text-text-sub"
        >
          {{ pending.provider }}에서 확인한 {{ pending.email }} 계정입니다.
        </p>
      </div>

      <BaseCard v-if="pending">
        <form
          class="space-y-5"
          @submit.prevent="submit"
        >
          <BaseInput
            v-model="form.nickname"
            label="닉네임"
            placeholder="2~20자"
          />
          <BaseInput
            v-model="form.phone"
            label="휴대전화 번호 (선택)"
            type="tel"
            placeholder="01012345678"
          />
          <div class="rounded-lg border border-border bg-bg p-4 text-sm">
            <label class="flex items-center gap-3 font-semibold text-text-main">
              <input
                type="checkbox"
                :checked="allRequired"
                @change="toggleRequired"
              >필수 약관 모두 동의
            </label>
            <div class="my-3 h-px bg-border" />
            <label class="flex items-center justify-between py-1 text-text-sub">
              <span><input
                v-model="form.serviceTermsAccepted"
                class="mr-2"
                type="checkbox"
              >(필수) 서비스 이용약관</span>
              <a
                :href="termsUrl"
                target="_blank"
                rel="noopener"
                class="text-primary"
              >보기</a>
            </label>
            <label class="flex items-center justify-between py-1 text-text-sub">
              <span><input
                v-model="form.privacyTermsAccepted"
                class="mr-2"
                type="checkbox"
              >(필수) 개인정보 처리방침</span>
              <a
                :href="privacyUrl"
                target="_blank"
                rel="noopener"
                class="text-primary"
              >보기</a>
            </label>
            <label class="block py-1 text-text-sub"><input
              v-model="form.ageRequirementAccepted"
              class="mr-2"
              type="checkbox"
            >(필수) 만 14세 이상입니다</label>
            <label class="block py-1 text-text-sub"><input
              v-model="form.marketingAccepted"
              class="mr-2"
              type="checkbox"
            >(선택) 혜택 및 마케팅 정보 수신</label>
          </div>
          <p
            v-if="errorMessage"
            class="text-sm text-red-500"
            role="alert"
          >
            {{ errorMessage }}
          </p>
          <BaseButton
            block
            type="submit"
            :disabled="isLoading"
          >
            {{ isLoading ? '가입 처리 중...' : '동의하고 시작하기' }}
          </BaseButton>
        </form>
      </BaseCard>

      <BaseCard
        v-else
        class="text-center"
      >
        <p class="text-sm text-text-sub">
          가입 세션을 찾을 수 없습니다. 소셜 로그인을 다시 시작해 주세요.
        </p>
        <RouterLink
          class="mt-5 inline-block font-semibold text-primary"
          to="/login"
        >
          로그인 화면으로 이동
        </RouterLink>
      </BaseCard>
    </section>
  </DefaultLayout>
</template>
