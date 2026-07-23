<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
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
    <section class="mx-auto max-w-md px-6 py-20 text-center">
      <BaseCard>
        <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-accent text-2xl">
          {{ status === 'VERIFIED' ? '✓' : status === 'FAILED' ? '!' : '…' }}
        </div>
        <h1 class="mt-5 text-2xl font-bold text-text-main">
          이메일 인증
        </h1>
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
    </section>
  </DefaultLayout>
</template>
