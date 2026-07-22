<script setup>
defineProps({
  navItems: {
    type: Array,
    default: () => ['휴대폰', '태블릿', '노트북', '카메라'],
  },
})
</script>

<template>
  <header class="w-full border-b border-border bg-surface">
    <div class="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
      <!-- Logo: LIMIT, 첫 I만 primary 색 -->
      <div class="flex items-center gap-10">
        <a
          href="/"
          class="text-lg font-bold text-text-main"
        >
          L<span class="text-primary">I</span>MIT
        </a>

        <!-- Nav -->
        <nav class="hidden items-center gap-6 md:flex">
          <a
            v-for="(item, idx) in navItems"
            :key="item"
            href="#"
            class="text-sm font-medium transition-colors"
            :class="idx === 0
              ? 'text-text-main border-b-2 border-primary pb-[2px]'
              : 'text-text-sub hover:text-text-main'"
          >
            {{ item }}
          </a>
        </nav>
      </div>

      <!-- Right actions -->
      <div class="flex items-center gap-4">
        <div class="hidden items-center gap-2 rounded-md border border-border bg-bg px-3 py-2 sm:flex">
          <svg
            class="h-4 w-4 text-text-sub"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z"
            />
          </svg>
          <input
            type="text"
            placeholder="상품 검색..."
            class="w-32 bg-transparent text-sm text-text-main placeholder:text-text-sub focus:outline-none"
          >
        </div>

        <button
          class="text-text-sub hover:text-text-main"
          aria-label="장바구니"
        >
          <svg
            class="h-5 w-5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"
            />
          </svg>
        </button>

        <RouterLink
          to="/mypage/social-accounts"
          class="text-text-sub hover:text-text-main"
          aria-label="내 계정"
        >
          <svg
            class="h-5 w-5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
            />
          </svg>
        </RouterLink>
      </div>
    </div>
  </header>
</template>
