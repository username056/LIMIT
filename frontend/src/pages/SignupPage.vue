<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import {
  checkEmailAvailability,
  checkNicknameAvailability,
  requestEmailVerification,
  signupWithEmail,
} from '../api/auth'
import { setPendingVerificationEmail } from '../auth/session'

const router = useRouter()
const form = ref({
  email: '', password: '', passwordConfirm: '', nickname: '', phone: '',
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
  if (isLoading.value) return
  errorMessage.value = ''
  if (form.value.password !== form.value.passwordConfirm) {
    errorMessage.value = '비밀번호 확인이 일치하지 않습니다.'
    return
  }
  if (!allRequired.value) {
    errorMessage.value = '필수 약관에 모두 동의해 주세요.'
    return
  }
  isLoading.value = true
  try {
    const [emailCheck, nicknameCheck] = await Promise.all([
      checkEmailAvailability(form.value.email),
      checkNicknameAvailability(form.value.nickname),
    ])
    if (!emailCheck.available) throw new Error('이미 사용 중인 이메일입니다.')
    if (!nicknameCheck.available) throw new Error('이미 사용 중인 닉네임입니다.')
    await signupWithEmail({
      email: form.value.email,
      password: form.value.password,
      nickname: form.value.nickname,
      phone: form.value.phone || null,
      serviceTermsAccepted: form.value.serviceTermsAccepted,
      privacyTermsAccepted: form.value.privacyTermsAccepted,
      ageRequirementAccepted: form.value.ageRequirementAccepted,
      marketingAccepted: form.value.marketingAccepted,
    })
    setPendingVerificationEmail(form.value.email)
    try {
      await requestEmailVerification(form.value.email)
    } finally {
      await router.push('/verify-email/requested')
    }
  } catch (error) {
    errorMessage.value = error.message || '회원가입을 완료하지 못했습니다.'
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
          Limit
        </p>
        <h1 class="text-2xl font-bold text-text-main">
          회원가입
        </h1>
        <p class="mt-2 text-sm text-text-sub">
          필요한 정보만 입력하고 안전하게 시작하세요.
        </p>
      </div>
      <BaseCard>
        <form
          class="space-y-5"
          @submit.prevent="submit"
        >
          <BaseInput
            v-model="form.email"
            label="이메일"
            type="email"
            placeholder="name@example.com"
          />
          <div class="grid gap-4 sm:grid-cols-2">
            <BaseInput
              v-model="form.password"
              label="비밀번호"
              type="password"
              placeholder="영문·숫자 포함 8자 이상"
            />
            <BaseInput
              v-model="form.passwordConfirm"
              label="비밀번호 확인"
              type="password"
              placeholder="한 번 더 입력해 주세요"
            />
          </div>
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
              >
              필수 약관 모두 동의
            </label>
            <div class="my-3 h-px bg-border" />
            <label class="flex items-center justify-between gap-3 py-1 text-text-sub">
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
            <label class="flex items-center justify-between gap-3 py-1 text-text-sub">
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
            <label class="block py-1 text-text-sub">
              <input
                v-model="form.ageRequirementAccepted"
                class="mr-2"
                type="checkbox"
              >(필수) 만 14세 이상입니다
            </label>
            <label class="block py-1 text-text-sub">
              <input
                v-model="form.marketingAccepted"
                class="mr-2"
                type="checkbox"
              >(선택) 혜택 및 마케팅 정보 수신
            </label>
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
            {{ isLoading ? '가입 처리 중...' : '회원가입' }}
          </BaseButton>
        </form>
        <p class="mt-5 text-center text-sm text-text-sub">
          이미 계정이 있나요? <RouterLink
            class="font-semibold text-primary"
            to="/login"
          >
            로그인
          </RouterLink>
        </p>
      </BaseCard>
    </section>
  </DefaultLayout>
</template>
