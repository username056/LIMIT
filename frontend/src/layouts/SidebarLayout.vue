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

    <div class="mx-auto flex w-full max-w-6xl flex-1 gap-6 px-6 py-6">
      <AppSidebar :items="sidebarItems" />
      <main class="min-w-0 flex-1">
        <slot />
      </main>
    </div>

    <AppFooter />
  </div>
</template>
