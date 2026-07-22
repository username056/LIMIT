<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import { loginWithEmail } from '../api/auth'
import { setAuthSession } from '../auth/session'
import { startOAuthLogin } from '../auth/oauth'

const router = useRouter()
const email = ref('')
const password = ref('')
const isLoading = ref(false)
const socialLoading = ref('')
const errorMessage = ref('')

async function submitEmailLogin() {
  if (isLoading.value) return
  isLoading.value = true
  errorMessage.value = ''
  try {
    const result = await loginWithEmail(email.value, password.value)
    setAuthSession(result)
    await router.push('/')
  } catch (error) {
    errorMessage.value = error.message || '로그인에 실패했습니다.'
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
  { id: 'google', label: 'Google로 계속하기', className: 'border-border bg-white' },
  { id: 'kakao', label: '카카오로 계속하기', className: 'border-[#FEE500] bg-[#FEE500]' },
  { id: 'naver', label: '네이버로 계속하기', className: 'border-[#03C75A] bg-[#03C75A] text-white' },
]
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto max-w-md px-6 py-16 text-center">
      <p class="mb-2 text-sm font-semibold text-primary">
        Limit
      </p>
      <h1 class="mb-2 text-2xl font-bold text-text-main">
        다시 만나서 반가워요
      </h1>
      <p class="mb-8 text-sm text-text-sub">
        이메일 또는 소셜 계정으로 로그인해 주세요.
      </p>

      <BaseCard class="text-left">
        <form @submit.prevent="submitEmailLogin">
          <div class="space-y-4">
            <BaseInput
              v-model="email"
              label="이메일 주소"
              type="email"
              placeholder="name@example.com"
            />
            <BaseInput
              v-model="password"
              label="비밀번호"
              type="password"
              placeholder="비밀번호를 입력해 주세요"
            />
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

        <p class="mt-4 text-center text-sm text-text-sub">
          아직 회원이 아닌가요?
          <RouterLink
            class="font-semibold text-primary"
            to="/signup"
          >
            회원가입
          </RouterLink>
        </p>

        <div class="my-6 flex items-center gap-3 text-xs text-text-sub">
          <span class="h-px flex-1 bg-border" />
          <span>또는</span>
          <span class="h-px flex-1 bg-border" />
        </div>

        <div class="grid gap-3">
          <button
            v-for="provider in providers"
            :key="provider.id"
            class="rounded-lg border px-4 py-3 text-center text-sm font-semibold text-text-main transition hover:brightness-95 disabled:opacity-60"
            :class="provider.className"
            type="button"
            :disabled="Boolean(socialLoading)"
            @click="socialLogin(provider.id)"
          >
            {{ socialLoading === provider.id ? '연결 중...' : provider.label }}
          </button>
        </div>

        <p
          v-if="errorMessage"
          class="mt-4 text-sm text-red-500"
          role="alert"
        >
          {{ errorMessage }}
        </p>
      </BaseCard>
    </section>
  </DefaultLayout>
</template>
