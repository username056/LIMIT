<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <!-- 배경을 두지 않아 페이지 바탕이 그대로 비칩니다. 위쪽 선만으로 본문과 구분합니다. -->
  <footer class="w-full border-t border-border">
    <div class="mx-auto grid max-w-[1200px] grid-cols-1 items-center gap-4 px-6 py-7 text-sm text-text-sub sm:grid-cols-3 lg:px-10">
      <nav
        class="flex flex-wrap justify-center gap-x-5 gap-y-2 sm:justify-start"
        aria-label="정책 메뉴"
      >
        <RouterLink
          to="/terms/service"
          class="hover:text-text-main"
        >
          이용약관
        </RouterLink>
        <RouterLink
          to="/terms/privacy"
          class="hover:text-text-main"
        >
          개인정보처리방침
        </RouterLink>
      </nav>

      <p class="text-center">
        © 2026 LIMIT. All rights reserved.
      </p>

      <nav
        class="flex justify-center gap-x-5 gap-y-2 sm:justify-end"
        aria-label="운영 메뉴"
      >
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
