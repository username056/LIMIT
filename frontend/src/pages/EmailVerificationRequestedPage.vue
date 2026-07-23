<script setup>
import { ref } from 'vue'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import AuthShell from '../components/AuthShell.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { requestEmailVerification } from '../api/auth'
import { usePendingVerificationEmail } from '../auth/session'

const email = usePendingVerificationEmail()
const isLoading = ref(false)
const message = ref('')
const isError = ref(false)

async function resend() {
  if (!email.value || isLoading.value) return
  isLoading.value = true
  message.value = ''
  isError.value = false
  try {
    await requestEmailVerification(email.value)
    message.value = '인증 메일을 다시 보냈습니다.'
  } catch (error) {
    isError.value = true
    message.value = error.message || '메일을 다시 보내지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <DefaultLayout>
    <AuthShell
      title="이메일을 확인해 주세요"
      description="받은 메일의 인증 버튼을 누르면 이메일 로그인을 시작할 수 있습니다."
    >
      <BaseCard class="p-8 text-center">
        <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-accent text-2xl">
          ✉️
        </div>
        <p class="mt-5 text-sm leading-6 text-text-sub">
          회원가입이 완료되었습니다. 인증 링크는 보안을 위해 한 번만 사용할 수 있습니다.
        </p>
        <p
          v-if="email"
          class="mt-2 break-all text-sm font-semibold text-text-main"
        >
          {{ email }}
        </p>
        <p
          v-if="message"
          class="mt-4 text-sm"
          :class="isError ? 'text-red-500' : 'text-green-600'"
          role="status"
        >
          {{ message }}
        </p>
        <BaseButton
          v-if="email"
          block
          variant="outline"
          class="mt-6"
          :disabled="isLoading"
          @click="resend"
        >
          {{ isLoading ? '발송 중...' : '인증 메일 다시 보내기' }}
        </BaseButton>
        <RouterLink
          class="mt-6 inline-block text-sm font-semibold text-primary"
          to="/login"
        >
          로그인 화면으로 이동
        </RouterLink>
      </BaseCard>
    </AuthShell>
  </DefaultLayout>
</template>
