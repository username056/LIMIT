<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import AuthShell from '../components/AuthShell.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import { completeSocialSignup } from '../api/auth'
import { clearPendingSocialSignup, setAuthSession, usePendingSocialSignup } from '../auth/session'

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
    <AuthShell
      width="lg"
      eyebrow="마지막 단계"
      title="소셜 회원가입"
      :description="pending ? `${pending.provider}에서 확인한 ${pending.email} 계정입니다.` : '로그인 세션을 다시 확인해 주세요.'"
    >
      <BaseCard
        v-if="pending"
        class="p-7 sm:p-8"
      >
        <div class="mb-6 rounded-md border border-border bg-accent px-4 py-3 text-sm text-text-sub">
          소셜 제공자에서 받은 이메일은 계정 식별과 로그인에만 사용합니다.
        </div>
        <form
          class="space-y-5"
          @submit.prevent="submit"
        >
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
            <div class="space-y-3 text-text-sub">
              <label class="flex items-center justify-between gap-3">
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
              <label class="flex items-center justify-between gap-3">
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
              <label class="block"><input
                v-model="form.ageRequirementAccepted"
                class="mr-2 accent-primary"
                type="checkbox"
              >(필수) 만 14세 이상입니다.</label>
              <label class="block"><input
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
            {{ isLoading ? '가입 처리 중...' : '동의하고 시작하기' }}
          </BaseButton>
        </form>
      </BaseCard>

      <BaseCard
        v-else
        class="p-8 text-center"
      >
        <p class="text-sm leading-6 text-text-sub">
          가입 세션이 만료되었거나 없습니다. 소셜 로그인을 다시 시작해 주세요.
        </p>
        <RouterLink
          class="mt-6 inline-block font-semibold text-primary"
          to="/login"
        >
          로그인 화면으로 이동
        </RouterLink>
      </BaseCard>
    </AuthShell>
  </DefaultLayout>
</template>
