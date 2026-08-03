<script setup>
/*
  채팅에서 상대를 알아보는 네모칸.
  ---------------------------------------------------------------------------
  상품 사진이 있으면 사진을, 없으면 사람 아이콘을 그립니다. 예전에는 사진이
  없을 때 아무 무늬 없는 그라데이션 네모만 남아, 무엇이 빠진 자리인지 알 수
  없었습니다.

  사진이든 아이콘이든 동그랗게 자릅니다. 하나는 네모, 하나는 동그라면 목록을
  훑을 때 줄이 들쭉날쭉해 보입니다.

  목록과 대화창 두 곳이 같은 칸을 쓰므로 한곳에 모아 둡니다. 한쪽만 고치면
  같은 상대가 화면마다 다르게 보입니다.
*/
defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },

  // Tailwind는 문자열을 조합해 클래스를 만들지 못하므로 크기는 그대로 받습니다.
  sizeClass: { type: String, default: 'h-10 w-10' },
  iconClass: { type: String, default: 'h-6 w-6' },

  // 고른 대화에만 씁니다. 색을 조금 올려 눈이 먼저 닿게 하되, 테두리나 그림자는
  // 두지 않습니다. 목록에서 한 칸만 튀어 보이면 나머지가 흐릿해집니다.
  vivid: { type: Boolean, default: false },
})
</script>

<template>
  <img
    v-if="src"
    :src="src"
    :alt="alt"
    class="shrink-0 rounded-full object-cover transition-[filter] duration-200"
    :class="[sizeClass, vivid ? 'saturate-125' : '']"
  >
  <div
    v-else
    class="flex shrink-0 items-center justify-center rounded-full bg-primary-gradient text-white transition-[filter] duration-200"
    :class="[sizeClass, vivid ? 'saturate-150 brightness-105' : '']"
  >
    <svg
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      :class="iconClass"
    >
      <circle
        cx="12"
        cy="8.6"
        r="3.7"
      />
      <path d="M4.6 20.4c0-3.9 3.3-6.4 7.4-6.4s7.4 2.5 7.4 6.4z" />
    </svg>
  </div>
</template>
