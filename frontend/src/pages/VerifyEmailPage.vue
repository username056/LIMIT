<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import AuthShell from '../components/AuthShell.vue'
import BaseCard from '../components/BaseCard.vue'
import { verifyEmail } from '../api/auth'

const route = useRoute()
const status = ref('VERIFYING')
const message = ref('이메일 인증 정보를 확인하고 있습니다...')

onMounted(async () => {
  const token = String(route.query.token || '')
  window.history.replaceState({}, document.title, '/verify-email')
  if (!token) {
    status.value = 'FAILED'
    message.value = '인증 토큰이 없습니다. 이메일의 링크를 다시 확인해 주세요.'
    return
  }
  try {
    await verifyEmail(token)
    status.value = 'VERIFIED'
    message.value = '이메일 인증이 완료되었습니다. 이제 로그인할 수 있습니다.'
  } catch (error) {
    status.value = 'FAILED'
    message.value = error.message || '인증 링크가 만료되었거나 이미 사용되었습니다.'
  }
})
</script>

<template>
  <DefaultLayout>
    <AuthShell
      title="이메일 인증"
      description="링크의 유효성과 일회용 토큰을 안전하게 확인합니다."
    >
      <BaseCard class="p-8 text-center">
        <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-accent text-2xl">
          {{ status === 'VERIFIED' ? '✓' : status === 'FAILED' ? '!' : '…' }}
        </div>
        <p
          class="mt-3 text-sm leading-6"
          :class="status === 'FAILED' ? 'text-red-500' : 'text-text-sub'"
          role="status"
        >
          {{ message }}
        </p>
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
