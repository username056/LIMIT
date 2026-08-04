<script setup>
import { computed, ref, watch } from 'vue'

/*
  회원 얼굴을 그리는 칸.
  ---------------------------------------------------------------------------
  사진이 있으면 사진을, 없으면 닉네임 첫 글자를 그립니다. 다섯 화면(헤더·마이페이지·
  상품 상세 판매자 카드·판매자 페이지·화상 통화)이 각자 첫 글자를 잘라 쓰고 있어서,
  사진을 넣을 때 다섯 곳을 따로 고쳐야 했고 한 곳을 빠뜨리면 같은 사람이 화면마다
  다르게 보였습니다. 한곳에 모아 둡니다.
*/
const props = defineProps({
  src: { type: String, default: '' },
  name: { type: String, default: '' },

  // Tailwind는 문자열을 조합해 클래스를 만들지 못하므로 크기는 그대로 받습니다.
  sizeClass: { type: String, default: 'h-10 w-10' },
  textClass: { type: String, default: 'text-sm' },

  // 네모(상품 사진과 나란히 놓일 때)와 동그라미를 고릅니다.
  rounded: { type: String, default: 'rounded-full' },
})

// 닉네임이 비면 '회'로 둡니다. 빈 원만 남으면 무엇이 빠진 자리인지 알 수 없습니다.
const initial = computed(() => (props.name || '회').trim().charAt(0) || '회')

/*
  사진 주소를 못 불러오면 첫 글자로 물러납니다.
  서버가 주는 주소는 시한이 있는 presigned URL일 수 있어, 화면을 오래 열어 두면
  만료됩니다. 그때 그대로 두면 깨진 그림 아이콘이 뜨는데, 첫 글자가 뜨는 편이 낫습니다.
*/
const isBroken = ref(false)
watch(() => props.src, () => { isBroken.value = false })
const hasImage = computed(() => Boolean(props.src) && !isBroken.value)
</script>

<template>
  <img
    v-if="hasImage"
    :src="src"
    :alt="`${name || '회원'} 프로필 사진`"
    class="shrink-0 object-cover"
    :class="[sizeClass, rounded]"
    @error="isBroken = true"
  >
  <span
    v-else
    class="flex shrink-0 items-center justify-center bg-primary-gradient font-bold text-white"
    :class="[sizeClass, rounded, textClass]"
    aria-hidden="true"
  >{{ initial }}</span>
</template>
