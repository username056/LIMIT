<script setup>
defineProps({
  provider: { type: String, required: true },
  label: { type: String, required: true },
  loading: { type: Boolean, default: false },
})
defineEmits(['click'])

const providerStyles = {
  google: 'border-border bg-white text-text-main',
  kakao: 'border-[#FEE500] bg-[#FEE500] text-[#191919]',
  naver: 'border-[#03C75A] bg-[#03C75A] text-white',
}

const providerMarks = { google: 'G', kakao: 'K', naver: 'N' }
</script>

<template>
  <button
    type="button"
    class="flex min-h-12 items-center justify-center gap-2 rounded-md border px-3 text-sm font-bold transition hover:-translate-y-0.5 hover:shadow-card disabled:cursor-wait disabled:opacity-60"
    :class="providerStyles[provider]"
    :disabled="loading"
    @click="$emit('click')"
  >
    <span
      class="flex h-5 w-5 items-center justify-center rounded-full bg-white text-xs font-black"
      :class="provider === 'naver' ? 'text-[#03C75A]' : 'text-text-main'"
    >
      {{ providerMarks[provider] }}
    </span>
    {{ loading ? '연결 중' : label }}
  </button>
</template>
