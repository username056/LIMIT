<script setup>
// 검색창 아래 인기 검색어. 무엇을 검색해야 할지 모르는 사람에게 첫 손잡이를 줍니다.
// 지금은 고정 목록입니다. 검색어 집계 API가 생기면 이 배열만 갈아 끼우면 됩니다.
//
// 실제로 취급하는 기종 위주로 둡니다. 눌렀는데 결과가 0건이면 첫 손잡이가 아니라
// 막다른 길이 됩니다.
const KEYWORDS = [
  '갤럭시 S26',
  '갤럭시 북5 엣지',
  '갤럭시 Z 플립8',
  '갤럭시 탭 S10+',
  '갤럭시 북5 엣지',
]
</script>

<template>
  <div class="keywords">
    <span class="keywords__label">인기 검색어</span>
    <RouterLink
      v-for="keyword in KEYWORDS"
      :key="keyword"
      :to="{ name: 'products', query: { q: keyword } }"
      class="keywords__pill"
    >
      {{ keyword }}
    </RouterLink>
  </div>
</template>

<style scoped>
.keywords {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.keywords__label {
  margin-right: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-sub);
}

.keywords__pill {
  padding: 7px 15px;
  border: 1px solid rgb(255 255 255 / 80%);
  border-radius: var(--radius-pill);
  background: rgb(255 255 255 / 72%);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-main);
  white-space: nowrap;
  backdrop-filter: blur(8px);
  transition:
    transform 0.25s var(--home-ease),
    box-shadow 0.25s var(--home-ease),
    color 0.25s var(--home-ease);
}

.keywords__pill:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px -4px rgb(76 100 200 / 24%);
  color: var(--color-gradient-start);
}

@media (max-width: 640px) {
  .keywords__label {
    width: 100%;
    margin-bottom: 2px;
    text-align: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .keywords__pill {
    transition: none;
  }

  .keywords__pill:hover {
    transform: none;
  }
}
</style>
