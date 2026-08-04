<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  label: { type: String, default: '' },
  modelValue: { type: String, default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  autocomplete: { type: String, default: '' },
  required: { type: Boolean, default: false },
  error: { type: String, default: '' }, // 에러 메시지가 있으면 빨간 테두리 + 아래에 메시지 표시
})
defineEmits(['update:modelValue'])

/*
  비밀번호는 눌러서 확인할 수 있게 둡니다.
  ---------------------------------------------------------------------------
  가려진 칸만 있으면 오타로 로그인이 실패했을 때 무엇을 잘못 쳤는지 알 수 없습니다.
  회원가입은 같은 비밀번호를 두 번 넣는데 둘 다 안 보여 더 답답합니다.

  type="password"인 칸에만 저절로 붙습니다. 쓰는 쪽에서 따로 켜지 않아도
  로그인·회원가입·비밀번호 재설정 다섯 칸이 함께 좋아집니다.
*/
const isPasswordField = computed(() => props.type === 'password')
const isRevealed = ref(false)

const inputType = computed(() => {
  if (!isPasswordField.value) return props.type
  return isRevealed.value ? 'text' : 'password'
})
</script>

<template>
  <label class="block">
    <span
      v-if="label"
      class="mb-2 block text-sm font-medium text-text-main"
    >{{ label }}</span>
    <span class="relative block">
      <input
        :type="inputType"
        :value="modelValue"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :required="required"
        class="w-full rounded-md border bg-surface py-3 pl-4 text-sm text-text-main placeholder:text-text-sub outline-none transition-colors"
        :class="[
          error ? 'border-red-400 focus:border-red-500' : 'border-border focus:border-primary',
          isPasswordField ? 'pr-12' : 'pr-4',
        ]"
        @input="$emit('update:modelValue', $event.target.value)"
      >
      <!--
        눈 아이콘 대신 글자를 씁니다. 아이콘은 '지금 보이는 상태'인지 '누르면 보인다'는
        뜻인지 헷갈리는데, 글자는 누르면 무엇이 될지 그대로 말해 줍니다.
      -->
      <button
        v-if="isPasswordField"
        type="button"
        class="absolute right-1 top-1/2 -translate-y-1/2 rounded px-2.5 py-1.5 text-xs font-semibold text-text-sub transition-colors hover:text-primary"
        :aria-label="isRevealed ? '비밀번호 가리기' : '비밀번호 보기'"
        :aria-pressed="isRevealed"
        @click="isRevealed = !isRevealed"
      >
        {{ isRevealed ? '가리기' : '보기' }}
      </button>
    </span>
    <span
      v-if="error"
      class="mt-1 block text-xs text-red-500"
    >{{ error }}</span>
  </label>
</template>
