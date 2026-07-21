<script setup>
defineProps({
  currentPage: { type: Number, required: true },
  totalPages: { type: Number, required: true },
})
defineEmits(['update:currentPage'])
</script>

<template>
  <div class="flex items-center justify-center gap-1">
    <button
      v-for="page in totalPages"
      :key="page"
      class="h-8 w-8 rounded-md text-sm font-medium transition-colors"
      :class="currentPage === page
        ? 'bg-primary-gradient text-white'
        : 'text-text-sub hover:bg-bg'"
      @click="$emit('update:currentPage', page)"
    >
      {{ page }}
    </button>
  </div>
</template>
