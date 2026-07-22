<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import BaseCard from '../components/BaseCard.vue'
import BaseButton from '../components/BaseButton.vue'
import { getSocialAccounts, unlinkSocialAccount } from '../api/auth'
import { startOAuthLink } from '../auth/oauth'
import { useAuthSession } from '../auth/session'

const route = useRoute()
const router = useRouter()
const session = useAuthSession()
const accounts = ref([])
const isLoading = ref(true)
const activeProvider = ref('')
const message = ref(route.query.linked ? '소셜 계정이 연결되었습니다.' : '')
const errorMessage = ref('')
const providers = [
  { id: 'google', label: 'Google' },
  { id: 'kakao', label: '카카오' },
  { id: 'naver', label: '네이버' },
]

function accountFor(provider) {
  return accounts.value.find((account) => account.provider.toLowerCase() === provider)
}

async function load() {
  if (!session.value?.accessToken) {
    await router.replace('/login')
    return
  }
  try {
    accounts.value = await getSocialAccounts()
  } catch (error) {
    errorMessage.value = error.message || '연결된 계정을 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

async function link(provider) {
  activeProvider.value = provider
  errorMessage.value = ''
  try {
    await startOAuthLink(provider)
  } catch (error) {
    errorMessage.value = error.message
    activeProvider.value = ''
  }
}

async function unlink(account) {
  if (!window.confirm(`${account.provider} 계정 연결을 해제할까요?`)) return
  activeProvider.value = account.provider.toLowerCase()
  errorMessage.value = ''
  try {
    await unlinkSocialAccount(account.socialAccountId)
    accounts.value = accounts.value.filter((item) => item.socialAccountId !== account.socialAccountId)
    message.value = '소셜 계정 연결을 해제했습니다.'
  } catch (error) {
    errorMessage.value = error.message || '연결을 해제하지 못했습니다.'
  } finally {
    activeProvider.value = ''
  }
}

onMounted(load)
</script>

<template>
  <DefaultLayout>
    <section class="mx-auto max-w-2xl px-6 py-12">
      <div class="mb-8">
        <p class="mb-2 text-sm font-semibold text-primary">
          계정 보안
        </p>
        <h1 class="text-2xl font-bold text-text-main">
          소셜 계정 연결
        </h1>
        <p class="mt-2 text-sm text-text-sub">
          기존 계정으로 로그인한 상태에서만 새 로그인 수단을 연결할 수 있습니다.
        </p>
      </div>

      <BaseCard>
        <p
          v-if="message"
          class="mb-4 rounded-md bg-green-50 px-4 py-3 text-sm text-green-700"
          role="status"
        >
          {{ message }}
        </p>
        <p
          v-if="errorMessage"
          class="mb-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-600"
          role="alert"
        >
          {{ errorMessage }}
        </p>
        <p
          v-if="isLoading"
          class="text-sm text-text-sub"
        >
          연결 정보를 불러오고 있습니다...
        </p>
        <div
          v-else
          class="divide-y divide-border"
        >
          <div
            v-for="provider in providers"
            :key="provider.id"
            class="flex items-center justify-between gap-4 py-4"
          >
            <div>
              <p class="font-semibold text-text-main">
                {{ provider.label }}
              </p>
              <p class="mt-1 text-xs text-text-sub">
                {{ accountFor(provider.id)?.providerEmail || '연결되지 않음' }}
              </p>
            </div>
            <BaseButton
              v-if="accountFor(provider.id)"
              variant="outline"
              :disabled="Boolean(activeProvider)"
              @click="unlink(accountFor(provider.id))"
            >
              연결 해제
            </BaseButton>
            <BaseButton
              v-else
              :disabled="Boolean(activeProvider)"
              @click="link(provider.id)"
            >
              {{ activeProvider === provider.id ? '연결 중...' : '연결' }}
            </BaseButton>
          </div>
        </div>
      </BaseCard>
    </section>
  </DefaultLayout>
</template>
