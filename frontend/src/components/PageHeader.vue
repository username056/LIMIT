<script setup>
// 페이지 맨 위 머리말. 영문 대문자 한 줄 → 페이지 이름 → 한 문장 설명 순서입니다.
//
// 예전에는 페이지마다 같은 마크업을 각자 들고 있었습니다. 그러다 보니 자간이
// 0.16em인 곳과 0.18em인 곳, 영문을 대문자로 쓴 곳과 아닌 곳이 섞여, 화면을
// 옮길 때마다 머리말이 조금씩 달라 보였습니다. 한 곳으로 모아 둡니다.
//
// 오른쪽에 버튼이 필요하면 action 슬롯을 씁니다(예: 전체 상품의 '내 상품 등록하기').
defineProps({
  // 위에 얹는 영문 한 줄. 대문자로 넣지 않아도 CSS가 대문자로 그립니다.
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },

  /*
    줄 간격을 좁힌 머리말.
    -------------------------------------------------------------------------
    글자 크기는 그대로 두고 사이 여백만 줄입니다. 채팅처럼 남은 높이를 본문이
    전부 써야 하는 화면에서는, 머리말이 쓰는 120px이 대화 두 줄과 맞바꿔집니다.
    기본값은 false라 나머지 열한 화면은 그대로입니다.
  */
  dense: { type: Boolean, default: false },
})
</script>

<template>
  <div
    class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"
    :class="dense ? 'mb-4' : 'mb-8'"
  >
    <div class="min-w-0">
      <!--
        12px에 자간까지 벌리니 대문자가 얇고 작게 보여 13px로 올렸습니다.
        (내 정보는 원래 13px이었는데 공용으로 묶으면서 12px로 줄어 있었습니다.)
      -->
      <p
        v-if="eyebrow"
        class="text-[13px] font-bold uppercase leading-5 tracking-[0.16em] text-primary"
      >
        {{ eyebrow }}
      </p>
      <h1
        class="text-2xl font-bold text-text-main"
        :class="dense ? 'mt-0.5' : 'mt-2'"
      >
        {{ title }}
      </h1>
      <p
        v-if="description"
        class="text-sm text-text-sub"
        :class="dense ? 'mt-1' : 'mt-2'"
      >
        {{ description }}
      </p>
    </div>

    <!-- 오른쪽 버튼 자리. 없으면 아무것도 그리지 않습니다. -->
    <slot name="action" />
  </div>
</template>
