<script setup>
import limitLogo from '../assets/limit_logo.png'

defineProps({
  adminName: { type: String, required: true },
  adminRole: { type: String, required: true },
  activeSection: { type: String, required: true },
  sections: { type: Array, required: true },
})
defineEmits(['select', 'logout'])
</script>

<template>
  <div class="min-h-screen bg-[#F7F9FC] text-text-main lg:grid lg:grid-cols-[240px_minmax(0,1fr)]">
    <aside class="border-b border-border bg-white px-5 py-6 lg:min-h-screen lg:border-b-0 lg:border-r">
      <div class="flex items-center justify-between lg:block">
        <RouterLink
          to="/"
          class="flex items-center"
        >
          <img
            :src="limitLogo"
            alt="LIMIT"
            class="h-7 w-auto"
          >
        </RouterLink>
        <button
          class="text-xs font-semibold text-text-sub hover:text-text-main lg:hidden"
          @click="$emit('logout')"
        >
          로그아웃
        </button>
      </div>

      <nav
        class="mt-6 flex gap-2 overflow-x-auto lg:mt-10 lg:flex-col"
        aria-label="관리자 메뉴"
      >
        <button
          v-for="section in sections"
          :key="section.id"
          class="flex shrink-0 items-center gap-3 rounded-md px-4 py-3 text-left text-sm font-semibold transition"
          :class="activeSection === section.id ? 'bg-accent text-primary' : 'text-text-sub hover:bg-bg hover:text-text-main'"
          @click="$emit('select', section.id)"
        >
          <span
            class="h-2 w-2 rounded-full"
            :class="activeSection === section.id ? 'bg-primary' : 'bg-border'"
          />
          {{ section.label }}
        </button>
      </nav>

      <div class="mt-6 hidden rounded-lg border border-border bg-bg p-4 lg:block">
        <p class="truncate text-sm font-bold text-text-main">
          {{ adminName }}
        </p>
        <p class="mt-1 text-xs text-text-sub">
          {{ adminRole }}
        </p>
        <button
          class="mt-4 text-xs font-semibold text-text-sub hover:text-red-500"
          @click="$emit('logout')"
        >
          관리자 로그아웃
        </button>
      </div>
    </aside>

    <main class="min-w-0 p-5 sm:p-8 lg:p-10">
      <slot />
    </main>
  </div>
</template>
