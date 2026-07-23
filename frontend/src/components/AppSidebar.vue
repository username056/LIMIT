<script setup>
defineProps({
  items: {
    // [{ label: '대시보드', icon: '', active: true, href: '/seller/dashboard' }, ...]
    type: Array,
    required: true,
  },
})
</script>

<template>
  <aside class="w-full shrink-0 rounded-lg border border-border bg-surface p-3 md:w-56 md:rounded-none md:border-y-0 md:border-l-0 md:border-r md:p-4">
    <nav class="space-y-1">
      <RouterLink
        v-for="item in items"
        :key="item.label"
        :to="item.href"
        :aria-current="item.active ? 'page' : undefined"
        class="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors"
        :class="item.active
          ? 'bg-accent text-primary'
          : 'text-text-sub hover:bg-bg hover:text-text-main'"
      >
        <span
          v-if="item.icon"
          class="text-base"
        >{{ item.icon }}</span>
        {{ item.label }}
      </RouterLink>
    </nav>
  </aside>
</template>
