<script setup>
// 히어로 배경.
// ---------------------------------------------------------------------------
// 배경은 두 겹입니다.
//
//   1) CSS 실크  — 항상 깔려 있습니다. 0KB고 즉시 그려집니다.
//   2) 영상      — 조건이 맞을 때만 뒤늦게 받아서 위에 겹칩니다.
//
// 영상이 없거나 못 받아도 1)이 그대로 남으므로 화면이 비지 않습니다.
// 파일은 번들이 아니라 public/에 둡니다. import로 묶으면 파일이 없을 때 빌드가
// 통째로 깨지는데, 배경 하나 때문에 배포를 막을 이유가 없습니다.
//
// 안 받는 경우:
//   - 좁은 화면(모바일). 셀룰러로 수 MB를 끌어오는 건 무례합니다.
//   - 움직임을 줄여 달라고 설정한 사용자.
//   - 데이터 절약 모드거나 회선이 느린 경우.
import { onMounted, ref } from 'vue'
import HomeSearchBar from './HomeSearchBar.vue'
import HomePopularKeywords from './HomePopularKeywords.vue'

// public/ 아래 실제 파일명입니다. 번들에 묶이지 않으므로 경로 문자열로 참조합니다.
const HERO_VIDEO_MP4 = '/limit_hero_banner.mp4'

// 제목 한가운데에서 돌아가는 낱말. 한 낱말이 2초 머무르고 0.5초에 걸쳐 바뀝니다.
// 실제 전환은 CSS keyframes가 하고, 여기 배열은 무엇을 몇 개 돌릴지만 정합니다.
const ROTATING_WORDS = ['스마트폰', '노트북', '태블릿PC']

const shouldLoadVideo = ref(false)
const isVideoReady = ref(false)

// 왜 안 트는지 화면만 보고는 알 수 없어서, 건너뛴 이유를 콘솔에 남깁니다.
// 조건 때문에 안 받은 것과 파일을 못 읽은 것은 눈으로 구분이 안 됩니다.
function videoSkipReason() {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return '움직임 줄이기 설정'
  // 진짜 휴대폰만 걸러냅니다. 900px로 잡았더니 개발자 도구를 열어 둔 노트북까지
  // 걸려서 정작 봐야 할 때 영상이 사라졌습니다.
  if (window.matchMedia?.('(max-width: 600px)').matches) return '좁은 화면(600px 이하)'

  // 데이터 절약 모드는 사용자가 직접 켠 것이라 존중합니다.
  // 반면 effectiveType(2g/3g 추정)은 브라우저가 응답 속도로 어림잡는 값이라,
  // 개발 서버가 잠깐 느리기만 해도 3g로 잡혀 영상이 말없이 사라집니다. 쓰지 않습니다.
  if (navigator.connection?.saveData) return '데이터 절약 모드'

  return null
}

onMounted(() => {
  const reason = videoSkipReason()
  if (reason) {
    console.info(`[hero] 배경 영상을 건너뜁니다: ${reason}. CSS 배경으로 대신합니다.`)
    return
  }
  // 첫 화면에 필요한 것들이 다 그려진 뒤에 받기 시작합니다.
  if (document.readyState === 'complete') shouldLoadVideo.value = true
  else window.addEventListener('load', () => { shouldLoadVideo.value = true }, { once: true })
})

function onVideoError(event) {
  const code = event.target?.error?.code
  console.warn(
    `[hero] 배경 영상을 재생하지 못했습니다(code ${code}). 경로: ${HERO_VIDEO_MP4}\n`
    + '파일이 public/에 있는지, 브라우저가 읽을 수 있는 코덱(H.264)인지 확인하세요.',
  )
}
</script>

<template>
  <section class="hero">
    <div
      class="hero__media"
      aria-hidden="true"
    >
      <span class="hero__silk hero__silk--a" />
      <span class="hero__silk hero__silk--b" />
      <span class="hero__silk hero__silk--c" />

      <!--
        받아 놓고 첫 프레임이 준비된 뒤에 서서히 켭니다. 바로 보이게 하면
        CSS 배경에서 영상으로 넘어가는 순간이 툭 끊겨 보입니다.
      -->
      <video
        v-if="shouldLoadVideo"
        class="hero__video"
        :class="{ 'hero__video--on': isVideoReady }"
        autoplay
        muted
        loop
        playsinline
        preload="auto"
        :src="HERO_VIDEO_MP4"
        @canplay="isVideoReady = true"
        @error="onVideoError"
      />
    </div>

    <div
      class="hero__veil"
      aria-hidden="true"
    />

    <div class="hero__content home-shell">
      <h1
        class="hero__title home-rise"
        style="animation-delay: 140ms"
      >
        당신이 찾는

        <!--
          가운데 낱말만 바뀝니다. 높이를 미리 정해 둔 칸 안에서 낱말을 겹쳐 놓고
          투명도만 번갈아 주므로, 낱말 길이가 달라도 아래 줄이 밀리지 않습니다.
        -->
        <span class="hero__rotator">
          <span
            v-for="(word, index) in ROTATING_WORDS"
            :key="word"
            class="hero__word"
            :style="{ animationDelay: `${index * 2.5}s` }"
            aria-hidden="true"
          >{{ word }}</span>

          <!-- 움직임을 줄여 달라고 한 사용자에게는 세 낱말을 한 줄로 그냥 보여 줍니다. -->
          <span class="hero__wordStatic">{{ ROTATING_WORDS.join(', ') }}</span>

          <!-- 읽어 주는 프로그램에는 돌아가는 낱말 대신 전체를 한 번만 읽힙니다. -->
          <span class="hero__srOnly">{{ ROTATING_WORDS.join(', ') }},</span>
        </span>

        LIMIT에서 더 안전하게.
      </h1>

      <div
        class="hero__search home-rise"
        style="animation-delay: 240ms"
      >
        <HomeSearchBar />
      </div>

      <div
        class="hero__keywords home-rise"
        style="animation-delay: 340ms"
      >
        <HomePopularKeywords />
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  /*
    ▼ 돌아가는 낱말(스마트폰 · 노트북 · 태블릿PC) 색.
      바꾸려면 여기 한 줄만 고치면 됩니다. 아래 .hero__word와,
      움직임 줄이기 설정에서 대신 보이는 .hero__wordStatic이 같이 따라옵니다.
  */
  --hero-word-color: #0820f1;

  position: relative;
  display: flex;
  align-items: center;
  min-height: 640px;
  /* 헤더를 뺀 나머지를 꽉 채웁니다. svh라 모바일 주소창이 접혀도 튀지 않습니다. */
  height: calc(100svh - 72px);
  max-height: 900px;
  overflow: hidden;
  background: var(--color-surface);
}

/* ---- 배경 ------------------------------------------------------------- */

.hero__media {
  position: absolute;
  inset: 0;
}

.hero__video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 900ms ease;
}

.hero__video--on {
  opacity: 1;
}

/*
  실크 세 겹. 크게 번진 타원을 서로 다른 속도로 아주 느리게 흘려 보냅니다.
  선명한 무늬를 만들면 가운데 글자와 다투므로, 경계가 없도록 흐리게만 둡니다.
*/
.hero__silk {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  will-change: transform;
}

/* 위쪽만 덮으면 색이 중간에서 뚝 끊겨 배경이 따로 놉니다. 아래까지 물고 갑니다. */
.hero__silk--a {
  top: -20%;
  left: -16%;
  width: 78%;
  height: 120%;
  background: radial-gradient(circle, rgb(129 140 248 / 38%) 0%, rgb(129 140 248 / 0%) 70%);
  animation: hero-drift-a 26s ease-in-out infinite;
}

.hero__silk--b {
  top: -26%;
  right: -18%;
  width: 74%;
  height: 124%;
  background: radial-gradient(circle, rgb(196 181 253 / 40%) 0%, rgb(196 181 253 / 0%) 70%);
  animation: hero-drift-b 32s ease-in-out infinite;
}

.hero__silk--c {
  top: 8%;
  left: 18%;
  width: 70%;
  height: 106%;
  background: radial-gradient(circle, rgb(147 197 253 / 36%) 0%, rgb(147 197 253 / 0%) 70%);
  animation: hero-drift-c 38s ease-in-out infinite;
}

@keyframes hero-drift-a {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }

  50% {
    transform: translate3d(4%, 3%, 0) scale(1.08);
  }
}

@keyframes hero-drift-b {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1.04);
  }

  50% {
    transform: translate3d(-5%, 4%, 0) scale(1);
  }
}

@keyframes hero-drift-c {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }

  50% {
    transform: translate3d(-3%, -4%, 0) scale(1.1);
  }
}

/*
  배경 위에 덮는 한 겹.
  흰색을 고르게 42%만 얹고 뒤를 2px 흐리게 합니다. 영상의 결은 남기면서 글자
  대비만 확보하는 방식이라, 부분부분 덮을 때처럼 화면이 갈라져 보이지 않습니다.
  맨 아래는 다음 섹션 색으로 이어 경계선을 없앱니다.
*/
.hero__veil {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0%) 78%, rgb(255 255 255 / 70%) 93%, var(--color-surface) 100%),
    rgb(255 255 255 / 42%);
  backdrop-filter: blur(2px);
}

/* ---- 내용 ------------------------------------------------------------- */

.hero__content {
  position: relative;
  text-align: center;
}

.hero__title {
  margin-bottom: 40px;
  font-size: 52px;
  font-weight: 800;
  line-height: 1.28;
  letter-spacing: -0.03em;
  color: var(--color-text-main);
}

/* ---- 돌아가는 낱말 ----------------------------------------------------- */

/*
  높이를 글자 한 줄로 못박아 둔 칸입니다. 안의 낱말은 전부 겹쳐 놓기 때문에
  '스마트폰'에서 '태블릿PC'로 바뀌어도 아래 줄이 위아래로 흔들리지 않습니다.
*/
.hero__rotator {
  position: relative;
  display: block;
  height: 1.28em;
}

/*
  낱말은 그라데이션 대신 한 가지 색으로 둡니다. 배경이 옅은 파랑이라 글자에까지
  하늘색이 섞이면 뒤로 물러나 보입니다.
*/
.hero__word {
  position: absolute;
  inset: 0;
  color: var(--hero-word-color);
  opacity: 0;
  /* 2초 머무르고 0.5초에 걸쳐 바뀝니다. 낱말이 셋이라 한 바퀴가 7.5초입니다. */
  animation: hero-word 7.5s cubic-bezier(0.16, 1, 0.3, 1) infinite both;
}

@keyframes hero-word {
  0% {
    opacity: 0;
    transform: translateY(16px);
  }

  6.67% {
    opacity: 1;
    transform: translateY(0);
  }

  26.67% {
    opacity: 1;
    transform: translateY(0);
  }

  33.33%,
  100% {
    opacity: 0;
    transform: translateY(-16px);
  }
}

/* 움직임을 줄여 달라고 한 사용자에게만 보이는 정적 대체 문구입니다. */
.hero__wordStatic {
  display: none;
  color: var(--hero-word-color);
}

.hero__srOnly {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.hero__keywords {
  margin-top: 22px;
}

@media (max-width: 1024px) {
  .hero__title {
    font-size: 40px;
  }
}

@media (max-width: 640px) {
  .hero {
    min-height: 560px;
    height: auto;
    padding: 72px 0 64px;
  }

  .hero__title {
    margin-bottom: 30px;
    font-size: 29px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero__silk {
    animation: none;
  }

  .hero__video {
    transition: none;
  }

  /* 돌아가는 대신 세 낱말을 한 줄로 세워 둡니다. 뜻은 그대로 전해집니다. */
  .hero__word {
    display: none;
  }

  .hero__wordStatic {
    display: block;
  }
}
</style>
