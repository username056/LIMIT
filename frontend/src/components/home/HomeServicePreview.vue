<script setup>
// 실제 검증 화면 미리보기. 글로만 설명하면 화상 검증이 무엇인지 잘 와닿지 않아서,
// 노트북 목업 안에 실제 화면을 넣어 한 번에 보여 줍니다.
// 이미지는 기존 히어로 배너를 재사용합니다(검증 중인 화면을 담고 있습니다).
import mainHeroBanner from '../../assets/main_hero_banner.png'
import { vReveal } from '../../composables/useReveal'
</script>

<template>
  <section class="home-section preview">
    <div class="home-shell preview__inner">
      <div
        v-reveal
        class="preview__copy"
      >
        <p class="home-section__eyebrow">
          LIMIT만의 특별한 서비스
        </p>
        <h2 class="home-section__title">
          실제 검증 화면을<br>
          미리 경험해보세요
        </h2>
        <p class="home-section__sub">
          판매자가 직접 체크리스트를 따라 검수하고,<br>
          구매자는 실시간으로 확인하며 거래할 수 있어요.
        </p>
      </div>

      <div
        v-reveal="120"
        class="preview__device"
      >
        <!--
          노트북 모양은 CSS로 만듭니다. 목업 이미지를 따로 두면 안쪽 화면을 바꿀 때마다
          이미지를 다시 만들어야 하는데, 화면은 앞으로 계속 바뀔 부분입니다.
        -->
        <div class="preview__screen">
          <img
            :src="mainHeroBanner"
            alt="LIMIT에서 실시간 화상으로 기기 상태를 확인하는 검증 화면"
            class="preview__shot"
            loading="lazy"
          >
        </div>
        <div
          class="preview__base"
          aria-hidden="true"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.preview__inner {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  align-items: center;
  gap: 56px;
}

/* ---- 노트북 목업 ------------------------------------------------------- */

.preview__device {
  /* 아주 느리게 떠 있게 둡니다. 멈춰 있으면 그림처럼 보입니다. */
  animation: preview-float 7s ease-in-out infinite;
}

.preview__screen {
  overflow: hidden;
  padding: 12px 12px 0;
  border: 1px solid #d8dfee;
  border-radius: 16px 16px 4px 4px;
  background: #2b3245;
  box-shadow: 0 26px 60px -24px rgb(30 41 59 / 45%);
}

.preview__shot {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  border-radius: 6px 6px 0 0;
  background: #fff;
}

/* 화면 아래 받침. 노트북처럼 보이게 하는 최소한의 장치입니다. */
.preview__base {
  width: 116%;
  height: 14px;
  margin-left: -8%;
  border-radius: 0 0 14px 14px;
  background: linear-gradient(180deg, #d9e0ee 0%, #bcc6da 100%);
  box-shadow: 0 14px 28px -14px rgb(30 41 59 / 35%);
}

@keyframes preview-float {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-9px);
  }
}

@media (max-width: 900px) {
  .preview__inner {
    grid-template-columns: minmax(0, 1fr);
    gap: 40px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .preview__device {
    animation: none;
  }
}
</style>
