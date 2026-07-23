<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <footer class="w-full border-t border-border bg-surface">
    <div class="mx-auto flex max-w-[1200px] flex-col gap-4 px-6 py-7 text-sm text-text-sub sm:flex-row sm:items-center sm:justify-between lg:px-10">
      <p>© 2026 L1MIT. All rights reserved.</p>
      <nav
        class="flex flex-wrap gap-x-5 gap-y-2"
        aria-label="정책 및 운영 메뉴"
      >
        <RouterLink
          to="/terms/service"
          class="hover:text-text-main"
        >
          이용약관
        </RouterLink>
        <RouterLink
          to="/terms/privacy"
          class="font-semibold text-text-main hover:text-primary"
        >
          개인정보 처리방침
        </RouterLink>
        <RouterLink
          to="/mypage/social-accounts"
          class="hover:text-text-main"
        >
          소셜 계정 관리
        </RouterLink>
        <RouterLink
          to="/admin"
          class="hover:text-text-main"
        >
          관리자 페이지
        </RouterLink>
      </nav>
    </div>
  </footer>
</template>
