<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { clearAuthSession, useAuthSession } from '../auth/session'

defineProps({
  navItems: {
    type: Array,
    default: () => [
      { label: '상품 둘러보기', href: '/' },
      { label: '판매하기', href: '/seller/apply' },
      { label: '채팅', href: '/coming-soon/chat' },
    ],
  },
})

const router = useRouter()
const session = useAuthSession()
const searchQuery = ref('')
const member = computed(() => session.value?.member || null)

async function submitSearch() {
  const query = searchQuery.value.trim()
  if (!query) return
  await router.push({
    name: 'coming-soon',
    params: { feature: 'product-search' },
    query: { q: query },
  })
}

async function logoutMember() {
  try {
    await logout()
  } finally {
    clearAuthSession()
    await router.push('/')
  }
}
</script>

<template>
  <header class="w-full border-b border-border bg-surface">
    <div class="mx-auto flex h-[72px] max-w-[1200px] items-center justify-between px-6 lg:px-10">
      <div class="flex items-center gap-10">
        <RouterLink
          to="/"
          class="bg-primary-gradient bg-clip-text text-xl font-extrabold tracking-[-0.04em] text-transparent"
        >
          L1MIT
        </RouterLink>

        <nav class="hidden items-center gap-7 md:flex">
          <RouterLink
            v-for="item in navItems"
            :key="item.label"
            :to="item.href"
            class="text-sm font-semibold text-text-sub transition-colors hover:text-text-main"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
      </div>

      <div class="flex items-center gap-4">
        <form
          class="hidden items-center gap-2 rounded-md border border-border bg-bg px-3 py-2 sm:flex"
          role="search"
          @submit.prevent="submitSearch"
        >
          <button
            type="submit"
            aria-label="상품 검색"
            class="text-text-sub hover:text-primary"
          >
            <svg
              class="h-4 w-4 text-text-sub"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              aria-hidden="true"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z"
              />
            </svg>
          </button>
          <label
            for="global-product-search"
            class="sr-only"
          >상품 검색</label>
          <input
            id="global-product-search"
            v-model="searchQuery"
            type="search"
            placeholder="상품 검색..."
            class="w-28 bg-transparent text-sm text-text-main placeholder:text-text-sub focus:outline-none lg:w-36"
          >
        </form>

        <template v-if="member">
          <RouterLink
            to="/mypage/profile"
            class="hidden text-sm font-semibold text-text-sub hover:text-text-main sm:inline"
          >
            {{ member.nickname || '마이페이지' }}
          </RouterLink>
          <button
            type="button"
            class="rounded-md border border-border px-4 py-2.5 text-sm font-semibold text-text-main hover:border-primary hover:text-primary"
            @click="logoutMember"
          >
            로그아웃
          </button>
        </template>
        <template v-else>
          <RouterLink
            to="/login"
            class="hidden text-sm font-semibold text-text-sub hover:text-text-main sm:inline"
          >
            로그인
          </RouterLink>
          <RouterLink
            to="/signup"
            class="rounded-md bg-primary-gradient px-4 py-2.5 text-sm font-semibold text-white shadow-elevated"
          >
            회원가입
          </RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>
