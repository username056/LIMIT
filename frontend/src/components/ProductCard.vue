<script setup>
// 상품 목록·관심상품·상품 관리가 같은 카드를 씁니다. 대표 이미지는 그 상품의 얼굴이라
// 어느 화면에서든 같은 자리에 같은 크기로 보여야 판매자가 등록한 사진이 제 역할을 합니다.
//
// 판매자용 화면은 카드 아래에 상태 배지나 관리 버튼이 더 필요해서 슬롯으로 열어 두었습니다.
import { isSoldOut } from '../utils/productStatus'

defineProps({
  product: { type: Object, required: true },
  // 카드 전체를 상세로 연결합니다.
  to: { type: [Object, String], default: null },
  // 카드 안에 버튼이 있어 전체를 링크로 감쌀 수 없을 때(판매자 화면) 제목만 링크로 만듭니다.
  titleTo: { type: [Object, String], default: null },
})

function formatPrice(price) {
  return Number(price || 0).toLocaleString('ko-KR')
}
</script>

<template>
  <component
    :is="to ? 'RouterLink' : 'div'"
    :to="to || undefined"
    class="group block overflow-hidden rounded-lg border border-border bg-surface shadow-card transition"
    :class="to ? 'hover:-translate-y-0.5 hover:border-primary/50 hover:shadow-elevated' : ''"
  >
    <div class="relative flex aspect-[4/3] items-center justify-center overflow-hidden bg-slate-50">
      <img
        v-if="product.thumbnailUrl"
        :src="product.thumbnailUrl"
        :alt="product.name"
        class="h-full w-full object-cover transition duration-300"
        :class="to ? 'group-hover:scale-[1.03]' : ''"
      >
      <div
        v-else
        class="flex flex-col items-center text-slate-300"
      >
        <span class="text-4xl">▣</span>
        <span class="mt-2 text-xs">등록된 이미지 없음</span>
      </div>
      <div
        v-if="isSoldOut(product.status)"
        class="absolute inset-0 flex items-center justify-center bg-black/55"
      >
        <span class="text-lg font-bold text-white">판매 완료</span>
      </div>
      <!-- 이미지 위 좌측 상단. 판매자 화면에서 상태를 얹는 자리입니다. -->
      <slot name="image-overlay" />
    </div>
    <div class="p-4">
      <p class="truncate text-xs font-semibold text-primary">
        {{ product.manufacturerName || '제조사 미등록' }} · {{ product.modelName || '모델 미등록' }}
      </p>
      <h2 class="mt-2 truncate font-bold text-text-main">
        <RouterLink
          v-if="titleTo"
          :to="titleTo"
          class="hover:text-primary hover:underline"
        >
          {{ product.name }}
        </RouterLink>
        <template v-else>
          {{ product.name }}
        </template>
      </h2>
      <p class="mt-3 text-lg font-bold text-text-main">
        {{ formatPrice(product.price) }}원
      </p>
      <div class="mt-3 border-t border-border pt-3 text-xs text-text-sub">
        <slot name="footer">
          <div class="flex items-center justify-end">
            <span class="shrink-0 font-semibold text-primary">상세 보기 →</span>
          </div>
        </slot>
      </div>
    </div>
  </component>
</template>
