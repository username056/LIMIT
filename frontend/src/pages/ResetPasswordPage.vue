<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import AuthShell from '../components/AuthShell.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseButton from '../components/BaseButton.vue'
import { resetPassword } from '../api/auth'

const route = useRoute()
const router = useRouter()
const token = ref('')
const isLoading = ref(false)
const isInvalidLink = ref(false)
const errorMessage = ref('')
const form = reactive({
  newPassword: '',
  newPasswordConfirm: '',
})

const passwordRuleMet = computed(() =>
  /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(form.newPassword))

onMounted(() => {
  token.value = typeof route.query.token === 'string' ? route.query.token : ''
  window.history.replaceState({}, document.title, '/reset-password')
  isInvalidLink.value = !token.value
})

async function submit() {
  if (isLoading.value) return
  errorMessage.value = ''
  if (!passwordRuleMet.value) {
    errorMessage.value = '비밀번호는 영문과 숫자를 포함해 8~72자로 입력해 주세요.'
    return
  }
  if (form.newPassword !== form.newPasswordConfirm) {
    errorMessage.value = '비밀번호 확인이 일치하지 않습니다.'
    return
  }

  isLoading.value = true
  try {
    await resetPassword(token.value, form.newPassword)
    await router.replace({ name: 'login', query: { passwordChanged: '1' } })
  } catch (error) {
    errorMessage.value = error.message || '비밀번호를 변경하지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <DefaultLayout>
    <AuthShell
      title="새 비밀번호 설정"
      description="다른 서비스에서 사용하지 않는 안전한 비밀번호로 계정을 보호해 주세요."
    >
      <BaseCard class="overflow-hidden">
        <div class="border-b border-border bg-accent px-7 py-5 sm:px-8">
          <div class="flex items-center gap-3">
            <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-lg shadow-card">
              🔒
            </span>
            <div>
              <p class="text-sm font-semibold text-text-main">
                안전한 비밀번호 사용
              </p>
              <p class="mt-1 text-xs leading-5 text-text-sub">
                영문과 숫자를 포함한 8자 이상으로 설정해 주세요.
              </p>
            </div>
          </div>
        </div>

        <div class="p-7 sm:p-8">
          <div
            v-if="isInvalidLink"
            class="text-center"
            role="alert"
          >
            <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-50 text-2xl text-red-500">
              !
            </div>
            <h2 class="mt-5 text-lg font-bold text-text-main">
              재설정 링크를 확인해 주세요
            </h2>
            <p class="mt-3 text-sm leading-6 text-text-sub">
              토큰이 없거나 링크가 올바르지 않습니다.<br>
              새 재설정 메일을 요청해 주세요.
            </p>
            <BaseButton
              to="/forgot-password"
              block
              class="mt-7"
            >
              재설정 메일 다시 받기
            </BaseButton>
          </div>

          <form
            v-else
            class="space-y-5"
            @submit.prevent="submit"
          >
            <BaseInput
              v-model="form.newPassword"
              label="새 비밀번호"
              type="password"
              autocomplete="new-password"
              required
              placeholder="영문·숫자 포함 8자 이상"
            />
            <BaseInput
              v-model="form.newPasswordConfirm"
              label="새 비밀번호 확인"
              type="password"
              autocomplete="new-password"
              required
              placeholder="한 번 더 입력해 주세요"
            />

            <div class="rounded-md border border-border bg-bg px-4 py-3">
              <p
                class="flex items-center gap-2 text-xs"
                :class="passwordRuleMet ? 'text-green-600' : 'text-text-sub'"
              >
                <span aria-hidden="true">{{ passwordRuleMet ? '✓' : '○' }}</span>
                영문과 숫자를 포함한 8~72자
              </p>
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
              {{ isLoading ? '변경 중...' : '비밀번호 변경' }}
            </BaseButton>
          </form>
        </div>
      </BaseCard>
    </AuthShell>
  </DefaultLayout>
</template>
