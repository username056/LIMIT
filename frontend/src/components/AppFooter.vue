<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <footer class="w-full border-t border-border bg-surface">
    <div class="mx-auto flex max-w-6xl items-center justify-between px-6 py-6 text-sm text-text-sub">
      <p>© 2026 Limit. All rights reserved.</p>
      <RouterLink
        to="/admin"
        class="hover:text-text-main hover:underline"
      >
        관리자 페이지
      </RouterLink>
    </div>
  </footer>
</template>
