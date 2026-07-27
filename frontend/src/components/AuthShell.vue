<script setup>
defineProps({
  eyebrow: { type: String, default: 'LIMIT' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  width: { type: String, default: 'md' },
})
</script>

<template>
  <section
    class="mx-auto w-full px-5 py-12 sm:py-16"
    :class="width === 'lg' ? 'max-w-[640px]' : 'max-w-[508px]'"
  >
    <header class="mb-8 text-center">
      <p class="mb-3 bg-primary-gradient bg-clip-text text-sm font-extrabold tracking-[0.16em] text-transparent">
        {{ eyebrow }}
      </p>
      <h1 class="text-3xl font-bold tracking-[-0.03em] text-text-main">
        {{ title }}
      </h1>
      <p
        v-if="description"
        class="mx-auto mt-3 max-w-lg text-sm leading-6 text-text-sub"
      >
        {{ description }}
      </p>
    </header>
    <slot />
  </section>
</template>
