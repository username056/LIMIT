<script setup>
import { ref } from 'vue'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import AuthShell from '../components/AuthShell.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import { requestPasswordReset } from '../api/auth'

const email = ref('')
const isLoading = ref(false)
const isSubmitted = ref(false)
const errorMessage = ref('')

async function submit() {
  if (isLoading.value) return
  isLoading.value = true
  errorMessage.value = ''
  try {
    await requestPasswordReset(email.value)
    isSubmitted.value = true
  } catch (error) {
    errorMessage.value = error.message || '재설정 메일을 요청하지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <DefaultLayout>
    <AuthShell
      title="비밀번호 찾기"
      description="가입한 이메일로 안전한 일회용 비밀번호 재설정 링크를 보내드려요."
    >
      <BaseCard class="overflow-hidden">
        <div class="border-b border-border bg-accent px-7 py-5 sm:px-8">
          <div class="flex items-center gap-3">
            <span class="h-10 w-10 shrink-0 rounded-full bg-white shadow-card" />
            <div>
              <p class="text-sm font-semibold text-text-main">
                이메일을 확인해 주세요
              </p>
              <p class="mt-1 text-xs leading-5 text-text-sub">
                링크는 발송 후 15분 동안 한 번만 사용할 수 있습니다.
              </p>
            </div>
          </div>
        </div>

        <div class="p-7 sm:p-8">
          <div
            v-if="isSubmitted"
            class="text-center"
            role="status"
          >
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-green-50 text-2xl text-green-600">
              ✓
            </div>
            <h2 class="mt-5 text-lg font-bold text-text-main">
              메일 요청을 접수했습니다
            </h2>
            <p class="mt-3 text-sm leading-6 text-text-sub">
              입력한 이메일로 가입한 계정이 있다면 재설정 링크가 도착합니다.<br>
              보이지 않으면 스팸함도 확인해 주세요.
            </p>
            <BaseButton
              to="/login"
              block
              class="mt-7"
            >
              로그인으로 돌아가기
            </BaseButton>
          </div>

          <form
            v-else
            @submit.prevent="submit"
          >
            <BaseInput
              v-model="email"
              label="가입 이메일"
              type="email"
              autocomplete="email"
              required
              placeholder="name@example.com"
            />
            <p class="mt-3 text-xs leading-5 text-text-sub">
              계정 보호를 위해 가입 여부와 관계없이 동일한 안내를 표시합니다.
            </p>
            <p
              v-if="errorMessage"
              class="mt-5 rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
              role="alert"
            >
              {{ errorMessage }}
            </p>
            <BaseButton
              block
              class="mt-6"
              type="submit"
              :disabled="isLoading"
            >
              {{ isLoading ? '메일 요청 중...' : '재설정 링크 받기' }}
            </BaseButton>
            <RouterLink
              class="mt-5 block text-center text-sm font-semibold text-text-sub transition-colors hover:text-primary"
              to="/login"
            >
              로그인으로 돌아가기
            </RouterLink>
          </form>
        </div>
      </BaseCard>
    </AuthShell>
  </DefaultLayout>
</template>
