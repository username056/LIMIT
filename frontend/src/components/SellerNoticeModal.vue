<script setup>
defineProps({
  open: { type: Boolean, default: false },
})
defineEmits(['close', 'apply'])
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
    role="alertdialog"
    aria-modal="true"
    aria-label="판매자 등록 안내"
    @click.self="$emit('close')"
  >
    <div class="w-full max-w-sm rounded-lg bg-surface p-6 text-center shadow-elevated">
      <h2 class="text-base font-bold text-text-main">
        판매자 등록을 해주세요!
      </h2>
      <p class="mt-2 text-sm leading-6 text-text-sub">
        상품을 등록하려면 판매자 등록이 먼저 필요합니다.
        별도 심사 없이 바로 완료됩니다.
      </p>
      <div class="mt-5 flex gap-3">
        <button
          type="button"
          class="flex-1 rounded-md border border-border px-4 py-2.5 text-sm font-semibold text-text-sub transition hover:border-primary hover:text-primary"
          @click="$emit('close')"
        >
          나중에
        </button>
        <button
          type="button"
          class="flex-1 rounded-md bg-primary-gradient px-4 py-2.5 text-sm font-bold text-white shadow-elevated transition hover:brightness-110"
          @click="$emit('apply')"
        >
          판매자 등록하기
        </button>
      </div>
    </div>
  </div>
</template>
