<script setup>
// 검증 프로세스 4단계. 이 서비스가 다른 중고 거래와 무엇이 다른지 한 줄로 보여 주는
// 자리라, 실제 제품의 흐름(체크리스트 → 화상 → 채팅 → 거래) 순서를 그대로 둡니다.
import { vReveal } from '../../composables/useReveal'

const STEPS = [
  {
    id: 'checklist',
    title: '기기별 체크리스트',
    description: '전문가 기준의 항목으로 꼼꼼하게 검수할 수 있어요.',
  },
  {
    id: 'video',
    title: '실시간 화상 검증',
    description: 'WebRTC 실시간 화상으로 판매자와 함께 직접 확인해요.',
  },
  {
    id: 'chat',
    title: '1:1 채팅',
    description: '검증 중 궁금한 점은 실시간으로 질문할 수 있어요.',
  },
  {
    id: 'safe',
    title: '안전한 거래',
    description: '검증된 정보로 안심하고 거래를 진행할 수 있어요.',
  },
]
</script>

<template>
  <section class="home-section process">
    <div class="home-shell">
      <div
        v-reveal
        class="home-section__head home-section__head--center"
      >
        <h2 class="home-section__title">
          LIMIT 검증 프로세스로 <span class="home-gradient-text">안전하게 거래하세요</span>
        </h2>
      </div>

      <ol class="process__list">
        <template
          v-for="(step, index) in STEPS"
          :key="step.id"
        >
          <li
            v-reveal="index * 90"
            class="process__step"
          >
            <span class="process__icon">
              <!-- 체크리스트 -->
              <svg
                v-if="step.id === 'checklist'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M9 4h6v2H9z" />
                <path d="M15 5h2a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h2" />
                <path d="m9 12 1.6 1.6L14 10" />
                <path d="M9 17h6" />
              </svg>
              <!-- 화상 검증 -->
              <svg
                v-else-if="step.id === 'video'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <rect
                  x="3"
                  y="6"
                  width="12"
                  height="12"
                  rx="2"
                />
                <path d="m15 10.5 6-3.5v10l-6-3.5z" />
              </svg>
              <!-- 채팅 -->
              <svg
                v-else-if="step.id === 'chat'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M21 12c0 4.1-4 7.4-9 7.4a10 10 0 0 1-3.4-.6L3 20.5l1.4-3.6A6.9 6.9 0 0 1 3 12c0-4.1 4-7.4 9-7.4s9 3.3 9 7.4z" />
                <path d="M8.5 12h.01M12 12h.01M15.5 12h.01" />
              </svg>
              <!-- 안전 거래 -->
              <svg
                v-else
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
            </span>

            <h3 class="process__title">
              {{ step.title }}
            </h3>
            <p class="process__desc">
              {{ step.description }}
            </p>
          </li>

          <li
            v-if="index < STEPS.length - 1"
            class="process__arrow"
            aria-hidden="true"
          >
            ›
          </li>
        </template>
      </ol>
    </div>
  </section>
</template>

<style scoped>
/*
  단계와 화살표를 한 줄에 함께 세워 간격이 늘 같게 둡니다.
  화살표를 카드 안에 넣으면 설명 길이에 따라 위치가 흔들립니다.
*/
.process__list {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr;
  align-items: start;
  gap: 0;
}

.process__step {
  padding: 0 12px;
  text-align: center;
}

.process__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  margin: 0 auto 18px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgb(99 102 241 / 10%), rgb(147 197 253 / 22%));
  color: var(--color-gradient-start);
  transition: transform 0.3s var(--home-ease);
}

.process__icon svg {
  width: 30px;
  height: 30px;
}

.process__step:hover .process__icon {
  transform: translateY(-4px);
}

.process__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-main);
}

.process__desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--color-text-sub);
}

.process__arrow {
  align-self: center;
  margin-top: -34px;
  padding: 0 6px;
  font-size: 22px;
  color: #cbd5e1;
}

@media (max-width: 900px) {
  .process__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 40px 16px;
  }

  /* 2열로 접히면 화살표가 흐름을 가리켜 주지 못하므로 뺍니다. */
  .process__arrow {
    display: none;
  }
}

@media (max-width: 560px) {
  .process__list {
    grid-template-columns: minmax(0, 1fr);
    gap: 32px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .process__icon {
    transition: none;
  }

  .process__step:hover .process__icon {
    transform: none;
  }
}
</style>
