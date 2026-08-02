<script setup>
// 홈에서 쓰는 상품 카드. 인기 상품과 최근 등록 상품이 같은 카드를 씁니다.
// 다른 건 정렬 기준과 카드 아래 한 줄에 무엇을 적느냐뿐입니다.
//
//   popular : '인기' 배지 + 조회수
//   recent  : 거래 지역 + 담기 버튼
//
// 목록·관심상품이 쓰는 ProductCard와 담는 정보는 같지만, 그쪽은 한 화면에 18개가
// 깔리는 조밀한 카드고 여기는 서너 개만 크게 놓는 자리라 여백을 따로 잡았습니다.
defineProps({
  product: { type: Object, required: true },
  variant: {
    type: String,
    default: 'popular',
    validator: (value) => ['popular', 'recent'].includes(value),
  },
  // recent에서만 씁니다. 담기 상태는 부모가 한 번에 받아 와 내려 줍니다.
  isFavorite: { type: Boolean, default: false },
  isPending: { type: Boolean, default: false },
})

defineEmits(['toggle-favorite'])

function formatPrice(price) {
  return Number(price || 0).toLocaleString('ko-KR')
}

function formatCount(value) {
  return Math.max(0, Number(value || 0)).toLocaleString('ko-KR')
}

function specLine(product) {
  const parts = [product.manufacturerName, product.modelName].filter(Boolean)
  return parts.length ? parts.join(' · ') : '사양 정보 준비 중'
}
</script>

<template>
  <RouterLink
    :to="{ name: 'product-detail', params: { productId: product.productId } }"
    class="card"
  >
    <div class="card__media">
      <img
        v-if="product.thumbnailUrl"
        :src="product.thumbnailUrl"
        :alt="product.name"
        class="card__image"
        loading="lazy"
      >
      <span
        v-else
        class="card__empty"
      >등록된 이미지 없음</span>

      <!--
        배지는 인기 상품에만 답니다. 최근 등록 쪽은 섹션 제목이 이미 '최근 등록된 상품'이라
        카드마다 NEW를 또 붙이면 같은 말을 두 번 하는 셈입니다.
      -->
      <span
        v-if="variant === 'popular'"
        class="card__badge"
      >인기</span>
    </div>

    <div class="card__body">
      <p class="card__name">
        {{ product.name }}
      </p>
      <p class="card__spec">
        {{ specLine(product) }}
      </p>

      <div class="card__foot">
        <p class="card__price">
          {{ formatPrice(product.price) }}원
        </p>

        <!--
          카드 전체가 상세로 가는 링크라, 기본 동작을 막아야 담기만 되고 화면이
          넘어가지 않습니다.
        -->
        <button
          type="button"
          class="card__fav"
          :class="{ 'card__fav--on': isFavorite }"
          :disabled="isPending"
          :aria-label="isFavorite ? '좋아요한 상품 해제' : '좋아요한 상품 등록'"
          :aria-pressed="isFavorite"
          @click.prevent.stop="$emit('toggle-favorite', product)"
        >
          <svg
            viewBox="0 0 24 24"
            :fill="isFavorite ? 'currentColor' : 'none'"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M12 20.5 4.2 13a4.7 4.7 0 0 1 6.6-6.6l1.2 1.1 1.2-1.1A4.7 4.7 0 0 1 19.8 13Z" />
          </svg>
        </button>
      </div>

      <div class="card__meta">
        <template v-if="variant === 'popular'">
          <span class="card__metaItem">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              aria-hidden="true"
            >
              <path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6z" />
              <circle
                cx="12"
                cy="12"
                r="2.6"
              />
            </svg>
            조회 {{ formatCount(product.viewCount) }}
          </span>
        </template>

        <!--
          거래 지역은 쓰지 않기로 했습니다. 대신 이 서비스에서 상품을 고르는 실제
          기준인 검증 개수를 적습니다.
        -->
        <template v-else>
          <span
            v-if="product.requiredItemCount"
            class="card__metaItem"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M12 3.5 19 6v6c0 4.3-2.9 7.6-7 8.5-4.1-.9-7-4.2-7-8.5V6z" />
              <path d="m9.2 12 1.9 1.9 3.7-3.8" />
            </svg>
            검증 {{ product.completedItemCount ?? 0 }}/{{ product.requiredItemCount }}
          </span>
          <span class="card__metaItem">
            조회 {{ formatCount(product.viewCount) }}
          </span>
        </template>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
  transition:
    transform 0.35s var(--home-ease),
    box-shadow 0.35s var(--home-ease),
    border-color 0.35s var(--home-ease);
}

.card:hover {
  transform: translateY(-6px);
  border-color: rgb(99 102 241 / 32%);
  box-shadow: 0 18px 40px -14px rgb(76 100 200 / 26%);
}

.card__media {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  /* 사진이 없을 때만 보이는 바탕입니다. 목록 카드와 같은 색으로 둡니다. */
  background: #f8fafc;
}

.card__image {
  width: 100%;
  height: 100%;
  /*
    상품 목록·관심상품 카드와 같은 방식(cover)입니다. contain으로 두면 세로로 긴
    사진이 카드 안에서 작게 떠 버려 같은 상품이 화면마다 다르게 보입니다.
  */
  object-fit: cover;
  transition: transform 0.45s var(--home-ease);
}

.card:hover .card__image {
  transform: scale(1.04);
}

.card__empty {
  font-size: 13px;
  color: #cbd5e1;
}

.card__badge {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  background: rgb(255 255 255 / 90%);
  font-size: 12px;
  font-weight: 700;
  color: var(--color-gradient-start);
  backdrop-filter: blur(6px);
}

.card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 20px;
}

.card__name {
  overflow: hidden;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__spec {
  margin-top: 6px;
  overflow: hidden;
  font-size: 13px;
  color: var(--color-text-sub);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 16px;
}

.card__price {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--color-text-main);
}

.card__fav {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: #cbd5e1;
  cursor: pointer;
  transition:
    color 0.25s var(--home-ease),
    border-color 0.25s var(--home-ease),
    background-color 0.25s var(--home-ease);
}

.card__fav svg {
  width: 20px;
  height: 20px;
}

.card__fav:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.card__fav--on {
  border-color: var(--color-primary);
  background: var(--color-accent-bg);
  color: var(--color-primary);
  /* 담긴 순간 한 번 톡 튑니다. 목록 화면의 하트와 같은 움직임입니다. */
  animation: card-fav-pop 320ms ease-out;
}

.card__fav:disabled {
  cursor: default;
  opacity: 0.6;
}

@keyframes card-fav-pop {
  0% { transform: scale(1); }
  45% { transform: scale(1.3); }
  100% { transform: scale(1); }
}

.card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-sub);
}

.card__metaItem {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__metaItem svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

@media (prefers-reduced-motion: reduce) {
  .card,
  .card__image,
  .card__fav {
    transition: none;
  }

  .card:hover,
  .card:hover .card__image {
    transform: none;
  }

  .card__fav--on {
    animation: none;
  }
}
</style>
