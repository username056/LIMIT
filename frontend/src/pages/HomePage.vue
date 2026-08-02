<script setup>
// 랜딩 페이지.
// -------------------------------------------------------------------------
// 이 화면만 Tailwind가 아니라 순수 CSS로 짭니다. 나머지 화면은 정보를 빠르게
// 훑는 곳이라 유틸리티 클래스가 잘 맞지만, 여기는 여백과 리듬 자체가 내용이어서
// 섹션마다 값을 직접 잡는 편이 읽기 쉽습니다. 색·반지름은 tokens.css의 변수를
// 그대로 쓰므로 다른 화면과 톤은 어긋나지 않습니다.
//
// 헤더와 푸터는 DefaultLayout의 것(AppHeader/AppFooter)을 그대로 씁니다.
// 랜딩용으로 하나 더 만들면 로그인 상태·검색·반응형 동작을 두 벌 관리하게 됩니다.
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '../layouts/DefaultLayout.vue'
import HomeHero from '../components/home/HomeHero.vue'
import HomeProductSection from '../components/home/HomeProductSection.vue'
import HomeProcess from '../components/home/HomeProcess.vue'
import HomeServicePreview from '../components/home/HomeServicePreview.vue'
import '../styles/home.css'

const route = useRoute()
const router = useRouter()
const showForbiddenNotice = ref(false)

// 권한이 없어 되돌려보낸 경우입니다. 한 번 보여 주고 주소에서 흔적을 지웁니다.
onMounted(() => {
  if (route.query.notice !== 'forbidden') return
  showForbiddenNotice.value = true
  const { notice, ...rest } = route.query
  router.replace({ query: rest })
})
</script>

<template>
  <DefaultLayout>
    <p
      v-if="showForbiddenNotice"
      role="alert"
      class="home-notice"
    >
      접근 권한이 없어 홈으로 이동했습니다.
    </p>

    <div class="home">
      <HomeHero />

      <!--
        히어로 아래부터 색을 입힙니다. 페이지 전체에 %로 그라데이션을 걸면 히어로가
        끝나는 지점의 색이 페이지 길이에 따라 달라져, 히어로 밑동(흰색)과 어긋나
        가로줄이 생깁니다. 여기서 흰색으로 시작하면 그 경계가 사라집니다.
      -->
      <div class="home__body">
        <!-- 크게 번진 빛 두 덩어리. 경계 없이 색만 돌게 하는 장치입니다. -->
        <span
          class="home__glow home__glow--1"
          aria-hidden="true"
        />
        <span
          class="home__glow home__glow--2"
          aria-hidden="true"
        />

        <!--
          두 상품 섹션은 같은 컴포넌트입니다. 정렬 기준과 카드에 적는 한 줄만 다릅니다.
          공개 목록이 허용하는 정렬은 createdAt·price·viewCount 셋뿐입니다.
        -->
        <HomeProductSection
          title="지금 가장 인기 있는 상품"
          subtitle="조회수가 높은 순서대로 보여 드려요"
          sort="viewCount,desc"
          :size="4"
          :columns="4"
          variant="popular"
        />

        <HomeProcess />
        <HomeServicePreview />

        <HomeProductSection
          title="최근 등록된 상품"
          subtitle="방금 업로드 된 상품들을 가장 먼저 확인해보세요"
          sort="createdAt,desc"
          :size="4"
          :columns="4"
          variant="recent"
        />
      </div>
    </div>
  </DefaultLayout>
</template>

<style scoped>
.home {
  position: relative;
}

/*
  히어로 아래 영역의 바탕. 흰색에서 시작해 아주 느리게 도는 그라데이션 한 겹입니다.
  흰색만 깔면 밋밋하고, 섹션마다 색을 나누면 경계마다 가로줄이 생깁니다.
  한 겹으로 길게 흐르게 두면 색은 도는데 어디서 바뀌는지는 보이지 않습니다.
  시작이 흰색이라 히어로 밑동과 이어지는 자리에도 선이 생기지 않습니다.
*/
.home__body {
  position: relative;
  overflow: hidden;
  background: linear-gradient(
    180deg,
    #fff 0%,
    #f7f9ff 12%,
    #f2f6ff 34%,
    #f8faff 58%,
    #f3f7ff 82%,
    #fff 100%
  );
}

/* 크게 번진 빛 두 덩어리. 경계가 없어야 하므로 아주 흐리고 옅게 둡니다. */
.home__glow {
  position: absolute;
  z-index: 0;
  border-radius: 50%;
  filter: blur(120px);
  pointer-events: none;
}

.home__glow--1 {
  top: 20%;
  left: -14%;
  width: 46%;
  height: 26%;
  background: radial-gradient(circle, rgb(99 102 241 / 12%) 0%, rgb(99 102 241 / 0%) 70%);
}

.home__glow--2 {
  top: 62%;
  right: -16%;
  width: 50%;
  height: 24%;
  background: radial-gradient(circle, rgb(147 197 253 / 16%) 0%, rgb(147 197 253 / 0%) 70%);
}

.home-notice {
  padding: 12px 24px;
  background: var(--color-accent-bg);
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  color: var(--color-gradient-start);
}
</style>
