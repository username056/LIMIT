<script setup>
defineProps({
  padded: { type: Boolean, default: true },
})
</script>

<template>
  <div
    class="card-soft rounded-lg bg-surface"
    :class="padded ? 'p-6' : ''"
  >
    <slot />
  </div>
</template>
