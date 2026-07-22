<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import { completeSocialLink, completeSocialLogin } from '../api/auth'
import { consumeOAuthMode, isSupportedProvider, oauthRedirectUri } from '../auth/oauth'
import { setAuthSession, setPendingSocialSignup } from '../auth/session'

const route = useRoute()
const router = useRouter()
const statusMessage = ref('소셜 계정을 안전하게 확인하고 있습니다...')
const isFailed = ref(false)

onMounted(async () => {
  const provider = String(route.params.provider || '').toLowerCase()
  const code = String(route.query.code || '')
  const state = String(route.query.state || '')
  const providerError = route.query.error
  const mode = consumeOAuthMode()
  window.history.replaceState({}, document.title, `/auth/callback/${provider}`)

  try {
    if (!isSupportedProvider(provider)) throw new Error('지원하지 않는 소셜 로그인 공급자입니다.')
    if (providerError) throw new Error('소셜 로그인 동의가 취소되었거나 거절되었습니다.')
    if (!code || !state) throw new Error('소셜 로그인 확인 정보가 없습니다.')

    if (mode === 'link') {
      await completeSocialLink(provider, code, oauthRedirectUri(provider), state)
      await router.replace('/mypage/social-accounts?linked=true')
      return
    }
    const result = await completeSocialLogin(provider, code, oauthRedirectUri(provider), state)
    if (result.status === 'SIGNUP_REQUIRED') {
      setPendingSocialSignup(result.signup)
      await router.replace('/signup/social')
      return
    }
    setAuthSession(result)
    statusMessage.value = '로그인이 완료되었습니다. 잠시 후 이동합니다.'
    await router.replace('/')
  } catch (error) {
    isFailed.value = true
    statusMessage.value = error.code === 'AUTH014'
      ? '이미 가입된 이메일입니다. 기존 계정으로 로그인한 뒤 소셜 계정을 연결해 주세요.'
      : error.message || '소셜 로그인에 실패했습니다.'
  }
})
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto max-w-md px-6 py-20 text-center">
      <BaseCard>
        <h1 class="text-xl font-bold text-text-main">
          소셜 로그인
        </h1>
        <p
          class="mt-4 text-sm"
          :class="isFailed ? 'text-red-500' : 'text-text-sub'"
          role="status"
        >
          {{ statusMessage }}
        </p>
        <RouterLink
          v-if="isFailed"
          class="mt-6 inline-block text-sm font-semibold text-primary"
          to="/login"
        >
          로그인 화면으로 돌아가기
        </RouterLink>
      </BaseCard>
    </section>
  </DefaultLayout>
</template>
