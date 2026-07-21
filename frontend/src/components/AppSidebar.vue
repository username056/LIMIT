<script setup>
defineProps({
  items: {
    // [{ label: '대시보드', icon: '', active: true, href: '#' }, ...]
    type: Array,
    required: true,
  },
})
</script>

<template>
  <aside class="w-56 shrink-0 border-r border-border bg-surface p-4">
    <nav class="space-y-1">
      <a
        v-for="item in items"
        :key="item.label"
        :href="item.href || '#'"
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
      </a>
    </nav>
  </aside>
</template>
