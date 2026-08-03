<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import AuthShell from '../components/AuthShell.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import SocialProviderButton from '../components/SocialProviderButton.vue'
import { loginWithEmail } from '../api/auth'
import { setAuthSession } from '../auth/session'
import { startOAuthLogin } from '../auth/oauth'

const router = useRouter()
const route = useRoute()
const email = ref('')
const password = ref('')
const isLoading = ref(false)
const socialLoading = ref('')
const errorMessage = ref('')
const successMessage = ref(route.query.passwordChanged ? '비밀번호가 변경되었습니다. 다시 로그인해 주세요.' : '')

const LOGIN_FAILURE_MESSAGES = Object.freeze({
  AUTH001: '이메일 또는 비밀번호가 올바르지 않습니다.',
  AUTH009: '이메일 인증이 필요합니다. 가입할 때 받은 인증 메일을 확인해 주세요.',
  MEM004: '현재 이용할 수 없는 계정입니다. 고객센터에 문의해 주세요.',
  CMN007: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.',
})

function resolveLoginFailureMessage(error) {
  if (LOGIN_FAILURE_MESSAGES[error?.code]) {
    return LOGIN_FAILURE_MESSAGES[error.code]
  }
  if (!error?.status) {
    return '서버에 연결하지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.'
  }
  return error?.message || '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.'
}

function safeRedirectPath() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/'
}

async function submitEmailLogin() {
  if (isLoading.value) return
  errorMessage.value = ''
  const normalizedEmail = email.value.trim()
  if (!normalizedEmail || !password.value) {
    errorMessage.value = '이메일과 비밀번호를 모두 입력해 주세요.'
    return
  }
  isLoading.value = true
  try {
    const result = await loginWithEmail(normalizedEmail, password.value)
    setAuthSession(result)
    await router.push(safeRedirectPath())
  } catch (error) {
    errorMessage.value = resolveLoginFailureMessage(error)
  } finally {
    isLoading.value = false
  }
}

async function socialLogin(provider) {
  if (socialLoading.value) return
  socialLoading.value = provider
  errorMessage.value = ''
  try {
    await startOAuthLogin(provider)
  } catch (error) {
    errorMessage.value = error.message
    socialLoading.value = ''
  }
}

const providers = [
  { id: 'google', label: 'Google' },
  { id: 'naver', label: '네이버' },
  { id: 'kakao', label: '카카오' },
]
</script>

<template>
  <DefaultLayout>
    <AuthShell
      eyebrow="LOG IN"
      title="로그인"
      description="이메일 또는 자주 사용하는 소셜 계정으로 안전하게 시작하세요."
    >
      <BaseCard class="p-7 sm:p-8">
        <p
          v-if="successMessage"
          class="mb-5 rounded-md bg-green-50 px-4 py-3 text-sm text-green-700"
          role="status"
        >
          {{ successMessage }}
        </p>
        <form @submit.prevent="submitEmailLogin">
          <div class="space-y-5">
            <BaseInput
              v-model="email"
              label="이메일"
              type="email"
              placeholder="name@example.com"
              autocomplete="email"
              required
            />
            <BaseInput
              v-model="password"
              label="비밀번호"
              type="password"
              placeholder="비밀번호를 입력해 주세요"
              autocomplete="current-password"
              required
            />
          </div>
          <div class="mt-3 text-right">
            <RouterLink
              class="text-xs font-semibold text-primary transition-colors hover:text-primary-dark"
              to="/forgot-password"
            >
              비밀번호를 잊으셨나요?
            </RouterLink>
          </div>
          <BaseButton
            block
            class="mt-6"
            :disabled="isLoading"
            type="submit"
          >
            {{ isLoading ? '로그인 중...' : '로그인' }}
          </BaseButton>
        </form>

        <div
          v-if="errorMessage"
          class="mt-5 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700"
          role="alert"
          aria-live="assertive"
        >
          <p class="font-semibold">
            로그인할 수 없습니다.
          </p>
          <p class="mt-1">
            {{ errorMessage }}
          </p>
        </div>

        <p class="mt-5 text-center text-sm text-text-sub">
          아직 회원이 아닌가요?
          <RouterLink
            class="ml-1 font-semibold text-primary"
            to="/signup"
          >
            회원가입
          </RouterLink>
        </p>

        <div class="my-7 flex items-center gap-3 text-xs text-text-sub">
          <span class="h-px flex-1 bg-border" />
          <span>소셜 계정으로 계속하기</span>
          <span class="h-px flex-1 bg-border" />
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <SocialProviderButton
            v-for="provider in providers"
            :key="provider.id"
            :provider="provider.id"
            :label="provider.label"
            :loading="socialLoading === provider.id"
            @click="socialLogin(provider.id)"
          />
        </div>
      </BaseCard>
    </AuthShell>
  </DefaultLayout>
</template>
