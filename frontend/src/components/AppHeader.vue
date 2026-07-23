<script setup>
defineProps({
  navItems: {
    type: Array,
    default: () => [
      { label: '상품 둘러보기', href: '/' },
      { label: '판매하기', href: '/seller/apply' },
      { label: '채팅', href: '/chat' },
    ],
  },
})
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
        <label class="hidden items-center gap-2 rounded-md border border-border bg-bg px-3 py-2 sm:flex">
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
          <span class="sr-only">상품 검색</span>
          <input
            type="search"
            placeholder="상품 검색..."
            class="w-28 bg-transparent text-sm text-text-main placeholder:text-text-sub focus:outline-none lg:w-36"
          >
        </label>

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
      </div>
    </div>
  </header>
</template>
