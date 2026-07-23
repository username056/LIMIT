<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import AuthShell from '../components/AuthShell.vue'
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
    <AuthShell
      width="lg"
      title="회원가입"
      description="필요한 정보만 입력하고 L1MIT의 안전한 중고거래를 시작하세요."
    >
      <BaseCard class="p-7 sm:p-8">
        <form
          class="space-y-5"
          @submit.prevent="submit"
        >
          <BaseInput
            v-model="form.email"
            label="이메일"
            type="email"
            autocomplete="email"
            required
            placeholder="name@example.com"
          />
          <div class="grid gap-4 sm:grid-cols-2">
            <BaseInput
              v-model="form.password"
              label="비밀번호"
              type="password"
              autocomplete="new-password"
              required
              placeholder="영문·숫자 포함 8자 이상"
            />
            <BaseInput
              v-model="form.passwordConfirm"
              label="비밀번호 확인"
              type="password"
              autocomplete="new-password"
              required
              placeholder="한 번 더 입력해 주세요"
            />
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <BaseInput
              v-model="form.nickname"
              label="닉네임"
              required
              placeholder="2~20자"
            />
            <BaseInput
              v-model="form.phone"
              label="휴대전화 번호 (선택)"
              type="tel"
              autocomplete="tel"
              placeholder="01012345678"
            />
          </div>

          <div class="rounded-lg border border-border bg-bg p-5 text-sm">
            <label class="flex cursor-pointer items-center gap-3 font-semibold text-text-main">
              <input
                type="checkbox"
                :checked="allRequired"
                class="h-4 w-4 accent-primary"
                @change="toggleRequired"
              >
              필수 약관 모두 동의
            </label>
            <div class="my-4 h-px bg-border" />
            <div class="space-y-3">
              <label class="flex items-center justify-between gap-3 text-text-sub">
                <span><input
                  v-model="form.serviceTermsAccepted"
                  class="mr-2 accent-primary"
                  type="checkbox"
                >(필수) 서비스 이용약관</span>
                <RouterLink
                  to="/terms/service"
                  target="_blank"
                  class="font-semibold text-primary"
                >보기</RouterLink>
              </label>
              <label class="flex items-center justify-between gap-3 text-text-sub">
                <span><input
                  v-model="form.privacyTermsAccepted"
                  class="mr-2 accent-primary"
                  type="checkbox"
                >(필수) 개인정보 처리방침</span>
                <RouterLink
                  to="/terms/privacy"
                  target="_blank"
                  class="font-semibold text-primary"
                >보기</RouterLink>
              </label>
              <label class="block text-text-sub"><input
                v-model="form.ageRequirementAccepted"
                class="mr-2 accent-primary"
                type="checkbox"
              >(필수) 만 14세 이상입니다.</label>
              <label class="block text-text-sub"><input
                v-model="form.marketingAccepted"
                class="mr-2 accent-primary"
                type="checkbox"
              >(선택) 혜택 및 마케팅 정보 수신</label>
            </div>
          </div>

          <p
            v-if="errorMessage"
            class="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
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
        <p class="mt-6 text-center text-sm text-text-sub">
          이미 계정이 있나요?
          <RouterLink
            class="ml-1 font-semibold text-primary"
            to="/login"
          >
            로그인
          </RouterLink>
        </p>
      </BaseCard>
    </AuthShell>
  </DefaultLayout>
</template>
