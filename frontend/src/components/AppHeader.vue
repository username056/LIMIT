<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { clearAuthSession, useAuthSession } from '../auth/session'
import limitLogo from '../assets/limit_logo.png'

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

const route = useRoute()
const router = useRouter()
const session = useAuthSession()
const searchQuery = ref('')
const member = computed(() => session.value?.member || null)
const isProfileMenuOpen = ref(false)

function isActiveNavItem(href) {
  if (href === '/') {
    return route.name === 'coming-soon' && route.params.feature === 'product-detail'
  }
  return route.path === href
}

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
  isProfileMenuOpen.value = false
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
          class="flex items-center"
        >
          <img
            :src="limitLogo"
            alt="LIMIT"
            class="-mt-[6.4px] h-8 w-auto"
          >
        </RouterLink>

        <nav class="hidden items-center gap-7 md:flex">
          <RouterLink
            v-for="item in navItems"
            :key="item.label"
            :to="item.href"
            class="group relative text-base transition-colors hover:font-bold hover:text-text-main"
            :class="isActiveNavItem(item.href)
              ? 'font-bold text-text-main'
              : 'font-semibold text-text-sub'"
          >
            {{ item.label }}
            <span
              class="absolute -bottom-2 left-0 right-0 h-0.5 rounded-full transition-colors group-hover:bg-primary"
              :class="isActiveNavItem(item.href) ? 'bg-primary' : 'bg-transparent'"
              aria-hidden="true"
            />
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
            class="w-28 bg-transparent text-base text-text-main placeholder:text-text-sub focus:outline-none lg:w-36"
          >
        </form>

        <template v-if="member">
          <RouterLink
            :to="{ name: 'coming-soon', params: { feature: 'notifications' } }"
            aria-label="알림"
            class="text-text-sub hover:text-text-main"
          >
            <svg
              class="h-5 w-5"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              aria-hidden="true"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 17h5l-1.4-1.4A2 2 0 0118 14.2V11a6 6 0 10-12 0v3.2a2 2 0 01-.6 1.4L4 17h5m6 0a3 3 0 11-6 0m6 0H9"
              />
            </svg>
          </RouterLink>

          <div class="relative">
            <button
              type="button"
              aria-haspopup="true"
              :aria-expanded="isProfileMenuOpen"
              aria-label="내 계정"
              class="flex h-8 w-8 items-center justify-center rounded-full border border-border bg-bg text-text-sub hover:border-primary hover:text-primary"
              @click="isProfileMenuOpen = !isProfileMenuOpen"
            >
              <svg
                class="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.5 20.25a7.5 7.5 0 0115 0"
                />
              </svg>
            </button>

            <template v-if="isProfileMenuOpen">
              <div
                class="fixed inset-0 z-10"
                @click="isProfileMenuOpen = false"
              />
              <div class="absolute right-0 top-full z-20 mt-2 w-44 rounded-md border border-border bg-surface p-2 shadow-elevated">
                <p class="truncate px-2 py-1.5 text-xs text-text-sub">
                  {{ member.nickname || '회원' }}
                </p>
                <RouterLink
                  to="/mypage/profile"
                  class="block rounded px-2 py-1.5 text-sm text-text-main hover:bg-bg"
                  @click="isProfileMenuOpen = false"
                >
                  마이페이지
                </RouterLink>
                <button
                  type="button"
                  class="block w-full rounded px-2 py-1.5 text-left text-sm text-text-main hover:bg-bg"
                  @click="logoutMember"
                >
                  로그아웃
                </button>
              </div>
            </template>
          </div>
        </template>
        <template v-else>
          <RouterLink
            to="/login"
            class="hidden text-base font-semibold text-text-sub hover:text-text-main sm:inline"
          >
            로그인
          </RouterLink>
          <RouterLink
            to="/signup"
            class="rounded-md bg-primary-gradient px-4 py-2.5 text-base font-semibold text-white shadow-elevated"
          >
            회원가입
          </RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>
