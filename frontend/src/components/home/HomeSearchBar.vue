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
/*
  헤더 판과 같은 유리 결로 맞춥니다. 흰색을 92%까지 얹어 뒤 배경이 살짝만 비치게
  하고, 테두리와 그림자는 존재만 알 정도로 옅게 둡니다. 진하면 배너 위에서
  혼자 무겁게 떠 보입니다.
*/
.search {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 660px;
  height: 50px;
  margin: 0 auto;
  padding: 0 6px 0 20px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid rgb(99 102 241 / 8%);
  border-radius: var(--radius-pill);
  box-shadow: 0 10px 30px rgb(15 23 42 / 5%);
  backdrop-filter: blur(20px);
}

/*
  커서를 올려도, 눌러도 테두리는 그대로입니다.
  ---------------------------------------------------------------------------
  예전에는 :focus-within으로 테두리를 진하게 했습니다. 키보드로 왔을 때만
  표시하려고 :has(:focus-visible)로 좁혀 봤지만, 텍스트 입력칸은 마우스로 눌러도
  :focus-visible이 걸립니다(브라우저가 곧 타이핑할 자리로 보고 일부러 그럽니다).
  그래서 조건으로는 가릴 수 없어 표시 자체를 뺐습니다.

  지금 어디에 있는지는 깜빡이는 커서가 알려 줍니다. 입력칸은 그것으로 충분합니다.
*/

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
  width: 38px;
  height: 38px;
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
    height: 46px;
    padding: 0 5px 0 16px;
  }

  .search__input {
    font-size: 15px;
  }

  .search__submit {
    width: 34px;
    height: 34px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .search,
  .search__submit {
    transition: none;
  }

  .search__submit:hover {
    transform: none;
  }
}
</style>
