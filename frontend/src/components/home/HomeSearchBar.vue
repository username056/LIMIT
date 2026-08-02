<script setup>
// 히어로 한가운데 놓이는 큰 검색창입니다. 헤더에도 검색창이 있지만 그건 이동 중에 쓰는
// 작은 입력이고, 여기는 처음 들어온 사람이 무엇부터 할지 정하는 자리라 따로 둡니다.
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const keyword = ref('')

function submit() {
  const query = keyword.value.trim()
  // 목록 페이지는 검색어를 q로 읽습니다. 빈 값이면 전체 목록으로 보냅니다.
  router.push({ name: 'products', query: query ? { q: query } : {} })
}
</script>

<template>
  <form
    class="search"
    role="search"
    @submit.prevent="submit"
  >
    <span
      class="search__icon"
      aria-hidden="true"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle
          cx="11"
          cy="11"
          r="7"
        />
        <path d="m20 20-3.5-3.5" />
      </svg>
    </span>

    <label
      class="search__label"
      for="home-search"
    >상품 검색</label>
    <input
      id="home-search"
      v-model="keyword"
      class="search__input"
      type="search"
      placeholder="제품명, 모델명으로 검색해보세요"
      autocomplete="off"
    >

    <button
      class="search__submit"
      type="submit"
      aria-label="검색"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M5 12h13" />
        <path d="m12 5 7 7-7 7" />
      </svg>
    </button>
  </form>
</template>

<style scoped>
.search {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 620px;
  margin: 0 auto;
  padding: 10px 10px 10px 22px;
  background: rgb(255 255 255 / 88%);
  border: 1px solid rgb(255 255 255 / 90%);
  border-radius: var(--radius-pill);
  box-shadow: 0 14px 40px -14px rgb(76 100 200 / 28%);
  backdrop-filter: blur(14px);
  transition:
    box-shadow 0.3s var(--home-ease),
    transform 0.3s var(--home-ease);
}

.search:hover,
.search:focus-within {
  transform: translateY(-2px);
  box-shadow: 0 18px 48px -14px rgb(76 100 200 / 36%);
}

.search__icon {
  display: flex;
  flex-shrink: 0;
  color: var(--color-text-sub);
}

.search__icon svg {
  width: 20px;
  height: 20px;
}

/* 라벨은 화면에서 감추되 읽어 주는 프로그램에는 남깁니다. */
.search__label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.search__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: 16px;
  color: var(--color-text-main);
}

.search__input::placeholder {
  color: var(--color-text-sub);
}

.search__input:focus {
  outline: none;
}

/* 검색 입력의 브라우저 기본 지우기 버튼이 화살표 옆에 겹쳐 보여 감춥니다. */
.search__input::-webkit-search-cancel-button {
  appearance: none;
}

.search__submit {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-primary-gradient);
  color: #fff;
  cursor: pointer;
  transition:
    transform 0.25s var(--home-ease),
    box-shadow 0.25s var(--home-ease);
}

.search__submit svg {
  width: 20px;
  height: 20px;
}

.search__submit:hover {
  transform: translateX(2px);
  box-shadow: 0 6px 16px rgb(99 102 241 / 32%);
}

@media (max-width: 640px) {
  .search {
    gap: 8px;
    padding: 8px 8px 8px 18px;
  }

  .search__input {
    font-size: 15px;
  }

  .search__submit {
    width: 40px;
    height: 40px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .search,
  .search__submit {
    transition: none;
  }

  .search:hover,
  .search:focus-within,
  .search__submit:hover {
    transform: none;
  }
}
</style>
