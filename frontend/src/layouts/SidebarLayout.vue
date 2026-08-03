<script setup>
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'
import AppSidebar from '../components/AppSidebar.vue'

defineProps({
  sidebarItems: {
    type: Array,
    required: true,
  },
})
</script>

<template>
  <div class="flex min-h-screen flex-col bg-bg font-main text-text-main">
    <AppHeader />

    <!--
      폭과 좌우 여백은 index.css의 .page-shell과 같은 값입니다. 예전에는 max-w-6xl(1152px)에
      px-6 고정이라, 목록에서 마이페이지로 넘어올 때 화면 양옆이 미묘하게 달라졌습니다.
      .page-shell의 폭을 바꾸면 이 값도 같이 맞춰 주세요.

      md:items-start는 사이드바가 화면을 따라 내려오게 하는 데 필요합니다. 기본값(stretch)이면
      사이드바가 본문 높이만큼 늘어나 sticky가 붙을 자리를 잃습니다.
    -->
    <div class="mx-auto flex w-full max-w-[1080px] flex-1 flex-col gap-6 px-4 py-8 sm:px-6 md:flex-row md:items-start lg:px-10 lg:py-12">
      <AppSidebar :items="sidebarItems" />
      <main class="min-w-0 flex-1">
        <slot />
      </main>
    </div>

    <AppFooter />
  </div>
</template>
