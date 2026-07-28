<script setup>
import { openDaumPostcode } from '../utils/daumPostcode'

const props = defineProps({
  modelValue: {
    // { zonecode: string, address: string, addressDetail: string }
    type: Object,
    required: true,
  },
  label: { type: String, default: '주소' },
  required: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

async function search() {
  const result = await openDaumPostcode()
  emit('update:modelValue', { ...props.modelValue, ...result })
}

function updateDetail(event) {
  emit('update:modelValue', { ...props.modelValue, addressDetail: event.target.value })
}
</script>

<template>
  <div>
    <span
      v-if="label"
      class="mb-2 block text-sm font-medium text-text-main"
    >{{ label }}</span>
    <div class="flex gap-2">
      <input
        type="text"
        :value="modelValue.address"
        readonly
        :required="required"
        placeholder="우편번호 검색을 눌러 주소를 입력하세요"
        class="w-full cursor-pointer rounded-md border border-border bg-bg px-4 py-3 text-sm text-text-main placeholder:text-text-sub outline-none"
        @click="search"
      >
      <button
        type="button"
        class="shrink-0 whitespace-nowrap rounded-md border border-border bg-surface px-4 py-3 text-sm font-semibold text-text-main transition-colors hover:border-primary hover:text-primary"
        @click="search"
      >
        우편번호 검색
      </button>
    </div>
    <input
      type="text"
      :value="modelValue.addressDetail"
      placeholder="상세 주소 (동/호수 등)"
      class="mt-2 w-full rounded-md border border-border bg-surface px-4 py-3 text-sm text-text-main placeholder:text-text-sub outline-none transition-colors focus:border-primary"
      @input="updateDetail"
    >
    <p
      v-if="modelValue.zonecode"
      class="mt-1 text-[13px] text-text-sub"
    >
      우편번호 {{ modelValue.zonecode }}
    </p>
  </div>
</template>
