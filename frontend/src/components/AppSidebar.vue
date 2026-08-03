<script setup>
defineProps({
  items: {
    // [{ label: '대시보드', active: true, href: '/seller/dashboard' }, ...]
    type: Array,
    required: true,
  },
})
</script>

<template>
  <!--
    내려도 따라옵니다. 마이페이지는 주문 내역처럼 목록이 긴 화면이 많은데, 메뉴가 맨 위에
    붙어 있으면 다른 메뉴로 가려고 화면을 끝까지 되감아 올려야 합니다.

    붙는 위치(top)는 헤더 높이 72px에 한 칸 띄운 값입니다. 헤더가 바뀌면 같이 맞춰 주세요.
    좁은 화면에서는 위아래로 쌓이므로 붙이지 않습니다(md 이상에서만).
  -->
  <aside
    class="w-full shrink-0 rounded-lg bg-surface p-3 shadow-card md:sticky md:top-[88px] md:w-56 md:self-start"
  >
    <nav class="space-y-1">
      <RouterLink
        v-for="item in items"
        :key="item.label"
        :to="item.href"
        :aria-current="item.active ? 'page' : undefined"
        class="sidebar__link block rounded-pill px-4 py-2.5 text-sm font-medium"
        :class="item.active
          ? 'sidebar__link--active bg-accent text-primary'
          : 'text-text-sub hover:bg-bg hover:text-text-main'"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </aside>
</template>

<style scoped>
/*
  메뉴에 커서를 올리거나 고르면 살짝 당겨지듯 움직입니다.
  끝에서 한 번 넘어갔다 잡히는 곡선(세 번째 값이 1을 넘습니다)이라, 그냥 미끄러지는 것보다
  손에 걸리는 느낌이 납니다.
*/
.sidebar__link {
  position: relative;
  transition:
    transform 0.34s cubic-bezier(0.34, 1.56, 0.64, 1),
    background-color 0.2s ease,
    color 0.2s ease;
}

.sidebar__link:hover {
  transform: translateX(3px);
}

.sidebar__link--active {
  transform: translateX(4px);
  font-weight: 700;
}

/* 움직임을 줄여 달라고 설정한 사용자에게는 이동을 걸지 않습니다. */
@media (prefers-reduced-motion: reduce) {
  .sidebar__link {
    transition: none;
  }

  .sidebar__link:hover,
  .sidebar__link--active {
    transform: none;
  }
}
</style>
